/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.stardroid.share.SkyShare
import com.google.android.stardroid.ui.map.ArCameraSpecs
import com.google.android.stardroid.ui.map.ArManualExposure
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The through-camera mode's hardware edge (camera-ar-mode.md/D64, Option A): binds a CameraX
 * [Preview] to the [PreviewView] that sits under the translucent GL surface, and translates
 * between the map's pure state ([ArCameraSpecs], zoom ratios, exposure indices) and CameraX.
 *
 * Owned by the activity; [bind]/[unbind] follow the camera layer toggle, and CameraX's
 * lifecycle binding releases the camera whenever the activity stops — camera-on is session
 * state (D64), so nothing here persists.
 */
class SkyCameraPreview(
    private val context: Context,
) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var bound = false

    /** Whether the device has any camera at all — the AR toggle hides entirely when not. */
    val hasCamera: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /**
     * Starts the rear camera into [previewView]; [onReady] fires on the main thread once the
     * camera is up, carrying the FOV/zoom/exposure envelope for the map's FOV lock.
     */
    fun bind(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        viewAspectLongOverShort: Double,
        onReady: (ArCameraSpecs) -> Unit,
    ) {
        bound = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get().also { this.provider = it }
            // The toggle may have flipped off while the provider was starting up.
            if (!bound) return@addListener
            try {
                val preview = buildPreview()
                preview.surfaceProvider = previewView.surfaceProvider
                // The share still (camera-ar-mode.md slice 4): a real capture, not a
                // preview scrape. Latency over quality — the sky doesn't hold still for
                // multi-frame merges, and manual-exposure runtime options apply to the
                // whole session, stills included.
                val capture =
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(previewView.display.rotation)
                        .build()
                provider.unbindAll()
                val camera =
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                this.camera = camera
                this.imageCapture = capture
                onReady(specsFor(camera, viewAspectLongOverShort))
            } catch (e: Exception) {
                // No usable rear camera after all (in use, disabled by policy, …): the
                // layer stays on showing the map over black; nothing to crash over.
                Log.w(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        bound = false
        camera = null
        imageCapture = null
        provider?.unbindAll()
    }

    /**
     * A camera still for the share layouts, upright per its rotation metadata; null when
     * the camera isn't running or the capture fails (the caller falls back to map-only).
     *
     * Sized for sharing, not for the sensor: a full-resolution still is ~48 MB on a 12 MP
     * rear camera, and it is only ever centre-cropped into a share composite that is a
     * fraction of that (audit-2026-08 M5). The caller owns the result and recycles it.
     */
    suspend fun takeStill(): Bitmap? {
        val capture = imageCapture ?: return null
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap =
                            image.use { proxy ->
                                val decoded = proxy.toBitmap()
                                val degrees = proxy.imageInfo.rotationDegrees
                                if (degrees == 0) {
                                    decoded
                                } else {
                                    val rotated =
                                        Bitmap.createBitmap(
                                            decoded,
                                            0,
                                            0,
                                            decoded.width,
                                            decoded.height,
                                            Matrix().apply { postRotate(degrees.toFloat()) },
                                            true,
                                        )
                                    // The rotation built a second full-size bitmap and
                                    // `decoded` was previously dropped un-recycled. Guarded
                                    // because createBitmap hands back the source itself for a
                                    // no-op transform.
                                    if (rotated !== decoded) decoded.recycle()
                                    rotated
                                }
                            }
                        continuation.resume(SkyShare.toShareSize(bitmap))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "Still capture failed", exception)
                        continuation.resume(null)
                    }
                },
            )
        }
    }

    /** Digital zoom driven by the map's pinch while the FOV is camera-locked. */
    fun setZoomRatio(ratio: Double) {
        camera?.cameraControl?.setZoomRatio(ratio.toFloat())
    }

    /** AE compensation from the in-AR exposure slider (lever 1 of the exposure plan). */
    fun setExposureIndex(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    /**
     * The temporary manual-exposure dev controls (lever 4 of the exposure plan, exposed
     * while the night behavior is tuned): AE off with the given sensitivity and exposure
     * time, or null to return to auto (which also restores the Low Light Boost opt-in from
     * the build-time config). The frame duration stretches with long exposures so the HAL
     * never has to clip them.
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun setManualExposure(manual: ArManualExposure?) {
        val control = camera?.cameraControl ?: return
        val camera2 = Camera2CameraControl.from(control)
        camera2.captureRequestOptions =
            if (manual == null) {
                CaptureRequestOptions.Builder().build()
            } else {
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_OFF,
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.SENSOR_SENSITIVITY,
                        manual.iso,
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.SENSOR_EXPOSURE_TIME,
                        manual.exposureTimeNs,
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.SENSOR_FRAME_DURATION,
                        maxOf(manual.exposureTimeNs, DEFAULT_FRAME_DURATION_NS),
                    )
                    .build()
            }
    }

    /**
     * Lever 3 of the exposure plan: opt into Low Light Boost AE (Android 15+, supported
     * devices) via Camera2Interop — a large night-time win exactly where this feature lives.
     * Everything else about the preview is stock.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun buildPreview(): Preview {
        val builder = Preview.Builder()
        // The subject is the sky: fix focus at infinity (focus distance 0 diopters) instead
        // of letting AF hunt across the room the phone happens to be in. Fixed-focus
        // hardware ignores both keys.
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF,
            )
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                val hasBoost =
                    provider?.availableCameraInfos.orEmpty().any { info ->
                        Camera2CameraInfo.from(info)
                            .getCameraCharacteristic(
                                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES,
                            )?.contains(LOW_LIGHT_BOOST_AE_MODE) == true
                    }
                if (hasBoost) {
                    Camera2Interop.Extender(builder).setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        LOW_LIGHT_BOOST_AE_MODE,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Low Light Boost probe failed", e)
            }
        }
        return builder.build()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun specsFor(
        camera: Camera,
        viewAspectLongOverShort: Double,
    ): ArCameraSpecs {
        val info = camera.cameraInfo
        val characteristics = Camera2CameraInfo.from(info)
        val sensorSize =
            characteristics.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,
            )
        val focalLength =
            characteristics
                .getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
                )?.firstOrNull()
        val fovDeg =
            if (sensorSize != null && focalLength != null) {
                cameraShortSideFovDeg(
                    sensorLongSideMm = maxOf(sensorSize.width, sensorSize.height).toDouble(),
                    sensorShortSideMm = minOf(sensorSize.width, sensorSize.height).toDouble(),
                    focalLengthMm = focalLength.toDouble(),
                    viewAspectLongOverShort = viewAspectLongOverShort,
                )
            } else {
                null
            }
        val zoom = info.zoomState.value
        val exposure = info.exposureState
        // Manual-sensor envelope for the temporary ISO/shutter dev controls; null (hidden
        // sliders) unless the bound camera declares the MANUAL_SENSOR capability — e.g.
        // the emulator's virtual back camera doesn't, so only real hardware grows them.
        val frameworkCharacteristics =
            runCatching {
                context
                    .getSystemService(CameraManager::class.java)
                    ?.getCameraCharacteristics(characteristics.cameraId)
            }.getOrNull()
        val capabilities =
            frameworkCharacteristics
                ?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val manualSensor =
            capabilities?.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
            ) == true
        val isoRange =
            frameworkCharacteristics
                ?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                .takeIf { manualSensor }
                ?.let { it.lower..it.upper }
        val exposureTimeRange =
            frameworkCharacteristics
                ?.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                .takeIf { manualSensor }
                ?.let { it.lower..it.upper }
        return ArCameraSpecs(
            shortSideFovDeg = fovDeg,
            zoomMin = (zoom?.minZoomRatio ?: 1f).toDouble(),
            zoomMax = (zoom?.maxZoomRatio ?: 1f).toDouble(),
            exposureMin =
                if (exposure.isExposureCompensationSupported) {
                    exposure.exposureCompensationRange.lower
                } else {
                    0
                },
            exposureMax =
                if (exposure.isExposureCompensationSupported) {
                    exposure.exposureCompensationRange.upper
                } else {
                    0
                },
            isoRange = isoRange,
            exposureTimeRangeNs = exposureTimeRange,
        )
    }

    companion object {
        private const val TAG = "SkyCameraPreview"

        /** ~30 fps; long manual exposures stretch the frame duration past this. */
        private const val DEFAULT_FRAME_DURATION_NS = 33_333_333L

        /** `CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY` (API 35). */
        private const val LOW_LIGHT_BOOST_AE_MODE =
            CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
    }
}
