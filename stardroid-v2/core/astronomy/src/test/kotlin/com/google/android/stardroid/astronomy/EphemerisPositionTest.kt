/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.astronomy.SolarSystemBody.EARTH
import com.google.android.stardroid.astronomy.SolarSystemBody.JUPITER
import com.google.android.stardroid.astronomy.SolarSystemBody.MARS
import com.google.android.stardroid.astronomy.SolarSystemBody.MERCURY
import com.google.android.stardroid.astronomy.SolarSystemBody.MOON
import com.google.android.stardroid.astronomy.SolarSystemBody.NEPTUNE
import com.google.android.stardroid.astronomy.SolarSystemBody.PLUTO
import com.google.android.stardroid.astronomy.SolarSystemBody.SATURN
import com.google.android.stardroid.astronomy.SolarSystemBody.SUN
import com.google.android.stardroid.astronomy.SolarSystemBody.URANUS
import com.google.android.stardroid.astronomy.SolarSystemBody.VENUS
import com.google.android.stardroid.math.HOURS_TO_DEGREES
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.angularSeparationDeg
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Golden position tests ported from v1 `UniverseSmokeTest` and `MoonTest`. Reference RA/Dec come
 * from the US Naval Observatory (planets/Sun) and theskylive (Moon). Tolerances match v1's: the
 * low-precision model is only good to ~0.3° for planets and ~1.6° for the geocentric Moon.
 */
class EphemerisPositionTest {
    private val planetTol = 0.30 // degrees
    private val lunarTol = 1.6 // degrees; v1 ignores observer position on Earth

    private fun assertPosition(
        body: SolarSystemBody,
        time: Instant,
        raHours: Double,
        decDeg: Double,
        tol: Double,
    ) {
        val pos = KeplerianEphemeris.geocentricPosition(body, time)
        assertThat(pos.raDeg).isWithin(tol).of(raHours * HOURS_TO_DEGREES)
        assertThat(pos.decDeg).isWithin(tol).of(decDeg)
    }

    @Test
    fun planetPositions_2009Jan01() {
        val t = utc(2009, 1, 1, 12)
        assertPosition(SUN, t, 18.813, -22.97, planetTol)
        assertPosition(MERCURY, t, 20.177, -21.60, planetTol)
        assertPosition(VENUS, t, 22.033, -13.60, planetTol)
        assertPosition(MARS, t, 18.285, -24.08, planetTol)
        assertPosition(JUPITER, t, 20.085, -20.75, planetTol)
        assertPosition(SATURN, t, 11.550, 5.15, planetTol)
        assertPosition(URANUS, t, 23.362, -4.95, planetTol)
        assertPosition(NEPTUNE, t, 21.662, -14.37, planetTol)
        assertPosition(PLUTO, t, 18.088, -17.75, planetTol)
    }

    @Test
    fun planetPositions_2009Sep20() {
        val t = utc(2009, 9, 20, 12)
        assertPosition(SUN, t, 11.857, 0.933, planetTol)
        assertPosition(MERCURY, t, 11.768, -1.75, planetTol)
        assertPosition(VENUS, t, 10.157, 12.35, planetTol)
        assertPosition(MARS, t, 7.143, 23.05, planetTol)
        assertPosition(JUPITER, t, 21.387, -16.48, planetTol)
        assertPosition(SATURN, t, 11.767, 3.67, planetTol)
        assertPosition(URANUS, t, 23.685, -2.92, planetTol)
        assertPosition(NEPTUNE, t, 21.778, -13.85, planetTol)
        assertPosition(PLUTO, t, 18.047, -18.00, planetTol)
    }

    @Test
    fun planetPositions_2010Dec25() {
        val t = utc(2010, 12, 25, 12)
        assertPosition(SUN, t, 18.260, -23.38, planetTol)
        assertPosition(MERCURY, t, 17.403, -20.17, planetTol)
        assertPosition(VENUS, t, 15.068, -13.83, planetTol)
        assertPosition(MARS, t, 18.975, -23.72, planetTol)
        assertPosition(JUPITER, t, 23.773, -2.88, planetTol)
        assertPosition(SATURN, t, 13.065, -4.23, planetTol)
        assertPosition(URANUS, t, 23.827, -1.93, planetTol)
        assertPosition(NEPTUNE, t, 21.930, -13.12, planetTol)
    }

