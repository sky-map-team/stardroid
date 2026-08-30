/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.normalizeDegrees
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Whether a rise or a set is wanted (v1 `CelestialObject.RiseSetIndicator`). */
enum class RiseSetIndicator { RISE, SET }

// v1's convergence bounds: give up after 25 Newton-ish sweeps or when the correction drops
// under 0.008 hours (~29 seconds).
private const val MAX_ITERATIONS = 25
private const val CONVERGENCE_HOURS = 0.008

/**
 * The Sun altitude marking the end of civil twilight: the point a bright object — a planet, a
 * satellite — stands out against the sky. Not the point the sky is *dark*; that is
 * [NAUTICAL_TWILIGHT_SUN_ALT_DEG], and the two are deliberately different.
 *
 * Lives here rather than in `:core:events` so the events engine and the satellite pass predictor
 * share one definition and cannot drift apart. (`:core:events` depends on this module, not the
 * other way round, so this is the only direction the sharing can go.)
 */
const val CIVIL_TWILIGHT_SUN_ALT_DEG: Double = -6.0

/**
 * The Sun altitude marking the end of nautical twilight, when the sky is fully dark rather than
 * merely dark enough — the point the events engine calls "darkness starts".
 *
 * Deliberately a *different* threshold from [CIVIL_TWILIGHT_SUN_ALT_DEG], not a duplicate of it:
 * −6° is early enough that a bright planet or satellite is already worth looking at, while −12°
 * is when faint objects come out. Both are standard definitions, so both live here.
 */
const val NAUTICAL_TWILIGHT_SUN_ALT_DEG: Double = -12.0

/**
 * The horizon altitude at which [body] is considered risen/set: -0.83° accounts for refraction
 * plus the solar/lunar semidiameter (v1 `bodySize`, overridden only by Sun and Moon); every
 * other body is a point on the geometric horizon.
 */
fun horizonAltitudeDeg(body: SolarSystemBody): Double =
    when (body) {
        SolarSystemBody.SUN, SolarSystemBody.MOON -> -0.83
        else -> 0.0
    }

/**
 * The next time [body] rises or sets at [location] after [after], or null if it doesn't within
 * the next few days (circumpolar or never-risen bodies, the polar day/night cases) — v1's
 * `calcNextRiseSetTime`, where a null drove the "sun will not set" toast.
 *
 * Same Astronomical Almanac hour-angle iteration as v1, with two deliberate departures
 * (D50): the day bookkeeping runs in UT rather than v1's device-zone `Calendar` arithmetic
 * (an answer already past re-solves on the next UT day, so the two agree on the next event),
 * and a horizon the body never crosses returns null instead of iterating on NaN. Positions
 * are topocentric (D54), so the Moon's diurnal parallax — worth tens of minutes on its
 * rise/set times, which v1 got wrong — is accounted for.
 */
fun nextRiseSetTime(
    body: SolarSystemBody,
    after: Instant,
    location: LatLong,
    indicator: RiseSetIndicator,
    ephemeris: Ephemeris = KeplerianEphemeris,
): Instant? =
    nextRiseSetTime(
        position = { ephemeris.topocentricPosition(body, it, location) },
        after = after,
        location = location,
        indicator = indicator,
        horizonAltitudeDeg = horizonAltitudeDeg(body),
    )

/**
 * The generalized solver (D51): the next time an object at [position] crosses
 * [horizonAltitudeDeg] at [location] after [after], or null if it doesn't within the next day.
 * [position] is read at each trial time, so a fixed RA/Dec (a star, a deep-sky object)
 * converges in two sweeps while a moving body iterates as v1 did.
 */
fun nextRiseSetTime(
    position: (Instant) -> RaDec,
    after: Instant,
    location: LatLong,
    indicator: RiseSetIndicator,
    horizonAltitudeDeg: Double = 0.0,
): Instant? {
    // Scan today plus the next two UT days: an event already past re-solves on the following
    // day (the Moon's rise/set drifts ~50 minutes a day, far beyond the solver's tolerance),
    // and the Moon's ~24h50m day skips one rise and one set day a month, pushing the next
    // event a further day out. Genuinely circumpolar cases stay null across the whole window.
    for (dayOffset in 0..2) {
        val candidate =
            riseSetOnDayOf(
                position,
                after + dayOffset.days,
                location,
                indicator,
                horizonAltitudeDeg,
            )
        if (candidate != null && candidate >= after) return candidate
    }
    return null
}

/** The instant an object at [position] rises/sets on the UT day containing [date], or null. */
private fun riseSetOnDayOf(
    position: (Instant) -> RaDec,
    date: Instant,
    location: LatLong,
    indicator: RiseSetIndicator,
    horizonAltitudeDeg: Double,
): Instant? {
    val utHours =
        riseSetUtHours(position, date, location, indicator, horizonAltitudeDeg) ?: return null
    val dayStart =
        Instant.fromEpochMilliseconds(
            date.toEpochMilliseconds().floorDiv(MILLIS_PER_UT_DAY) * MILLIS_PER_UT_DAY,
        )
    return dayStart + utHours.hours
}

