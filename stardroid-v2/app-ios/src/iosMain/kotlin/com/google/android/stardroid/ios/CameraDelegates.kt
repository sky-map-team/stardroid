/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCaptureResolvedPhotoSettings
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.fileDataRepresentation
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetWidthOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.CoreVideo.kCVReturnSuccess
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_t
import kotlin.concurrent.AtomicInt

/** Receives one photo's encoded data, or null; answers once. */
internal class PhotoReceiver(
    private val onDone: (NSData?) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    private val answered = AtomicInt(0)

    fun fail() = answer(null)

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) = answer(if (error == null) didFinishProcessingPhoto.fileDataRepresentation() else null)

    // Always the last callback, and the only one when the capture failed before processing.
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishCaptureForResolvedSettings: AVCaptureResolvedPhotoSettings,
        error: NSError?,
    ) = answer(null)

    private fun answer(data: NSData?) {
        if (answered.compareAndSet(0, 1)) onDone(data)
    }
}

/**
 * A few brightness samples a second from the preview, as on Android, so the map can dim a camera
 * image bright enough to drown it out (`ArScrimMath`). Only the mean leaves this class.
 */
@OptIn(ExperimentalForeignApi::class)
internal class PreviewLumaProbe(
    private val onLuma: (Double) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private var lastSampleSeconds = 0.0

    fun attachTo(
        output: AVCaptureVideoDataOutput,
        queue: dispatch_queue_t,
    ) {
        output.alwaysDiscardsLateVideoFrames = true
        // Plane 0 of this format is brightness, 0–255.
        output.videoSettings =
            mapOf<Any?, Any>(
                pixelFormatKey to kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
            )
        output.setSampleBufferDelegate(this, queue)
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val now = NSProcessInfo.processInfo.systemUptime
        if (now - lastSampleSeconds < INTERVAL_SECONDS) return
        lastSampleSeconds = now
        val buffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
        if (CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly) != kCVReturnSuccess) {
            return
        }
        val plane = CVPixelBufferGetBaseAddressOfPlane(buffer, 0u)?.reinterpret<UByteVar>()
        if (plane != null) {
            val width = CVPixelBufferGetWidthOfPlane(buffer, 0u).toInt()
            val height = CVPixelBufferGetHeightOfPlane(buffer, 0u).toInt()
            val rowBytes = CVPixelBufferGetBytesPerRowOfPlane(buffer, 0u).toInt()
            var sum = 0L
            for (row in 0 until GRID) {
                val y = row * (height - 1) / (GRID - 1)
                for (column in 0 until GRID) {
                    sum += plane[y * rowBytes + column * (width - 1) / (GRID - 1)].toLong()
                }
            }
            onLuma(sum / (GRID * GRID * MAX_LUMA))
        }
        CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
    }
}

private const val INTERVAL_SECONDS = 0.25
private const val GRID = 24
private const val MAX_LUMA = 255.0

// A CF constant as a Kotlin map key; the retain balances the bridging release.
@OptIn(ExperimentalForeignApi::class)
private val pixelFormatKey: Any? by lazy {
    val key: CFStringRef? = kCVPixelBufferPixelFormatTypeKey
    CFBridgingRelease(CFRetain(key))
}
