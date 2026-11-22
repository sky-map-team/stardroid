/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.camera.arLenses
import com.google.android.stardroid.camera.cameraShortHalfTan
import com.google.android.stardroid.camera.fullAngleDeg
import com.google.android.stardroid.ui.map.ArCameraSpecs
import com.google.android.stardroid.ui.map.ArLens
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.constituentDevices
import platform.AVFoundation.deviceType
import platform.AVFoundation.geometricDistortionCorrectedVideoFieldOfView
import platform.AVFoundation.geometricDistortionCorrectionEnabled
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.maxExposureTargetBias
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.minExposureTargetBias
import platform.AVFoundation.virtualDeviceSwitchOverVideoZoomFactors
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.NSNumber
import kotlin.math.ceil
import kotlin.math.floor

/** The exposure slider's step: a third of a stop (camera-ar-launch.md §4.2). */
internal const val EXPOSURE_STEPS_PER_EV = 3

/** The most zoom offered, as a multiple of the main wide lens. */
private const val MAX_ZOOM_RATIO = 8.0

/**
 * What the configured device reports, read on the session's queue. Its zoom factor counts from
 * the widest lens; the app's ratio counts from the main wide one, [mainFactor] times further in.
 */
internal class CameraOptics(
    val mainFactor: Double,
    private val formatWidth: Int,
    private val formatHeight: Int,
    private val switchOverFactors: List<Double>,
    private val lensFovsDeg: List<Double?>,
    private val zoomMin: Double,
    private val zoomMax: Double,
    private val exposureMin: Int,
    private val exposureMax: Int,
) {
    fun specsFor(
        viewWidth: Double,
        viewHeight: Double,
        landscape: Boolean,
    ): ArCameraSpecs {
        val halfTans =
            lensFovsDeg.map { fov ->
                fov?.let {
                    cameraShortHalfTan(
                        it,
                        formatWidth,
                        formatHeight,
                        viewWidth,
                        viewHeight,
                        landscape,
                    )
                }
            }
        val lenses = arLenses(switchOverFactors, halfTans, mainFactor)
        return lockedSpecs(lenses, zoomMin, zoomMax, exposureMin, exposureMax)
    }

    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun read(device: AVCaptureDevice): CameraOptics {
            val format = device.activeFormat
            val (width, height) = format.dimensions()
            // Distortion correction, on by default for an ultra-wide, narrows what is shown.
            val corrected = device.geometricDistortionCorrectionEnabled
            val constituents = device.constituentDevices.filterIsInstance<AVCaptureDevice>()
            val switches =
                device.virtualDeviceSwitchOverVideoZoomFactors
                    .filterIsInstance<NSNumber>()
                    .map { it.doubleValue }
            val mainIndex =
                constituents.indexOfFirst {
                    it.deviceType == AVCaptureDeviceTypeBuiltInWideAngleCamera
                }
            val mainFactor = switches.getOrNull(mainIndex - 1) ?: 1.0
            val others =
                constituents.drop(1).map { it.fovMatching(width, height, corrected) }
            return CameraOptics(
                mainFactor = mainFactor,
                formatWidth = width,
                formatHeight = height,
                switchOverFactors = switches,
                lensFovsDeg = listOf(format.fovDeg(corrected)) + others,
                zoomMin = device.minAvailableVideoZoomFactor / mainFactor,
                zoomMax =
                    minOf(device.maxAvailableVideoZoomFactor / mainFactor, MAX_ZOOM_RATIO),
                exposureMin = ceil(device.minExposureTargetBias * EXPOSURE_STEPS_PER_EV).toInt(),
                exposureMax = floor(device.maxExposureTargetBias * EXPOSURE_STEPS_PER_EV).toInt(),
            )
        }

        // A lens's own FOV, from its format shaped like the one in use: the widest, as binned
        // and cropped formats of the same shape report less.
        private fun AVCaptureDevice.fovMatching(
            width: Int,
            height: Int,
            corrected: Boolean,
        ): Double? =
            formats
                .filterIsInstance<AVCaptureDeviceFormat>()
                .filter { it.dimensions() == width to height }
                .mapNotNull { it.fovDeg(corrected) }
                .maxOrNull()

        private fun AVCaptureDeviceFormat.fovDeg(corrected: Boolean): Double? {
            val fov =
                if (corrected) geometricDistortionCorrectedVideoFieldOfView else videoFieldOfView
            return fov.toDouble().takeIf { it > 0.0 }
        }

        @OptIn(ExperimentalForeignApi::class)
        private fun AVCaptureDeviceFormat.dimensions(): Pair<Int, Int> =
            CMVideoFormatDescriptionGetDimensions(formatDescription)
                .useContents { width to height }
    }
}

/** Specs whose map lock follows [lenses]; with none known the map's zoom stays free. */
internal fun lockedSpecs(
    lenses: List<ArLens>,
    zoomMin: Double,
    zoomMax: Double,
    exposureMin: Int,
    exposureMax: Int,
): ArCameraSpecs {
    val main = lenses.lastOrNull { it.fromZoom <= 1.0 }
    return ArCameraSpecs(
        shortSideFovDeg = main?.let { fullAngleDeg(it.halfTan * it.fromZoom) },
        zoomMin = zoomMin,
        zoomMax = zoomMax,
        exposureMin = exposureMin,
        exposureMax = exposureMax,
        lenses = lenses,
    )
}
