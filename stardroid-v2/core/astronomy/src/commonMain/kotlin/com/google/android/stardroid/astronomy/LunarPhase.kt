/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.normalizeDegrees
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * The eight conventional lunar phases. [targetElongationDeg] is the Moon–Sun elongation (Moon
 * minus Sun, measured eastward) at which each phase occurs, used by [nextLunarPhaseEvent].
 */
enum class LunarPhase(val targetElongationDeg: Double) {
    NEW(0.0),
    WAXING_CRESCENT(45.0),
    FIRST_QUARTER(90.0),
    WAXING_GIBBOUS(135.0),
    FULL(180.0),
    WANING_GIBBOUS(225.0),
    LAST_QUARTER(270.0),
    WANING_CRESCENT(315.0),
}

/**
 * Classifies the Moon's phase at [time] into one of the eight [LunarPhase] buckets. Follows the
 * structure of v1's `getLunarPhaseImageId` (which selected a drawable), but on the corrected phase
 * angle (0° = full, 180° = new — v1's was inverted; see [KeplerianEphemeris]). v2 returns the
 * phase and lets the app map it to imagery; waxing/waning is disambiguated by sampling a day
 * later.
 */
fun lunarPhaseBucket(
    time: Instant,
    ephemeris: Ephemeris = KeplerianEphemeris,
): LunarPhase {
    // phaseAngleDeg is 0° at full moon and 180° at new moon, and decreases as the Moon waxes.
    // Thresholds are v1's elongation cut-points (22.5° / 67.5° / 112.5° / 150°) expressed as phase
    // angle = 180° − elongation, so the bucket boundaries land exactly where v1's drawable choice
    // did despite the low-precision approximation (a "full" moon only reaches ~30° here, not 0°).
    val phase = ephemeris.phaseAngleDeg(SolarSystemBody.MOON, time)
    if (phase < 30.0) return LunarPhase.FULL
    if (phase > 157.5) return LunarPhase.NEW
    val waxing = ephemeris.phaseAngleDeg(SolarSystemBody.MOON, time + 1.days) < phase
    return when {
        phase < 67.5 -> if (waxing) LunarPhase.WAXING_GIBBOUS else LunarPhase.WANING_GIBBOUS
        phase < 112.5 -> if (waxing) LunarPhase.FIRST_QUARTER else LunarPhase.LAST_QUARTER
        else -> if (waxing) LunarPhase.WAXING_CRESCENT else LunarPhase.WANING_CRESCENT
    }
}

private const val SYNODIC_MONTH_DAYS = 29.530588853

/**
 * The first instant strictly after [after] at which the Moon reaches the given [phase].
 *
 * Replaces v1's hour-stepping `getNextFullMoonSlow` loop with an analytic solve: a linear
 * estimate from the synodic month, refined by Newton steps on the Moon–Sun elongation. Accuracy
 * is limited by the underlying low-precision ephemeris and the RA-based elongation proxy (good to
 * a few hours), which is ample for time-travel presets.
 */
fun nextLunarPhaseEvent(
    phase: LunarPhase,
    after: Instant,
    ephemeris: Ephemeris = KeplerianEphemeris,
): Instant {
    val target = phase.targetElongationDeg
    var advance = normalizeDegrees(target - elongationDeg(after, ephemeris))
    if (advance < 1e-6) advance += 360.0 // result must be strictly after `after`
    var time = after + (advance / 360.0 * SYNODIC_MONTH_DAYS).days
    repeat(8) {
        val errorDeg = signedDegrees(target - elongationDeg(time, ephemeris))
        time += (errorDeg / 360.0 * SYNODIC_MONTH_DAYS).days
    }
    return time
}

/** Moon–Sun elongation (Moon RA minus Sun RA), normalized to `[0, 360)`. */
private fun elongationDeg(
    time: Instant,
    ephemeris: Ephemeris,
): Double {
    val moonRa = ephemeris.geocentricPosition(SolarSystemBody.MOON, time).raDeg
    val sunRa = ephemeris.geocentricPosition(SolarSystemBody.SUN, time).raDeg
    return normalizeDegrees(moonRa - sunRa)
}

/** Maps an angle difference to `[-180, 180)`. */
private fun signedDegrees(deg: Double): Double = normalizeDegrees(deg + 180.0) - 180.0
