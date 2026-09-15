/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

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
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.angularSeparationDeg
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * Accuracy tests for [MeeusEphemeris]. Unlike the [KeplerianEphemeris] golden tests, whose loose
 * tolerances pin the faithful-port baseline, these assert the far tighter accuracy the Meeus/ELP
 * series deliver — and, end to end, that a solar eclipse peaks at the right time.
 */
class MeeusEphemerisTest {
    // The Sun (Meeus ch. 25) is good to ~0.01°; allow for the references' own rounding.
    private val sunTol = 0.05

    // The Moon (Meeus ch. 47) is good to ~10″; the reference positions are quoted to ~0.001h.
    private val moonTol = 0.10

    /**
     * The published references below are *apparent* positions, referred to the equinox of date;
     * [MeeusEphemeris] returns J2000, the frame the app draws in. So the comparison precesses the
     * result forward to the reference's own frame — which also exercises [Precession] in the
     * opposite direction from the ephemeris itself.
     */
    private fun assertGeocentric(
        body: SolarSystemBody,
        time: Instant,
        raHours: Double,
        decDeg: Double,
        tol: Double,
    ) {
        val pos =
            RaDec.fromGeocentricVector(
                MeeusEphemeris
                    .geocentricPosition(body, time)
                    .toGeocentricVector()
                    .precessedFromJ2000(time),
            )
        assertThat(pos.raDeg).isWithin(tol).of(raHours * HOURS_TO_DEGREES)
        assertThat(pos.decDeg).isWithin(tol).of(decDeg)
    }

    @Test
    fun sunPositionsAreArcminuteAccurate() {
        // USNO apparent geocentric positions (same references as the baseline golden tests, but
        // held to a ~0.05° tolerance the baseline's ~0.4° Sun could never meet).
        assertGeocentric(SUN, utc(2009, 1, 1, 12), 18.813, -22.97, sunTol)
        assertGeocentric(SUN, utc(2009, 9, 20, 12), 11.857, 0.933, sunTol)
        assertGeocentric(SUN, utc(2010, 12, 25, 12), 18.260, -23.38, sunTol)
    }

    @Test
    fun moonGeocentricPositionsAreFarTighterThanTheBaseline() {
        // Astronomical Almanac geocentric apparent positions (v1 MoonTest), held to 0.10° where
        // the baseline needs 1.6°. NB the theskylive values used by the baseline golden test carry
        // ~1° of topocentric parallax, so they are unsuitable for a tight *geocentric* check.
        // 2009-01-01 12:00 UT: 22h27m20.423s, −7°09′49.94″
        assertGeocentric(MOON, utc(2009, 1, 1, 12), 22.45567, -7.16387, moonTol)
        // 2009-09-20 12:00 UT: 13h07m23.974s, −12°36′06.15″
        assertGeocentric(MOON, utc(2009, 9, 20, 12), 13.12333, -12.60171, moonTol)
        // 2010-12-25 12:00 UT: 9h54m53.914s, +8°03′22.00″
        assertGeocentric(MOON, utc(2010, 12, 25, 12), 9.91497, 8.05611, moonTol)
    }

    /**
     * The lunar parallax correction as the topocentric−geocentric *differential* against JPL
     * Horizons, isolating the geometry from any absolute-position error (mirrors the baseline's
     * parallax test, now on the sharper series).
     */
    @Test
    fun topocentricLunarParallaxMatchesJplHorizonsDifferentials() {
        assertParallaxShift(utc(2020, 10, 12), LatLong(51.48, 0.0), 0.57927, -0.80086)
        assertParallaxShift(utc(2020, 10, 12), LatLong(-33.87, 151.21), -0.50493, 0.73933)
        assertParallaxShift(utc(2009, 2, 11), LatLong(-0.2, -78.5), 0.97883, -0.01000)
    }

