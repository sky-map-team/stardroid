/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.space

import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*

/**
 * Guards the end-to-end timing of a solar eclipse - the combined accuracy of the Sun's apparent
 * position ([Sun.getRaDec]) and the Moon's topocentric position ([Moon.getTopocentricRaDec]).
 *
 * Maximum eclipse is when the topocentric Sun-Moon separation is least, and its timing is very
 * sensitive to position errors: at the ~0.5 degrees/hour rate the Moon closes on the Sun, every
 * 0.1 degrees of error in either body moves the moment by ~13 minutes. Earlier, low-precision Sun
 * and Moon models put this eclipse's maximum ~30 minutes early; the Meeus/ELP series bring it back
 * in line with published local circumstances.
 */
class SolarEclipseTimingTest {
    private fun separationDegrees(a: RaDec, b: RaDec): Double {
        val cosSimilarity =
            getGeocentricCoords(a).cosineSimilarity(getGeocentricCoords(b)).coerceIn(-1f, 1f)
        return (MathUtils.acos(cosSimilarity) * RADIANS_TO_DEGREES).toDouble()
    }

    @Test
    fun maximumOfAug2026PartialEclipseMatchesPublishedLocalCircumstances() {
        val sun = Sun()
        val moon = Moon()
        // Grassington, North Yorkshire, at sea level.
        val grassington = LatLong(54.070f, -1.990f)

        var minSeparation = Double.MAX_VALUE
        var maximumEclipseSecondsUt = -1
        // Scan 17:00-20:00 UT on 2026-08-12 at one-second resolution.
        val start = GregorianCalendar(TimeZone.getTimeZone("GMT"))
        start.set(2026, GregorianCalendar.AUGUST, 12, 17, 0, 0)
        for (second in 0..(3 * 3600)) {
            val time = Date(start.time.time + second * 1000L)
            val separation =
                separationDegrees(sun.getRaDec(time), moon.getTopocentricRaDec(time, grassington))
            if (separation < minSeparation) {
                minSeparation = separation
                maximumEclipseSecondsUt = second
            }
        }

        // Published local maximum for Grassington is 18:07 UT (19:07 BST); allow a few minutes for
        // our ~arcminute Sun/Moon models, sea-level assumption and neglected refraction.
        val expectedSecondsUt = 67 * 60 // 18:07 UT relative to the 17:00 UT scan start.
        assertThat(maximumEclipseSecondsUt.toDouble()).isWithin(3.0 * 60).of(expectedSecondsUt.toDouble())
        // It is a deep partial from here, not a central eclipse: the disks never fully coincide.
        assertThat(minSeparation).isGreaterThan(0.02)
        assertThat(minSeparation).isLessThan(0.10)
    }
}
