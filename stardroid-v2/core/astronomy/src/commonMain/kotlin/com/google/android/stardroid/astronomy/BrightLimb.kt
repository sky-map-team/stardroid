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
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Position angle of the midpoint of a body's bright limb — Meeus chapter 48's χ — in degrees
 * **east of north**, measured at the body on the celestial sphere.
 *
 * This is the direction the lit side faces: χ = 0° puts the lit limb toward the north celestial
 * pole, 90° toward the east. Together with the illuminated fraction it fixes the terminator
 * completely, which is what lets the phase be drawn procedurally (D88) instead of baked into
 * eight bitmaps that also drag the surface features around with them.
 *
 * The Sun's position is geocentric, and [body]'s should be too: Meeus notes that using a
 * topocentric body position with a geocentric Sun introduces an error well under a degree, and
 * for the Moon the parallax mostly moves the disc, not the illumination.
 */
fun brightLimbAngleDeg(
    body: RaDec,
    sun: RaDec,
): Double {
    val ra = body.raDeg * DEGREES_TO_RADIANS
    val dec = body.decDeg * DEGREES_TO_RADIANS
    val sunRa = sun.raDeg * DEGREES_TO_RADIANS
    val sunDec = sun.decDeg * DEGREES_TO_RADIANS
    val dRa = sunRa - ra
    // Meeus 48.5.
    val numerator = cos(sunDec) * sin(dRa)
    val denominator = sin(sunDec) * cos(dec) - cos(sunDec) * sin(dec) * cos(dRa)
    val chi = atan2(numerator, denominator) * RADIANS_TO_DEGREES
    // Half-open [0, 360): adding 360 to a tiny negative angle rounds to exactly 360.0 in double
    // precision, so fold that case explicitly rather than returning an out-of-range angle.
    val normalized = chi.mod(360.0)
    return if (normalized >= 360.0) 0.0 else normalized
}
