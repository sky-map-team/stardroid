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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*

class TimeUtilTest {
    @Test
    fun testJulianDate2000AD() {
        // To begin with, make sure we can get a GregorianCalender object
        // to agree with UNIX time for 2000 epoch.
        val unix2000: Long = 946684800 // Confirm using 'date -r 946684800'.
        val year2000 = GregorianCalendar()
        year2000.timeZone = TimeZone.getTimeZone("GMT")
        // Set to midnight GMT at beginning of 2000-01-01.
        year2000[2000, GregorianCalendar.JANUARY, 1, 0, 0] = 0
        // Even when you tell the Calendar to fix on a particular day down
        // to the number of seconds, it still adds the number of milliseconds
        // from your system clock unless you set it to zero separately.
        year2000[GregorianCalendar.MILLISECOND] = 0
        // Thanks to the beauty of Object OverOriented design, we run getTime().
        // on the Calendar to get a Date, and getTime() on the date to finally
        // get a long integer.
        assertThat(year2000.time.time / 1000).isEqualTo(unix2000)
        // January 1, 2000 at midday corresponds to JD = 2451545.0, according to
        // http://en.wikipedia.org/wiki/Julian_day#Gregorian_calendar_from_Julian_day_number.
        // So midnight before is half a day earlier.
        assertThat(julianDay(year2000.time)).isWithin(TOL)
            .of(2451544.5)
    }

    // Make sure that we correctly generate Julian dates. Standard values
    // were obtained from the USNO web site.
    // http://www.usno.navy.mil/USNO/astronomical-applications/data-services/jul-date
    @Test
    fun testJulianDate() {
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")

        // Jan 1, 2009, 12:00 UT1 
        testCal[2009, GregorianCalendar.JANUARY, 1, 12, 0] = 0
        assertThat(julianDay(testCal.time)).isWithin(TOL).of(2454833.0)

        // Jul 4, 2009, 12:00 UT1
        testCal[2009, GregorianCalendar.JULY, 4, 12, 0] = 0
        assertThat(julianDay(testCal.time)).isWithin(TOL).of(2455017.0)

        // Sep 20, 2009, 12:00 UT1
        testCal[2009, GregorianCalendar.SEPTEMBER, 20, 12, 0] = 0
        assertThat(julianDay(testCal.time)).isWithin(TOL).of(2455095.0)

        // Dec 25, 2010, 12:00 UT1
        testCal[2010, GregorianCalendar.DECEMBER, 25, 12, 0] = 0
        assertThat(julianDay(testCal.time)).isWithin(TOL).of(2455556.0)
    }

    // Make sure that we correctly generate Julian dates. Standard values
    // were obtained from the USNO web site.
    // http://www.usno.navy.mil/USNO/astronomical-applications/data-services/jul-date
    @Test
    fun testJulianCenturies() {
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")

        // Jan 1, 2009, 12:00 UT1 
        testCal[2009, GregorianCalendar.JANUARY, 1, 12, 0] = 0
        assertThat(julianCenturies(testCal.time)).isWithin(TOL).of(0.09002)

        // Jul 4, 2009, 12:00 UT1
        testCal[2009, GregorianCalendar.JULY, 4, 12, 0] = 0
        assertThat(julianCenturies(testCal.time)).isWithin(TOL).of(0.09506)

        // Sep 20, 2009, 12:00 UT1
        testCal[2009, GregorianCalendar.SEPTEMBER, 20, 12, 0] = 0
        assertThat(julianCenturies(testCal.time)).isWithin(TOL).of(0.09719)

        // Dec 25, 2010, 12:00 UT1
        testCal[2010, GregorianCalendar.DECEMBER, 25, 12, 0] = 0
        assertThat(julianCenturies(testCal.time)).isWithin(TOL).of(0.10982)
    }

    // Verify that we are calculating the correct local mean sidereal time.
    @Test
    fun testMeanSiderealTime() {
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")

        // Longitude of selected cities
        val pit = -79.97f // W 79 58'12.0"
        val lon = -0.13f // W 00 07'41.0"
        val tok = 139.77f // E139 46'00.0"

        // A couple of select dates:
        // Jan  1, 2009, 12:00 UT1
        testCal[2009, GregorianCalendar.JANUARY, 1, 12, 0] = 0
        assertThat(meanSiderealTime(testCal.time, pit)).isWithin(LMST_TOL)
            .of(13.42f * HOURS_TO_DEGREES)
        assertThat(meanSiderealTime(testCal.time, lon)).isWithin(LMST_TOL)
            .of(18.74f * HOURS_TO_DEGREES)
        assertThat(meanSiderealTime(testCal.time, tok)).isWithin(LMST_TOL)
            .of(4.07f * HOURS_TO_DEGREES)

        // Sep 20, 2009, 12:00 UT1
        testCal[2009, GregorianCalendar.SEPTEMBER, 20, 12, 0] = 0
        assertThat(meanSiderealTime(testCal.time, pit)).isWithin(LMST_TOL)
            .of(6.64f * HOURS_TO_DEGREES)
        assertThat(meanSiderealTime(testCal.time, lon)).isWithin(LMST_TOL)
            .of(11.96f * HOURS_TO_DEGREES)
        assertThat(meanSiderealTime(testCal.time, tok)).isWithin(LMST_TOL)
            .of(21.29f * HOURS_TO_DEGREES)

        // Dec 25, 2010, 12:00 UT1
        testCal[2010, GregorianCalendar.DECEMBER, 25, 12, 0] = 0
        assertThat(meanSiderealTime(testCal.time, pit)).isWithin(LMST_TOL)
            .of(12.92815f * HOURS_TO_DEGREES)
        assertThat(meanSiderealTime(testCal.time, lon)).isWithin(LMST_TOL)
            .of(18.25f * HOURS_TO_DEGREES)
        assertThat(meanSiderealTime(testCal.time, tok)).isWithin(LMST_TOL)
            .of(3.58f * HOURS_TO_DEGREES)
    }

    companion object {
        private const val TOL = 0.0001 // Tolerance for Julian Date calculations
        private const val LMST_TOL = 0.15f // Tolerance for LMST calculation
        private const val HOURS_TO_DEGREES = 360.0f / 24.0f // Convert from hours to degrees
    }
}