    private fun assertParallaxShift(
        time: Instant,
        observer: LatLong,
        expectedRaShiftDeg: Double,
        expectedDecShiftDeg: Double,
    ) {
        val geo = MeeusEphemeris.geocentricPosition(MOON, time)
        val topo = MeeusEphemeris.topocentricPosition(MOON, time, observer)
        assertThat(topo.raDeg - geo.raDeg).isWithin(0.05).of(expectedRaShiftDeg)
        assertThat(topo.decDeg - geo.decDeg).isWithin(0.05).of(expectedDecShiftDeg)
    }

    /**
     * The frame guard, and the reason [Precession] exists. [KeplerianEphemeris] derives the Sun
     * from J2000 orbital elements by a completely independent path that never touches precession,
     * so if the two agree across the whole valid range, [MeeusEphemeris] really is returning J2000
     * — the frame the catalog, the grid and the planets are all drawn in.
     *
     * Without the rotation the two diverge *linearly with epoch* at the 50.3″/year precession rate:
     * 0.004° at J2000 but 0.365° by 2026 and 0.555° by 2040, which is what this pins down. Note the
     * tolerance is far tighter than the 0.365° error it guards against, and that the check is
     * strongest at the range's ends.
     */
    @Test
    fun sunIsReturnedInTheSameJ2000FrameAsTheBaselineAcrossTheValidRange() {
        for (year in listOf(1800, 1900, 2000, 2026, 2050)) {
            val time = utc(year, 3, 21, 12)
            val separation =
                angularSeparationDeg(
                    MeeusEphemeris.geocentricPosition(SUN, time),
                    KeplerianEphemeris.geocentricPosition(SUN, time),
                )
            assertThat(separation).isLessThan(0.05)
        }
    }

    @Test
    fun planetPositionsAreUnchangedFromTheBaseline() {
        val t = utc(2009, 1, 1, 12)
        val observer = LatLong(51.48, 0.0)
        for (body in listOf(MERCURY, VENUS, MARS, JUPITER, SATURN, URANUS, NEPTUNE, PLUTO)) {
            assertThat(MeeusEphemeris.geocentricPosition(body, t))
                .isEqualTo(KeplerianEphemeris.geocentricPosition(body, t))
            assertThat(MeeusEphemeris.topocentricPosition(body, t, observer))
                .isEqualTo(KeplerianEphemeris.topocentricPosition(body, t, observer))
            assertThat(MeeusEphemeris.magnitude(body, t))
                .isEqualTo(KeplerianEphemeris.magnitude(body, t))
        }
    }

    /**
     * End-to-end guard: maximum of the 12 Aug 2026 partial eclipse seen from Grassington, North
     * Yorkshire. Maximum eclipse is when the topocentric Sun–Moon separation is least; its timing
     * is acutely sensitive to position error (~13 min per 0.1°), so the baseline's low-precision
     * Sun and Moon put it ~30 min early. The published local maximum is 18:07 UT (19:07 BST).
     */
    @Test
    fun aug2026PartialEclipseMaximumMatchesPublishedLocalCircumstances() {
        val grassington = LatLong(54.070, -1.990) // sea level
        val startUt = utc(2026, 8, 12, 17, 0, 0)

        var minSeparation = Double.MAX_VALUE
        var maximumEclipseSecondsUt = -1
        for (second in 0..(3 * 3600)) {
            val time = startUt.plusSeconds(second)
            val separation =
                angularSeparationDeg(
                    MeeusEphemeris.topocentricPosition(SUN, time, grassington),
                    MeeusEphemeris.topocentricPosition(MOON, time, grassington),
                )
            if (separation < minSeparation) {
                minSeparation = separation
                maximumEclipseSecondsUt = second
            }
        }

        val expectedSecondsUt = 67 * 60 // 18:07 UT, relative to the 17:00 UT scan start.
        assertThat(
            maximumEclipseSecondsUt.toDouble(),
        ).isWithin(3.0 * 60).of(expectedSecondsUt.toDouble())
        // A deep partial from here, not a central eclipse: the discs never fully coincide.
        assertThat(minSeparation).isGreaterThan(0.02)
        assertThat(minSeparation).isLessThan(0.10)
    }

    private fun Instant.plusSeconds(seconds: Int): Instant =
        Instant.fromEpochMilliseconds(toEpochMilliseconds() + seconds * 1000L)
}
