/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * What the physical camera can do, reported once it is up (camera-ar-mode.md/D64): the
 * short-side FOV that locks the map's zoom (null when the characteristics are unusable —
 * the map FOV then stays free), the digital zoom envelope, the AE-compensation range
 * (0..0 when unsupported — the exposure slider hides), and the manual-sensor envelope for
 * the temporary ISO/shutter dev controls (null when the device lacks `MANUAL_SENSOR`).
 */
data class ArCameraSpecs(
    val shortSideFovDeg: Double?,
    val zoomMin: Double,
    val zoomMax: Double,
    val exposureMin: Int,
    val exposureMax: Int,
    val isoRange: IntRange? = null,
    val exposureTimeRangeNs: LongRange? = null,
)

/** A manual sensor exposure (AE off): what the hardware edge applies while set. */
data class ArManualExposure(
    val iso: Int,
    val exposureTimeNs: Long,
)

/**
 * The in-AR control panel's state, null while the camera layer is off. Scrim is the camera
 * dimmer the GL layer draws (`RenderState.cameraScrim`) — the value here is the user's
 * setting; night mode's ~65% floor applies downstream in the render state, not here, so the
 * slider stays where the user left it. [isoFraction]/[shutterFraction] are the temporary
 * manual-exposure dev sliders (0 = auto, log-scaled across the sensor's range).
 */
data class ArUiState(
    val scrim: Double,
    val exposureIndex: Int,
    val exposureMin: Int,
    val exposureMax: Int,
    val isoFraction: Double = 0.0,
    val shutterFraction: Double = 0.0,
    val manualExposureSupported: Boolean = false,
) {
    val exposureSupported: Boolean get() = exposureMin != 0 || exposureMax != 0
}

/**
 * Slider-fraction ↔ sensor-value mapping for the temporary manual-exposure controls:
 * logarithmic, because both ISO and shutter time are perceived in stops. Fraction 0 means
 * "auto"; the usable scale starts just above it.
 */
object ArExposureMath {
    fun isoForFraction(
        range: IntRange,
        fraction: Double,
    ): Int = logInterpolate(range.first.toDouble(), range.last.toDouble(), fraction).roundToInt()

    fun exposureTimeForFraction(
        rangeNs: LongRange,
        fraction: Double,
    ): Long =
        logInterpolate(rangeNs.first.toDouble(), rangeNs.last.toDouble(), fraction)
            .roundToLong()

    /** "1/30s" above a second-fraction, "0.5s" below — the dev slider's readout. */
    fun formatExposureTime(exposureTimeNs: Long): String {
        val seconds = exposureTimeNs / 1e9
        return if (seconds >= 0.25) {
            "%.1fs".format(seconds)
        } else {
            "1/${(1.0 / seconds).roundToInt()}s"
        }
    }

    private fun logInterpolate(
        min: Double,
        max: Double,
        fraction: Double,
    ): Double {
        val f = fraction.coerceIn(0.0, 1.0)
        return exp(ln(min) + (ln(max) - ln(min)) * f)
    }
}
