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

/** Julian Day at the J2000.0 epoch (2000-01-01 12:00 TT, approximated as UTC here). */
const val JD_J2000: Double = 2451545.0

/** Julian Day number of the Unix epoch (1970-01-01 00:00 UTC). */
private const val JD_UNIX_EPOCH: Double = 2440587.5

private const val MILLIS_PER_DAY: Double = 86_400_000.0

/**
 * The Julian Day for this instant, using the exact identity
 * `JD = epochMillis / 86_400_000 + 2440587.5`.
 *
 * v1's formula (`367*Y - …`) was only valid for 1900–2099 and pulled in `Calendar`/`TimeZone`;
 * this identity is exact for all dates (see docs/design/core-math-astronomy.md).
 */
fun Instant.julianDay(): Double = toEpochMilliseconds() / MILLIS_PER_DAY + JD_UNIX_EPOCH

/** Julian centuries elapsed since J2000.0. */
fun Instant.julianCenturiesJ2000(): Double = (julianDay() - JD_J2000) / 36525.0

/**
 * Delta-T (TT − UT) in seconds: the gap between the UT clock and the uniform Terrestrial Time the
 * ephemeris series are expressed in. Espenak–Meeus polynomial for 2005–2050 (about 69 s in the
 * app's era). Only ~1 minute of clock time, but at the Moon's ~0.5′/minute motion it is visible in
 * eclipse timing. [KeplerianEphemeris] intentionally omits this (faithful v1 port); the
 * higher-precision ephemeris uses it.
 */
fun Instant.deltaTSeconds(): Double {
    val year = 2000.0 + (julianDay() - JD_J2000) / 365.25
    val u = year - 2000.0
    return 62.92 + 0.32217 * u + 0.005589 * u * u
}

/** Julian centuries of Terrestrial Time since J2000.0 — the time argument the Meeus/ELP series expect. */
fun Instant.julianCenturiesTerrestrialJ2000(): Double =
    (julianDay() + deltaTSeconds() / 86_400.0 - JD_J2000) / 36525.0

/**
 * Local mean sidereal time in degrees, for the given longitude (negative for west).
 *
 * Same approximation as v1 (`280.461 + 360.98564737 · Δdays`), but computed in `Double` — v1
 * cast the J2000 reference to `Float`, costing arcminutes decades from the epoch.
 */
fun meanSiderealTimeDeg(
    time: Instant,
    longitudeDeg: Double,
): Double {
    val delta = time.julianDay() - JD_J2000
    val gstDeg = 280.461 + 360.98564737 * delta
    return normalizeDegrees(gstDeg + longitudeDeg)
}
