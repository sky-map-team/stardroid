/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * Everything the moon-phase home-screen widget shows, computed offline (D75). Pure so the
 * platform widget code (Glance today) contains no astronomy: it
 * formats these fields and draws a disc from [illuminatedFraction]/[waxing]/[mirrored].
 */
data class MoonWidgetModel(
    val phase: LunarPhase,
    /** Fraction of the disc illuminated, in `[0, 1]`. */
    val illuminatedFraction: Double,
    /** Whether the illuminated fraction is increasing (lit limb on the east/right, N hemisphere). */
    val waxing: Boolean,
    /**
     * Whether the disc should be drawn mirrored: a southern-hemisphere observer sees the Moon
     * "upside down", putting the lit limb on the opposite side. False without a location.
     */
    val mirrored: Boolean,
    /** Next moonrise after the model's time, or null without a location or crossing (D51). */
    val riseTime: Instant?,
    /** Next moonset after the model's time, or null without a location or crossing (D51). */
    val setTime: Instant?,
)

/**
 * The widget model at [time] for an observer at [location] — null when no location has ever
 * been acquired (widget placed before first app run), which degrades to a geometry-only model:
 * phase and illumination are geocentric facts, rise/set need somewhere to stand.
 */
fun moonWidgetModel(
    time: Instant,
    location: LatLong?,
    ephemeris: Ephemeris = KeplerianEphemeris,
): MoonWidgetModel {
    // Waxing = phase angle decreasing, the same disambiguation lunarPhaseBucket uses.
    val waxing =
        ephemeris.phaseAngleDeg(SolarSystemBody.MOON, time + 1.days) <
            ephemeris.phaseAngleDeg(SolarSystemBody.MOON, time)
    return MoonWidgetModel(
        phase = lunarPhaseBucket(time, ephemeris),
        illuminatedFraction = ephemeris.illuminatedFraction(SolarSystemBody.MOON, time),
        waxing = waxing,
        mirrored = location != null && location.latitudeDeg < 0,
        riseTime =
            location?.let {
                nextRiseSetTime(SolarSystemBody.MOON, time, it, RiseSetIndicator.RISE, ephemeris)
            },
        setTime =
            location?.let {
                nextRiseSetTime(SolarSystemBody.MOON, time, it, RiseSetIndicator.SET, ephemeris)
            },
    )
}
