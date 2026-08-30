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
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Golden tests for [nextRiseSetTime] against USNO Astronomical Applications rise/set times
 * (aa.usno.navy.mil, apparent topocentric phenomena). Tolerances reflect the low-precision
 * Keplerian ephemeris the solver reads: a couple of minutes for the Sun, wider for the
 * fast-moving Moon (whose geocentric-vs-topocentric parallax alone is worth ~1°).
 */
class RiseSetTest {
    @Test
    fun `london summer sunrise and sunset match USNO`() {
        // USNO for 2026-07-08 at 51.51N 0.13W: sunrise 03:53 UT, sunset 20:17 UT.
        val after = utc(2026, 7, 8)
        val rise = nextRiseSetTime(SolarSystemBody.SUN, after, LONDON, RiseSetIndicator.RISE)!!
        val set = nextRiseSetTime(SolarSystemBody.SUN, after, LONDON, RiseSetIndicator.SET)!!
        assertCloseTo(rise, utc(2026, 7, 8, 3, 53), SUN_TOLERANCE)
        assertCloseTo(set, utc(2026, 7, 8, 20, 17), SUN_TOLERANCE)
    }

    @Test
    fun `new york winter sunrise and sunset match USNO`() {
        // USNO for 2026-01-15 at 40.71N 74.01W: sunrise 12:18 UT, sunset 21:53 UT.
        val after = utc(2026, 1, 15)
        val rise = nextRiseSetTime(SolarSystemBody.SUN, after, NEW_YORK, RiseSetIndicator.RISE)!!
        val set = nextRiseSetTime(SolarSystemBody.SUN, after, NEW_YORK, RiseSetIndicator.SET)!!
        assertCloseTo(rise, utc(2026, 1, 15, 12, 18), SUN_TOLERANCE)
        assertCloseTo(set, utc(2026, 1, 15, 21, 53), SUN_TOLERANCE)
    }

    @Test
    fun `london moonrise and moonset match USNO`() {
        // USNO for 2026-07-08 at 51.51N 0.13W: moonrise 23:19 UT, moonset 13:37 UT.
        val after = utc(2026, 7, 8)
        val rise = nextRiseSetTime(SolarSystemBody.MOON, after, LONDON, RiseSetIndicator.RISE)!!
        val set = nextRiseSetTime(SolarSystemBody.MOON, after, LONDON, RiseSetIndicator.SET)!!
        assertCloseTo(rise, utc(2026, 7, 8, 23, 19), MOON_TOLERANCE)
        assertCloseTo(set, utc(2026, 7, 8, 13, 37), MOON_TOLERANCE)
    }

    @Test
    fun `an event already past today lands on tomorrow, after the given instant`() {
        val after = utc(2026, 7, 8, 12, 0)
        // London's 03:53 sunrise is long past by noon; the next one is tomorrow's.
        val rise = nextRiseSetTime(SolarSystemBody.SUN, after, LONDON, RiseSetIndicator.RISE)!!
        assertThat(rise).isGreaterThan(after)
        assertCloseTo(rise, utc(2026, 7, 9, 3, 54), SUN_TOLERANCE)
    }

    @Test
    fun `a moon event already past today re-solves on tomorrow rather than adding a day`() {
        // USNO for 2026-07-09 at 51.51N 0.13W: moonset 14:51 UT — ~74 minutes later than the
        // 8th's 13:37, so reusing today's hour (v1's rule) would miss by over an hour.
        val after = utc(2026, 7, 8, 14, 0)
        val set = nextRiseSetTime(SolarSystemBody.MOON, after, LONDON, RiseSetIndicator.SET)!!
        assertThat(set).isGreaterThan(after)
        assertCloseTo(set, utc(2026, 7, 9, 14, 51), MOON_TOLERANCE)
    }

    @Test
    fun `the moon's skipped days defer to the following day instead of returning null`() {
        // The Moon's ~24h50m day means one UT day a month has no moonrise and one no moonset;
        // the next event is on the following day, never null at a mid-latitude site. Sweeping
        // a full lunar month guarantees the skipped days are among the queries.
        for (day in 1..31) {
            val after = utc(2026, 7, day)
            for (indicator in RiseSetIndicator.entries) {
                val next = nextRiseSetTime(SolarSystemBody.MOON, after, LONDON, indicator)
                assertThat(next).isNotNull()
                assertThat(next!!).isGreaterThan(after)
            }
        }
    }