    @Test
    fun earthIsNotAViewableTarget() {
        // Earth is the geocentric origin; querying its position/phase/magnitude is a caller error
        // (would otherwise yield a degenerate RaDec(0,0) or NaN).
        val t = utc(2010, 12, 25, 12)
        assertThrows<IllegalArgumentException> { KeplerianEphemeris.geocentricPosition(EARTH, t) }
        assertThrows<IllegalArgumentException> { KeplerianEphemeris.phaseAngleDeg(EARTH, t) }
        assertThrows<IllegalArgumentException> { KeplerianEphemeris.magnitude(EARTH, t) }
    }

    @Test
    fun lunarPositions() {
        assertPosition(MOON, utc(2009, 1, 1, 12), 22.456, -7.164, lunarTol)
        assertPosition(MOON, utc(2009, 9, 20, 12), 13.123, -12.602, lunarTol)
        assertPosition(MOON, utc(2010, 12, 25, 12), 9.915, 8.056, lunarTol)
        // From theskylive ephemeris.
        assertPosition(MOON, utc(2020, 10, 12), 9.0708, 20.0433, lunarTol)
        assertPosition(MOON, utc(2009, 2, 11), 10.7464, 4.408, lunarTol)
        assertPosition(MOON, utc(2005, 4, 11), 2.8694, 18.0444, lunarTol)
    }

    /**
     * The lunar parallax correction (D54), pinned as the topocentric−geocentric *differential*
     * against JPL Horizons apparent positions: the differential isolates the correction from
     * the low-precision series' ~1° baseline error, so it gets a far tighter tolerance than
     * the absolute positions.
     */
    @Test
    fun topocentricLunarParallax_matchesJplHorizonsDifferentials() {
        // Horizons geocentric vs topocentric (airless apparent, ANG_FORMAT=DEG), sites at 0 m:
        // 2020-10-12 00:00 UT, Greenwich (51.48N, 0E):  ΔRA +0.57927, ΔDec −0.80086
        // 2020-10-12 00:00 UT, Sydney (33.87S, 151.21E): ΔRA −0.50493, ΔDec +0.73933
        // 2009-02-11 00:00 UT, Quito (0.2S, 78.5W):      ΔRA +0.97883, ΔDec −0.01000
        assertParallaxShift(utc(2020, 10, 12), LatLong(51.48, 0.0), 0.57927, -0.80086)
        assertParallaxShift(utc(2020, 10, 12), LatLong(-33.87, 151.21), -0.50493, 0.73933)
        assertParallaxShift(utc(2009, 2, 11), LatLong(-0.2, -78.5), 0.97883, -0.01000)
    }

    @Test
    fun topocentricPosition_neverExceedsTheMaximumLunarParallax() {
        // The horizontal parallax series peaks at ~1.03° (perigee); the topocentric shift is
        // π·sin(zenith distance), so no observer anywhere can see a larger offset.
        for (day in listOf(1, 8, 15, 22)) {
            val t = utc(2020, 10, day)
            val geo = KeplerianEphemeris.geocentricPosition(MOON, t)
            for (lat in -80..80 step 40) {
                for (lon in -180..180 step 60) {
                    val topo =
                        KeplerianEphemeris.topocentricPosition(
                            MOON,
                            t,
                            LatLong(lat.toDouble(), lon.toDouble()),
                        )
                    assertThat(angularSeparationDeg(geo, topo)).isLessThan(1.03)
                }
            }
        }
    }

    @Test
    fun topocentricPosition_isGeocentricForEverythingButTheMoon() {
        val t = utc(2009, 1, 1, 12)
        val observer = LatLong(51.48, 0.0)
        for (body in listOf(SUN, MERCURY, VENUS, MARS, JUPITER, SATURN, URANUS, NEPTUNE, PLUTO)) {
            assertThat(KeplerianEphemeris.topocentricPosition(body, t, observer))
                .isEqualTo(KeplerianEphemeris.geocentricPosition(body, t))
        }
    }

    private fun assertParallaxShift(
        time: Instant,
        observer: LatLong,
        expectedRaShiftDeg: Double,
        expectedDecShiftDeg: Double,
    ) {
        val geo = KeplerianEphemeris.geocentricPosition(MOON, time)
        val topo = KeplerianEphemeris.topocentricPosition(MOON, time, observer)
        assertThat(topo.raDeg - geo.raDeg).isWithin(parallaxTol).of(expectedRaShiftDeg)
        assertThat(topo.decDeg - geo.decDeg).isWithin(parallaxTol).of(expectedDecShiftDeg)
    }

    /**
     * The correction itself is geometry — its error is dominated by the series' small direction
     * and parallax-term truncations, a couple of arcminutes at most.
     */
    private val parallaxTol = 0.05 // degrees
}
