/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.ui.graphics.ImageBitmap
import com.google.android.stardroid.share.MAX_SHARE_EDGE_PX
import com.google.android.stardroid.ui.map.ArCameraSpecs
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVCaptureAutoFocusRangeRestrictionFar
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionDidStartRunningNotification
import platform.AVFoundation.AVCaptureSessionInterruptionEndedNotification
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureSessionRuntimeErrorNotification
import platform.AVFoundation.AVCaptureSessionWasInterruptedNotification
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVCaptureVideoStabilizationModeOff
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.autoFocusRangeRestriction
import platform.AVFoundation.autoFocusRangeRestrictionSupported
import platform.AVFoundation.automaticallyEnablesLowLightBoostWhenAvailable
import platform.AVFoundation.focusMode
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.lowLightBoostSupported
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.maxExposureTargetBias
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.minExposureTargetBias
import platform.AVFoundation.setExposureTargetBias
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.QuartzCore.CATransaction
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

/**
 * One bind of the camera. Its methods are the main thread's; what AVFoundation makes slow
 * (configuring, starting, stopping, device locks) runs on [queue].
 */
@OptIn(ExperimentalForeignApi::class)
internal class CameraSession(
    private val device: AVCaptureDevice,
    private val queue: dispatch_queue_t,
    private val target: ArPreviewTarget,
    private val onPreviewLuma: (Double) -> Unit,
    private val onReady: (ArCameraSpecs) -> Unit,
) {
    private val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private val videoOutput = AVCaptureVideoDataOutput()
    private val previewView = UIView(frame = CGRectZero.readValue())
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session)

    // The video output doesn't retain its delegate; the photo receivers are kept to fail on close.
    private val lumaProbe = PreviewLumaProbe(::reportLuma)
    private val photos = mutableSetOf<PhotoReceiver>()

    private var closed = false
    private var active = true
    private var optics: CameraOptics? = null
    private var reportedShape: PreviewShape? = null
    private var zoomRatio = 1.0
    private var exposureIndex = 0
    private var observers = emptyList<NSObjectProtocol>()
    private var lastRestartSeconds: Double? = null

    fun open() {
        observeSession()
        previewView.userInteractionEnabled = false
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        previewView.layer.addSublayer(previewLayer)
        target.container.insertSubview(previewView, atIndex = 0)
        layoutPreview()
        dispatch_async(queue) {
            val usable = configure()
            if (usable) session.startRunning()
            // Read once running, when the preset has certainly chosen the active format.
            val configured = if (usable) CameraOptics.read(device) else null
            dispatch_async(dispatch_get_main_queue()) {
                // Without one (in use, restricted) the layer stays on, showing the map over black.
                if (!closed && configured != null) {
                    optics = configured
                    applyZoom()
                    applyExposure()
                    layoutChanged()
                }
            }
        }
    }

    fun close() {
        closed = true
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers = emptyList()
        failPendingPhotos()
        previewView.removeFromSuperview()
        dispatch_async(queue) {
            videoOutput.setSampleBufferDelegate(null, null)
            if (session.running) session.stopRunning()
        }
    }

    fun setActive(active: Boolean) {
        if (closed || active == this.active) return
        this.active = active
        if (active) lastRestartSeconds = null else failPendingPhotos()
        dispatch_async(queue) {
            if (active && !session.running) session.startRunning()
            if (!active && session.running) session.stopRunning()
        }
    }

    // While the camera can't deliver, its last frame would sit still behind a moving map, so the
    // preview hides and the map is over black. An interruption ends by itself; an error, such as
    // the media services resetting, gets a restart unless one was only just tried.
    private fun observeSession() {
        fun observe(
            name: String?,
            onPosted: () -> Unit,
        ) = NSNotificationCenter.defaultCenter.addObserverForName(
            name,
            session,
            NSOperationQueue.mainQueue,
        ) { if (!closed) onPosted() }

        observers =
            listOf(
                observe(AVCaptureSessionWasInterruptedNotification) { previewView.setHidden(true) },
                // Either can arrive late enough to follow a pause.
                observe(AVCaptureSessionInterruptionEndedNotification) {
                    if (active) previewView.setHidden(false)
                },
                observe(AVCaptureSessionDidStartRunningNotification) {
                    if (active) previewView.setHidden(false)
                },
                observe(AVCaptureSessionRuntimeErrorNotification) {
                    previewView.setHidden(true)
                    failPendingPhotos()
                    restartAfterError()
                },
            )
    }

    private fun restartAfterError() {
        if (!active) return
        val now = NSProcessInfo.processInfo.systemUptime
        val last = lastRestartSeconds
        // An error straight after a restart would loop; a later one is worth another try.
        if (last != null && now - last < RESTART_BACKOFF_SECONDS) return
        lastRestartSeconds = now
        dispatch_async(queue) { session.startRunning() }
    }

    // Their callbacks may never come once the session stops.
    private fun failPendingPhotos() = photos.toList().forEach(PhotoReceiver::fail)

    fun layoutChanged() {
        if (closed) return
        layoutPreview()
        val optics = optics ?: return
        val rotation = target.rotation()
        previewLayer.connection?.let { connection ->
            if (connection.supportsVideoOrientation) {
                connection.videoOrientation = rotation.videoOrientation
            }
            // It crops, which would change the FOV the map is locked to, and it adds latency.
            if (connection.supportsVideoStabilization) {
                connection.preferredVideoStabilizationMode = AVCaptureVideoStabilizationModeOff
            }
        }
        val (width, height) = target.container.sizeInPoints()
        if (width <= 0.0 || height <= 0.0) return
        val shape = PreviewShape.of(width, height, rotation.isLandscape)
        if (shape == reportedShape) return
        reportedShape = shape
        onReady(optics.specsFor(shape))
    }

    private fun layoutPreview() {
        val (width, height) = target.container.sizeInPoints()
        val frame = CGRectMake(0.0, 0.0, width, height)
        previewView.setFrame(frame)
        // A bare sublayer animates its frame changes unless told not to.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(frame)
        CATransaction.commit()
    }

    fun setZoomRatio(ratio: Double) {
        zoomRatio = ratio
        applyZoom()
    }

    fun setExposureIndex(index: Int) {
        exposureIndex = index
        applyExposure()
    }

    private fun applyZoom() {
        val optics = optics ?: return
        val factor = zoomRatio * optics.mainFactor
        configureDevice {
            // Out of range is an Objective-C exception, which Kotlin can't catch.
            device.videoZoomFactor =
                factor.coerceIn(
                    device.minAvailableVideoZoomFactor,
                    device.maxAvailableVideoZoomFactor,
                )
        }
    }

    private fun applyExposure() {
        if (optics == null) return
        val bias = exposureIndex.toFloat() / EXPOSURE_STEPS_PER_EV
        configureDevice {
            device.setExposureTargetBias(
                bias.coerceIn(device.minExposureTargetBias, device.maxExposureTargetBias),
                completionHandler = null,
            )
        }
    }

    private fun configureDevice(change: () -> Unit) {
        if (closed) return
        dispatch_async(queue) {
            if (device.lockForConfiguration(null)) {
                change()
                device.unlockForConfiguration()
            }
        }
    }

    suspend fun takeStill(): ImageBitmap? {
        if (closed || !active || optics == null) return null
        val orientation = target.rotation().videoOrientation
        val data = CompletableDeferred<NSData?>()
        lateinit var receiver: PhotoReceiver
        receiver =
            PhotoReceiver { photo ->
                data.complete(photo)
                dispatch_async(dispatch_get_main_queue()) { photos -= receiver }
            }
        photos += receiver
        dispatch_async(queue) {
            val connection = photoOutput.connectionWithMediaType(AVMediaTypeVideo)
            // capturePhoto throws, uncatchably, without a live connection.
            if (connection == null || !connection.active || !session.running) {
                receiver.fail()
            } else {
                if (connection.supportsVideoOrientation) connection.videoOrientation = orientation
                if (connection.supportsVideoStabilization) {
                    connection.preferredVideoStabilizationMode = AVCaptureVideoStabilizationModeOff
                }
                photoOutput.capturePhotoWithSettings(
                    AVCapturePhotoSettings.photoSettings(),
                    receiver,
                )
            }
        }
        val jpeg = withTimeoutOrNull(STILL_TIMEOUT_MILLIS) { data.await() } ?: return null
        return withContext(Dispatchers.Default) {
            UIImage.imageWithData(jpeg)?.toImageBitmap(MAX_SHARE_EDGE_PX)
        }
    }

    private fun reportLuma(luma: Double) {
        dispatch_async(dispatch_get_main_queue()) { if (!closed) onPreviewLuma(luma) }
    }

    /** On [queue]. False if the camera can't be used. */
    private fun configure(): Boolean {
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false
        session.beginConfiguration()
        // The photo preset gives the preview and the still one field of view.
        if (session.canSetSessionPreset(AVCaptureSessionPresetPhoto)) {
            session.sessionPreset = AVCaptureSessionPresetPhoto
        }
        val usable = session.canAddInput(input)
        if (usable) {
            session.addInput(input)
            if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
            if (session.canAddOutput(videoOutput)) {
                session.addOutput(videoOutput)
                lumaProbe.attachTo(
                    videoOutput,
                    dispatch_queue_create("io.penterakt.skymap.camera.luma", null),
                )
            }
        }
        session.commitConfiguration()
        if (!usable) return false
        if (device.lockForConfiguration(null)) {
            if (device.lowLightBoostSupported) {
                device.automaticallyEnablesLowLightBoostWhenAvailable = true
            }
            if (device.autoFocusRangeRestrictionSupported) {
                device.autoFocusRangeRestriction = AVCaptureAutoFocusRangeRestrictionFar
            }
            if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
                device.focusMode = AVCaptureFocusModeContinuousAutoFocus
            }
            device.unlockForConfiguration()
        }
        return true
    }

    private companion object {
        const val STILL_TIMEOUT_MILLIS = 10_000L
        const val RESTART_BACKOFF_SECONDS = 10.0
    }
}

// Both enums name a landscape by the side the home indicator is on (see DisplayRotation.from).
private val DisplayRotation.videoOrientation: AVCaptureVideoOrientation
    get() =
        when (this) {
            DisplayRotation.ROTATION_0 -> AVCaptureVideoOrientationPortrait
            DisplayRotation.ROTATION_90 -> AVCaptureVideoOrientationLandscapeLeft
            DisplayRotation.ROTATION_180 -> AVCaptureVideoOrientationPortraitUpsideDown
            DisplayRotation.ROTATION_270 -> AVCaptureVideoOrientationLandscapeRight
        }
