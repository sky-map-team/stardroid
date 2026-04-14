// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.math

import junit.framework.TestCase
import com.google.common.truth.Truth.assertThat
import java.util.*

/**
 * Tests based on data from
 * http://www.jgiesen.de/astro/astroJS/siderealClock/
 * http://aa.usno.navy.mil/data/docs/JulianDate.php
 * http://www.csgnetwork.com/siderealjuliantimecalc.html
 */
class SiderealTimeRegressionTest : TestCase() {
    fun testZeroTime() {
        // Local sidereal time should be zero here.
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar[2009, 2, 20, 12, 7] = 24
        val time = calendar.time
        assertThat(julianDay(time)).isWithin(0.0007).of(2454911.00514) // accurate to 1 min
        assertThat(meanSiderealTime(time, 0f) % 360).isWithin(ANGULAR_TOL).of(0f)
    }

    fun testZeroTimeAt90Longitude() {
        // Local sidereal time should be 90 here.
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar[2009, 2, 20, 12, 7] = 24
        val time = calendar.time
        assertThat(meanSiderealTime(time, 90f) % 360).isWithin(ANGULAR_TOL).of(90f)
    }

    fun testABitMoreInteresting() {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar[2000, 0, 1, 0, 0] = 0
        val time = calendar.time
        assertThat(julianDay(time)).isWithin(0.0007).of(2451544.5) // accurate to 1 min
        // Sidereal time should be 6:39:51
        val expectedTime = (6f + 39f / 60 + 51f / 60 / 60) / 24 * 360
        assertThat(meanSiderealTime(time, 0f) % 360).isWithin(ANGULAR_TOL).of(expectedTime)
    }

    companion object {
        private const val ANGULAR_TOL = 1e-1f
    }
}