/**
 * The altitude of an object at [position] above the horizon at [time] for an observer at
 * [location], in degrees — the standard `sin alt = sin lat · sin dec + cos lat · cos dec ·
 * cos ha` (Astronomical Almanac p487). What the info card uses to tell "circumpolar" from
 * "never rises" when the solver returns null for both events (D51).
 */
fun altitudeDeg(
    position: RaDec,
    time: Instant,
    location: LatLong,
): Double {
    val haRad =
        (meanSiderealTimeDeg(time, location.longitudeDeg) - position.raDeg) * DEGREES_TO_RADIANS
    val latRad = location.latitudeDeg * DEGREES_TO_RADIANS
    val decRad = position.decDeg * DEGREES_TO_RADIANS
    val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
    return asin(sinAlt.coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
}

/**
 * The azimuth of an object at [position] at [time] for an observer at [location], in degrees
 * from north, eastward `[0, 360)` — the [altitudeDeg] formula family solved for the horizontal
 * angle. The events engine uses it to say which way to look (D75 phase 2).
 */
fun azimuthDeg(
    position: RaDec,
    time: Instant,
    location: LatLong,
): Double {
    val haRad =
        (meanSiderealTimeDeg(time, location.longitudeDeg) - position.raDeg) * DEGREES_TO_RADIANS
    val latRad = location.latitudeDeg * DEGREES_TO_RADIANS
    val decRad = position.decDeg * DEGREES_TO_RADIANS
    val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
    val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))
    val sinAz = -sin(haRad) * cos(decRad) / cos(altRad)
    val cosAz = (sin(decRad) - sinAlt * sin(latRad)) / (cos(altRad) * cos(latRad))
    return normalizeDegrees(atan2(sinAz, cosAz) * RADIANS_TO_DEGREES)
}

/**
 * v1's `calcRiseSetTime`: iterates the hour of day (UT) at which the object's hour angle
 * matches the rise/set hour angle for [location], reading the object's [position] at each
 * trial time. Returns null on non-convergence or when the object never crosses the horizon.
 */
private fun riseSetUtHours(
    position: (Instant) -> RaDec,
    date: Instant,
    location: LatLong,
    indicator: RiseSetIndicator,
    horizonAltitudeDeg: Double,
): Double? {
    val dayStart =
        Instant.fromEpochMilliseconds(
            date.toEpochMilliseconds().floorDiv(MILLIS_PER_UT_DAY) * MILLIS_PER_UT_DAY,
        )
    val sign = if (indicator == RiseSetIndicator.RISE) 1.0 else -1.0
    var delta = 5.0
    var ut = 12.0
    var iterations = 0
    while (abs(delta) > CONVERGENCE_HOURS && iterations < MAX_ITERATIONS) {
        val trial = dayStart + ut.hours
        val (raDeg, decDeg) = position(trial)
        // GHA = GST - RA, in degrees.
        val ghaDeg = meanSiderealTimeDeg(trial, 0.0) - raDeg
        val hourAngleDeg =
            riseSetHourAngleDeg(horizonAltitudeDeg, location.latitudeDeg, decDeg)
                ?: return null
        delta = (ghaDeg + location.longitudeDeg + sign * hourAngleDeg) / 15.0
        if (!delta.isFinite()) return null
        delta %= 24.0
        if (delta > 12.0) delta -= 24.0
        if (delta < -12.0) delta += 24.0
        ut = (ut - delta) % 24.0
        if (ut < 0.0) ut += 24.0
        iterations++
    }
    return if (iterations == MAX_ITERATIONS) null else ut
}

/**
 * The hour angle (degrees from the meridian) at which an object at [declinationDeg] stands at
 * [altitudeDeg] for an observer at [latitudeDeg] — Astronomical Almanac p487:
 * `cos ha = (sin alt - sin lat · sin dec) / (cos lat · cos dec)`. Null when the object never
 * reaches that altitude (`|cos ha| > 1`), where v1 iterated on the resulting NaN.
 */
private fun riseSetHourAngleDeg(
    altitudeDeg: Double,
    latitudeDeg: Double,
    declinationDeg: Double,
): Double? {
    val altRad = altitudeDeg * DEGREES_TO_RADIANS
    val latRad = latitudeDeg * DEGREES_TO_RADIANS
    val decRad = declinationDeg * DEGREES_TO_RADIANS
    val cosHa = (sin(altRad) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
    // NaN when both numerator and denominator vanish (a polar observer with the body's
    // declination right at the horizon altitude); abs(NaN) > 1.0 is false, so guard explicitly.
    if (cosHa.isNaN() || abs(cosHa) > 1.0) return null
    return acos(cosHa) * RADIANS_TO_DEGREES
}

private const val MILLIS_PER_UT_DAY = 86_400_000L
