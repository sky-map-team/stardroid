/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.space

import com.google.android.stardroid.math.HOURS_TO_DEGREES
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*

// Accuracy of our position calculations, in degrees.
private const val POS_TOL = 0.2f

// The topocentric correction itself is geometry - its error is dominated by the underlying
// series' own small direction and parallax-term truncations, a couple of arcminutes at most.
private const val PARALLAX_TOL = 0.05f

// The horizontal parallax series peaks at ~1.03 degrees (perigee); the topocentric shift is
// pi * sin(zenith distance), so no observer anywhere can see a larger offset.
private const val MAX_LUNAR_PARALLAX = 1.03f

private fun utc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Date {
    val cal = GregorianCalendar()
    cal.timeZone = TimeZone.getTimeZone("GMT")
    cal.set(year, month, day, hour, minute, 0)
    return cal.time
}

class MoonTest {
    // Verify that we are calculating a valid lunar RA/Dec.
    @Test
    fun testLunarGeocentricLocation() {
        val moon = Moon()
        val testCal = GregorianCalendar()
        testCal.setTimeZone(TimeZone.getTimeZone("GMT"))

        // 2009 Jan  1, 12:00 UT1: RA = 22h 27m 20.423s, Dec = - 7d  9m 49.94s
        testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0)
        var lunarPos = moon.getRaDec(testCal.getTime())
        assertThat(lunarPos.ra).isWithin(POS_TOL).of(22.456f * HOURS_TO_DEGREES)
        assertThat(lunarPos.dec).isWithin(POS_TOL).of(-7.164f)

        // 2009 Sep 20, 12:00 UT1: RA = 13h  7m 23.974s, Dec = -12d 36m  6.15s
        testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0)
        lunarPos = moon.getRaDec(testCal.getTime())
        assertThat(lunarPos.ra).isWithin(POS_TOL).of(13.123f * HOURS_TO_DEGREES)
        assertThat(lunarPos.dec).isWithin(POS_TOL).of(-12.602f)

        // 2010 Dec 25, 12:00 UT1: RA =  9h 54m 53.914s, Dec = +8d 3m 22.00s
        testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0)
        lunarPos = moon.getRaDec(testCal.getTime())
        assertThat(lunarPos.ra).isWithin(POS_TOL).of(9.915f * HOURS_TO_DEGREES)
        assertThat(lunarPos.dec).isWithin(POS_TOL).of(8.056f)
    }

    // Pins the topocentric-geocentric differential against JPL Horizons apparent positions.
    // The differential isolates the parallax correction from the low-precision series' own
    // ~1 degree baseline error, so it can use a far tighter tolerance than the absolute
    // position tests above.
    @Test
    fun testTopocentricLunarParallaxMatchesJplHorizonsDifferentials() {
        val moon = Moon()

        // Horizons geocentric vs topocentric (airless apparent, ANG_FORMAT=DEG), sites at 0 m:
        // 2020-10-12 00:00 UT, Greenwich (51.48N, 0E):  dRA +0.57927, dDec -0.80086
        assertParallaxShift(moon, utc(2020, 9, 12), LatLong(51.48f, 0.0f), 0.57927f, -0.80086f)
        // 2020-10-12 00:00 UT, Sydney (33.87S, 151.21E): dRA -0.50493, dDec +0.73933
        assertParallaxShift(
            moon, utc(2020, 9, 12), LatLong(-33.87f, 151.21f), -0.50493f, 0.73933f
        )
        // 2009-02-11 00:00 UT, Quito (0.2S, 78.5W): dRA +0.97883, dDec -0.01000
        assertParallaxShift(moon, utc(2009, 1, 11), LatLong(-0.2f, -78.5f), 0.97883f, -0.01000f)
    }

    private fun assertParallaxShift(
        moon: Moon, time: Date, location: LatLong, expectedRaShiftDeg: Float,
        expectedDecShiftDeg: Float
    ) {
        val geo = moon.getRaDec(time)
        val topo = moon.getTopocentricRaDec(time, location)
        assertThat(topo.ra - geo.ra).isWithin(PARALLAX_TOL).of(expectedRaShiftDeg)
        assertThat(topo.dec - geo.dec).isWithin(PARALLAX_TOL).of(expectedDecShiftDeg)
    }

    @Test
    fun testTopocentricPositionNeverExceedsTheMaximumLunarParallax() {
        val moon = Moon()
        for (day in listOf(1, 8, 15, 22)) {
            val time = utc(2020, 9, day)
            val geo = moon.getRaDec(time)
            var lat = -80
            while (lat <= 80) {
                var lon = -180
                while (lon <= 180) {
                    val topo = moon.getTopocentricRaDec(time, LatLong(lat.toFloat(), lon.toFloat()))
                    assertThat(angularSeparation(geo, topo)).isLessThan(MAX_LUNAR_PARALLAX)
                    lon += 60
                }
                lat += 40
            }
        }
    }

    private fun angularSeparation(a: RaDec, b: RaDec): Float {
        val pointA = getGeocentricCoords(a)
        val pointB = getGeocentricCoords(b)
        return MathUtils.acos(pointA.cosineSimilarity(pointB)) * RADIANS_TO_DEGREES
    }

    @Test
    fun testTrueAngularRadius() {
        // Mean apparent angular diameter of the Moon as seen from Earth is about 0.518 degrees,
        // i.e. an angular radius of about 0.259 degrees - close to the Sun's, which is why total
        // solar eclipses happen at all. This is a small fraction of the fixed,
        // exaggerated-for-visibility 0.02 radians (about 1.15 degrees) getPlanetaryImageSize()
        // returns.
        val moon = Moon()
        val angularRadiusDeg = moon.getTrueAngularRadius(utc(2020, 9, 12)) * RADIANS_TO_DEGREES
        assertThat(angularRadiusDeg).isWithin(0.05f).of(0.259f)
    }
}