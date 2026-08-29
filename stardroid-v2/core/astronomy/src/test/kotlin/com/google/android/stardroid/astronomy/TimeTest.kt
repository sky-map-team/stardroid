/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.HOURS_TO_DEGREES
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Ported from v1 `TimeUtilTest` and `SiderealTimeRegressionTest`. Reference Julian dates and
 * sidereal times come from the USNO; v2 computes them in `Double` via the exact JD identity.
 */
class TimeTest {
    @Test
    fun julianDayAtJ2000() {
        // 2000-01-01 12:00 UTC == JD 2451545.0; midnight before is half a day earlier.
        assertThat(utc(2000, 1, 1).julianDay()).isWithin(1e-6).of(2451544.5)
        assertThat(utc(2000, 1, 1, 12).julianDay()).isWithin(1e-6).of(JD_J2000)
    }

    @Test
    fun julianDayKnownValues() {
        assertThat(utc(2009, 1, 1, 12).julianDay()).isWithin(1e-6).of(2454833.0)
        assertThat(utc(2009, 7, 4, 12).julianDay()).isWithin(1e-6).of(2455017.0)
        assertThat(utc(2009, 9, 20, 12).julianDay()).isWithin(1e-6).of(2455095.0)
        assertThat(utc(2010, 12, 25, 12).julianDay()).isWithin(1e-6).of(2455556.0)
    }

    @Test
    fun julianCenturiesKnownValues() {
        assertThat(utc(2009, 1, 1, 12).julianCenturiesJ2000()).isWithin(1e-5).of(0.09002)
        assertThat(utc(2009, 7, 4, 12).julianCenturiesJ2000()).isWithin(1e-5).of(0.09506)
        assertThat(utc(2009, 9, 20, 12).julianCenturiesJ2000()).isWithin(1e-5).of(0.09719)
        assertThat(utc(2010, 12, 25, 12).julianCenturiesJ2000()).isWithin(1e-5).of(0.10982)
    }

    @Test
    fun meanSiderealTimeForSelectedCities() {
        val pittsburgh = -79.97
        val london = -0.13
        val tokyo = 139.77
        val tol = 0.15 // degrees

        var t = utc(2009, 1, 1, 12)
        assertThat(meanSiderealTimeDeg(t, pittsburgh)).isWithin(tol).of(13.42 * HOURS_TO_DEGREES)
        assertThat(meanSiderealTimeDeg(t, london)).isWithin(tol).of(18.74 * HOURS_TO_DEGREES)
        assertThat(meanSiderealTimeDeg(t, tokyo)).isWithin(tol).of(4.07 * HOURS_TO_DEGREES)

        t = utc(2009, 9, 20, 12)
        assertThat(meanSiderealTimeDeg(t, pittsburgh)).isWithin(tol).of(6.64 * HOURS_TO_DEGREES)
        assertThat(meanSiderealTimeDeg(t, london)).isWithin(tol).of(11.96 * HOURS_TO_DEGREES)
        assertThat(meanSiderealTimeDeg(t, tokyo)).isWithin(tol).of(21.29 * HOURS_TO_DEGREES)

        t = utc(2010, 12, 25, 12)
        assertThat(meanSiderealTimeDeg(t, pittsburgh)).isWithin(tol).of(12.92815 * HOURS_TO_DEGREES)
        assertThat(meanSiderealTimeDeg(t, london)).isWithin(tol).of(18.25 * HOURS_TO_DEGREES)
        assertThat(meanSiderealTimeDeg(t, tokyo)).isWithin(tol).of(3.58 * HOURS_TO_DEGREES)
    }

    @Test
    fun meanSiderealTimeZeroPoints() {
        // 2009-03-20 12:07:24 UTC: LST ~ 0 at Greenwich, ~90 at +90 longitude.
        val t = utc(2009, 3, 20, 12, 7, 24)
        assertThat(meanSiderealTimeDeg(t, 0.0) % 360).isWithin(0.1).of(0.0)
        assertThat(meanSiderealTimeDeg(t, 90.0) % 360).isWithin(0.1).of(90.0)
    }
}
