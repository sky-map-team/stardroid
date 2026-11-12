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
import android.os.SystemClock
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.stardroid.share.SkyShare
import com.google.android.stardroid.ui.map.ArCameraSpecs
import com.google.android.stardroid.ui.map.ArManualExposure
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    /** How the share still is taken; the debug switch flips it while the night path is tuned. */
    enum class StillMode { AUTO, NIGHT }

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var bound = false

    // What bind() was given, kept so the session can be rebuilt for a night still.
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var viewAspectLongOverShort = 1.0
    private var onReady: ((ArCameraSpecs) -> Unit)? = null
    private var onPreviewLuma: ((Double) -> Unit)? = null
    private var analysisExecutor: ExecutorService? = null
    private var lastLumaMillis = 0L

    // The latest controls, re-applied after a rebind drops them.
    private var zoomRatio = 1.0
    private var exposureIndex = 0
    private var manualExposure: ArManualExposure? = null

    var stillMode by mutableStateOf(StillMode.AUTO)

    /** Whether the device offers a vendor night mode (CameraX Extensions) for stills. */
    var nightAvailable by mutableStateOf(false)
        private set

    /** What the extension probe found, for the debug chip: modes offered, or why none. */
    var extensionReport by mutableStateOf("probing")
        private set

    // NIGHT where offered, else the vendor's AUTO (which picks night itself in low light).
    private var stillExtensionMode = ExtensionMode.NONE

    /** True while a night still is being taken: the UI should ask the user to hold still. */
    var capturingNight by mutableStateOf(false)
        private set

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
        onPreviewLuma: (Double) -> Unit,
        onReady: (ArCameraSpecs) -> Unit,
    ) {
        bound = true
        this.onPreviewLuma = onPreviewLuma
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        this.viewAspectLongOverShort = viewAspectLongOverShort
        this.onReady = onReady
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get().also { this.provider = it }
            // The toggle may have flipped off while the provider was starting up.
            if (!bound) return@addListener
            val camera = bindSession(night = false)
            if (camera != null) onReady(specsFor(camera, viewAspectLongOverShort))
            probeNightExtension(provider)
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        bound = false
        camera = null
        imageCapture = null
        previewView = null
        lifecycleOwner = null
        onReady = null
        onPreviewLuma = null
        provider?.unbindAll()
        analysisExecutor?.shutdown()
        analysisExecutor = null
        nightAvailable = false
        extensionReport = "probing"
    }

    /**
     * Rebinds the preview and the still capture, either as the normal live session or as the
     * vendor night-mode session. Null when it could not bind (no usable camera, in use,
     * disabled by policy, …): the layer stays on showing the map over black.
     */
    private fun bindSession(night: Boolean): Camera? {
        val provider = provider ?: return null
        val view = previewView ?: return null
        val owner = lifecycleOwner ?: return null
        return try {
            val selector =
                if (night) {
                    extensions?.getExtensionEnabledCameraSelector(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        stillExtensionMode,
                    ) ?: return null
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            val preview = buildPreview(tuned = !night)
            preview.surfaceProvider = view.surfaceProvider
            val capture = buildCapture(view.display.rotation, tuned = !night)
            provider.unbindAll()
            // The brightness probe rides only the live session; extension sessions take just
            // preview and capture.
            val useCases =
                listOfNotNull<UseCase>(preview, capture, if (night) null else buildAnalysis())
            val session =
                provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
            camera = session
            imageCapture = capture
            session
        } catch (e: Exception) {
            Log.w(TAG, "Camera bind failed (night=$night)", e)
            null
        }
    }

    /**
     * A few brightness samples a second from the live preview, so the map can dim a camera
     * image bright enough to drown it out (see `ArScrimMath`). Auto-exposure holds the image
     * near mid-grey, so the sensor settings can't tell us; the pixels can.
     */
    private fun buildAnalysis(): ImageAnalysis {
        val executor =
            analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                analysisExecutor = it
            }
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor) { image ->
                    image.use { frame ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastLumaMillis < LUMA_INTERVAL_MS) return@use
                        lastLumaMillis = now
                        val luma = meanLuma(frame)
                        ContextCompat.getMainExecutor(
                            context,
                        ).execute { onPreviewLuma?.invoke(luma) }
                    }
                }
            }
    }

    /** Mean of a coarse grid of the Y plane, 0–1. */
    private fun meanLuma(image: ImageProxy): Double {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var sum = 0L
        for (row in 0 until LUMA_GRID) {
            val y = row * (image.height - 1) / (LUMA_GRID - 1)
            for (col in 0 until LUMA_GRID) {
                val x = col * (image.width - 1) / (LUMA_GRID - 1)
                sum += buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
            }
        }
        return sum / (LUMA_GRID * LUMA_GRID * 255.0)
    }

    private fun probeNightExtension(provider: ProcessCameraProvider) {
        val future = ExtensionsManager.getInstanceAsync(context, provider)
        future.addListener({
            try {
                val manager = future.get()
                extensions = manager
                val offered =
                    EXTENSION_MODES.filter { (mode, _) ->
                        manager.isExtensionAvailable(CameraSelector.DEFAULT_BACK_CAMERA, mode)
                    }
                val offeredModes = offered.map { it.first }
                stillExtensionMode =
                    listOf(ExtensionMode.NIGHT, ExtensionMode.AUTO)
                        .firstOrNull { it in offeredModes }
                        ?: ExtensionMode.NONE
                nightAvailable = bound && stillExtensionMode != ExtensionMode.NONE
                extensionReport =
                    "camerax: " + offered.joinToString { it.second }.ifEmpty { "none" } +
                    "; platform: " + platformExtensions()
                Log.i(TAG, "Extensions offered: $extensionReport")
            } catch (e: Exception) {
                Log.w(TAG, "Extensions probe failed", e)
                extensionReport = "probe failed: ${e.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** The platform's own extension list (API 31+), to compare with what CameraX offers. */
    @SuppressLint("UnsafeOptInUsageError")
    private fun platformExtensions(): String {
        // getCameraExtensionCharacteristics is API 31; minSdk 28 would throw NoSuchMethodError.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "n/a (API < 31)"
        val camera = camera ?: return "no camera"
        return try {
            val id = Camera2CameraInfo.from(camera.cameraInfo).cameraId
            val supported =
                context.getSystemService(CameraManager::class.java)
                    .getCameraExtensionCharacteristics(id)
                    .supportedExtensions
            supported.joinToString { PLATFORM_EXTENSION_NAMES[it] ?: "#$it" }
                .ifEmpty { "none" }
        } catch (e: Exception) {
            Log.w(TAG, "Platform extension probe failed", e)
            "failed"
        }
    }

    /**
     * A camera still for the share layouts, upright per its rotation metadata; null when
     * the camera isn't running or the capture fails (the caller falls back to map-only).
     *
     * [StillMode.AUTO] always uses auto exposure, even while the manual preview sliders are
     * engaged; [StillMode.NIGHT] takes the vendor night capture when the device has one.
     *
     * Sized for sharing, not for the sensor: a full-resolution still is ~48 MB on a 12 MP
     * rear camera, and it is only ever centre-cropped into a share composite that is a
     * fraction of that (audit-2026-08 M5). The caller owns the result and recycles it.
     */
    suspend fun takeStill(): Bitmap? =
        if (stillMode == StillMode.NIGHT && nightAvailable) {
            takeNightStill() ?: takeAutoStill()
        } else {
            takeAutoStill()
        }

    /** Manual options apply to stills too, so drop them and let AE settle before the shot. */
    private suspend fun takeAutoStill(): Bitmap? {
        val manual = manualExposure
        if (manual == null) return capture()
        applyManualOptions(null)
        try {
            delay(AE_SETTLE_MS)
            return capture()
        } finally {
            applyManualOptions(manual)
        }
    }

    /**
     * Rebinds into the night-mode session for the shot, then back to the live one. The
     * preview blanks for the swap and the capture itself takes seconds; null when the night
     * session could not bind (the caller falls back to the auto still).
     */
    private suspend fun takeNightStill(): Bitmap? {
        capturingNight = true
        try {
            if (bindSession(night = true) == null) return null
            return capture()
        } finally {
            capturingNight = false
            if (bound) {
                if (bindSession(night = false) != null) {
                    reapplyControls()
                } else {
                    Log.w(TAG, "Live session failed to rebind after night still")
                }
            }
        }
    }

    private suspend fun capture(): Bitmap? {
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

    private fun reapplyControls() {
        camera?.cameraControl?.setZoomRatio(zoomRatio.toFloat())
        camera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
        applyManualOptions(manualExposure)
    }

    /** Digital zoom driven by the map's pinch while the FOV is camera-locked. */
    fun setZoomRatio(ratio: Double) {
        zoomRatio = ratio
        camera?.cameraControl?.setZoomRatio(ratio.toFloat())
    }

    /** AE compensation from the in-AR exposure slider (lever 1 of the exposure plan). */
    fun setExposureIndex(index: Int) {
        exposureIndex = index
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
        manualExposure = manual
        applyManualOptions(manual)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun applyManualOptions(manual: ArManualExposure?) {
        val control = camera?.cameraControl ?: return
        val camera2 = Camera2CameraControl.from(control)
        val options =
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
        camera2.setCaptureRequestOptions(options)
    }

    /**
     * The sky tuning shared by the preview and the still: focus fixed at infinity (focus
     * distance 0 diopters) instead of letting AF hunt across the room the phone happens to be
     * in, and — lever 3 of the exposure plan — Low Light Boost AE (Android 15+, supported
     * devices), a large night-time win exactly where this feature lives. Fixed-focus hardware
     * ignores the focus keys.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun applySkyTuning(extender: Camera2Interop.Extender<*>) {
        extender
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF,
            )
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
        if (lowLightBoostAvailable()) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                LOW_LIGHT_BOOST_AE_MODE,
            )
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun lowLightBoostAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        return try {
            provider?.availableCameraInfos.orEmpty().any { info ->
                Camera2CameraInfo.from(info)
                    .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                    ?.contains(LOW_LIGHT_BOOST_AE_MODE) == true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Low Light Boost probe failed", e)
            false
        }
    }

    /** [tuned] false for the night-extension session, which owns its own capture settings. */
    @SuppressLint("UnsafeOptInUsageError")
    private fun buildPreview(tuned: Boolean): Preview {
        val builder = Preview.Builder()
        if (tuned) applySkyTuning(Camera2Interop.Extender(builder))
        return builder.build()
    }

    /**
     * The share still (camera-ar-mode.md slice 4): a real capture, not a preview scrape. Quality
     * over latency now that stills are being tuned for night; the same sky tuning as the
     * preview, so the still is no worse than what the user framed.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun buildCapture(
        rotation: Int,
        tuned: Boolean,
    ): ImageCapture {
        val builder =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(rotation)
        if (tuned) applySkyTuning(Camera2Interop.Extender(builder))
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

        private val PLATFORM_EXTENSION_NAMES =
            mapOf(0 to "auto", 1 to "face", 2 to "bokeh", 3 to "hdr", 4 to "night")

        private val EXTENSION_MODES =
            listOf(
                ExtensionMode.NIGHT to "night",
                ExtensionMode.AUTO to "auto",
                ExtensionMode.HDR to "hdr",
                ExtensionMode.BOKEH to "bokeh",
                ExtensionMode.FACE_RETOUCH to "face",
            )

        private const val LUMA_INTERVAL_MS = 250L
        private const val LUMA_GRID = 24

        /** How long AE gets to converge after the manual options are dropped for a still. */
        private const val AE_SETTLE_MS = 600L

        /** ~30 fps; long manual exposures stretch the frame duration past this. */
        private const val DEFAULT_FRAME_DURATION_NS = 33_333_333L

        /** `CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY` (API 35). */
        private const val LOW_LIGHT_BOOST_AE_MODE =
            CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
    }
}
