/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import com.google.android.stardroid.math.normalizeDegrees
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * One sampled readout for the map HUD (map-hud.md, D65): where the screen centre points, in
 * equatorial and horizontal coordinates, plus the field of view and the drag-to-align
 * correction (D64). Produced by `MapViewModel.hudState` at a throttled cadence.
 */
data class HudState(
    val raDeg: Double,
    val decDeg: Double,
    val altDeg: Double,
    /** True-north azimuth, normalized to [0, 360). */
    val azDeg: Double,
    val fovDeg: Double,
    /** True while the AR camera layer locks the FOV to the lens (D64) — lock glyph. */
    val fovLocked: Boolean = false,
    val correctionAzDeg: Double,
    val correctionAltDeg: Double,
)

/**
 * The HUD's pure value-shaping helpers, split from the composable so JVM tests can pin the
 * edge cases (minute/hour wrap, cardinal bucketing, the hidden-at-zero rule) without Android.
 * Number-to-text rendering itself happens in string resources, which stay locale-aware.
 */
object HudFormats {
    /**
     * Right ascension as the astronomy-conventional hours/minutes pair (D65): 279.4° →
     * 18 h 38 m. Minutes round, carrying into the hour so 59.6 m never prints as "60 m",
     * and the hour wraps 24 → 0.
     */
    fun raHoursMinutes(raDeg: Double): Pair<Int, Int> {
        val hours = normalizeDegrees(raDeg) / DEGREES_PER_HOUR
        var wholeHours = floor(hours).toInt()
        var minutes = ((hours - wholeHours) * MINUTES_PER_HOUR).roundToInt()
        if (minutes == MINUTES_PER_HOUR) {
            minutes = 0
            wholeHours = (wholeHours + 1) % HOURS_PER_DAY
        }
        return wholeHours to minutes
    }

    /** Azimuth for display: rounded whole degrees, with 359.7° wrapping to 0°, not 360°. */
    fun azimuthWholeDegrees(azDeg: Double): Int =
        normalizeDegrees(azDeg).roundToInt() % DEGREES_PER_TURN

    /**
     * Index into the 16-point compass rose (N at 0, then NNE… every 22.5°) for the cardinal
     * suffix on the azimuth row.
     */
    fun cardinalIndex(azDeg: Double): Int =
        (normalizeDegrees(azDeg) / DEGREES_PER_CARDINAL).roundToInt() % CARDINAL_COUNT

    /**
     * Whether the correction row shows (D65: strictly hidden at zero). The threshold soaks
     * up float dust; any correction a person could have dragged in clears it easily.
     */
    fun correctionActive(
        correctionAzDeg: Double,
        correctionAltDeg: Double,
    ): Boolean = hypot(correctionAzDeg, correctionAltDeg) >= CORRECTION_VISIBLE_DEG

    /**
     * How many decimals the FOV readout needs: whole degrees at 10° and above, one decimal
     * between 1° and 10°, two below that so the deepest zooms (down to 0.01°) still change
     * the number instead of freezing at "0.0°".
     */
    fun fovDecimals(fovDeg: Double): Int =
        when {
            fovDeg >= FINE_FOV_THRESHOLD_DEG -> 0
            fovDeg >= FINEST_FOV_THRESHOLD_DEG -> 1
            else -> 2
        }

    private const val DEGREES_PER_HOUR = 15.0
    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24
    private const val DEGREES_PER_TURN = 360
    private const val DEGREES_PER_CARDINAL = 22.5
    const val CARDINAL_COUNT = 16
    private const val CORRECTION_VISIBLE_DEG = 0.05
    private const val FINE_FOV_THRESHOLD_DEG = 10.0
    private const val FINEST_FOV_THRESHOLD_DEG = 1.0
}