    @Test
    fun `polar day and polar night return null`() {
        // Tromsø under the midnight sun: the sun neither sets nor rises around the June
        // solstice — v1's "sun will not set" toast case.
        val midsummer = utc(2026, 6, 20)
        assertThat(nextRiseSetTime(SolarSystemBody.SUN, midsummer, TROMSO, RiseSetIndicator.SET))
            .isNull()
        assertThat(nextRiseSetTime(SolarSystemBody.SUN, midsummer, TROMSO, RiseSetIndicator.RISE))
            .isNull()
        // And the polar night at the December solstice.
        val midwinter = utc(2026, 12, 21)
        assertThat(nextRiseSetTime(SolarSystemBody.SUN, midwinter, TROMSO, RiseSetIndicator.RISE))
            .isNull()
    }

    @Test
    fun `the sun stands at the rise-set horizon altitude at the solved times`() {
        // Self-consistency: whatever the ephemeris thinks, the returned instant should put the
        // sun at the -0.83° rise/set altitude to within the solver's convergence tolerance.
        val after = utc(2026, 10, 1)
        for (indicator in RiseSetIndicator.entries) {
            val time = nextRiseSetTime(SolarSystemBody.SUN, after, LONDON, indicator)!!
            assertThat(sunAltitudeDeg(time, LONDON)).isWithin(0.25).of(-0.83)
        }
    }

    @Test
    fun `a fixed star stands on the geometric horizon at the solved times`() {
        // The generalized solver (D51) over a fixed RA/Dec: Sirius from London crosses the
        // 0° horizon at whatever instants come back, both within the next day.
        val after = utc(2026, 7, 8, 12, 0)
        for (indicator in RiseSetIndicator.entries) {
            val time = nextRiseSetTime({ SIRIUS }, after, LONDON, indicator)!!
            assertThat(time).isGreaterThan(after)
            assertThat(time).isAtMost(after + 1.days)
            assertThat(altitudeDeg(SIRIUS, time, LONDON)).isWithin(0.25).of(0.0)
        }
    }

    @Test
    fun `a circumpolar star and a never-risen star return null`() {
        val after = utc(2026, 7, 8)
        // Polaris (dec +89.3°) never sets from London; Acrux (dec -63.1°) never rises.
        for (indicator in RiseSetIndicator.entries) {
            assertThat(nextRiseSetTime({ POLARIS }, after, LONDON, indicator)).isNull()
            assertThat(nextRiseSetTime({ ACRUX }, after, LONDON, indicator)).isNull()
        }
    }

    @Test
    fun `altitudeDeg puts Polaris at the observer's latitude`() {
        // The pole star stands (to within its 0.7° offset from the pole) at the observer's
        // latitude from anywhere in the northern hemisphere, at any time of day.
        for (hour in listOf(0, 6, 12, 18)) {
            val altitude = altitudeDeg(POLARIS, utc(2026, 7, 8, hour, 0), LONDON)
            assertThat(altitude).isWithin(1.0).of(LONDON.latitudeDeg)
        }
    }

    private fun sunAltitudeDeg(
        time: Instant,
        location: LatLong,
    ): Double {
        val (raDeg, decDeg) = KeplerianEphemeris.geocentricPosition(SolarSystemBody.SUN, time)
        val haRad =
            (meanSiderealTimeDeg(time, location.longitudeDeg) - raDeg) * DEGREES_TO_RADIANS
        val latRad = location.latitudeDeg * DEGREES_TO_RADIANS
        val decRad = decDeg * DEGREES_TO_RADIANS
        return asin(
            sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad),
        ) * RADIANS_TO_DEGREES
    }

    private fun assertCloseTo(
        actual: Instant,
        expected: Instant,
        tolerance: kotlin.time.Duration,
    ) {
        val error = actual - expected
        assertThat(error.absoluteValue).isAtMost(tolerance)
    }

    companion object {
        private val LONDON = LatLong(51.51, -0.13)
        private val NEW_YORK = LatLong(40.71, -74.01)
        private val TROMSO = LatLong(69.65, 18.96)

        private val SIRIUS = RaDec(101.287, -16.716)
        private val POLARIS = RaDec(37.955, 89.264)
        private val ACRUX = RaDec(186.650, -63.099)

        private val SUN_TOLERANCE = 5.minutes

        // The topocentric correction (D54) removed the tens-of-minutes parallax error v1
        // carried (the pre-D54 tolerance was 45 minutes); what remains is the low-precision
        // lunar theory's ~0.3°, worth up to ~13 minutes on these goldens.
        private val MOON_TOLERANCE = 15.minutes
    }
}
