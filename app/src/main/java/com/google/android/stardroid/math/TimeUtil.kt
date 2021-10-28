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

import java.util.*

/**
 * Utilities for working with Dates and times.
 *
 * @author Kevin Serafini
 * @author Brent Bryan
 */
object TimeUtil {
    /**
     * Calculate the number of Julian Centuries from the epoch 2000.0
     * (equivalent to Julian Day 2451545.0).
     */
    @JvmStatic
    fun julianCenturies(date: Date?): Double {
        val jd = calculateJulianDay(date)
        val delta = jd - 2451545.0
        return delta / 36525.0
    }

    /**
     * Calculate the Julian Day for a given date using the following formula:
     * JD = 367 * Y - INT(7 * (Y + INT((M + 9)/12))/4) + INT(275 * M / 9)
     * + D + 1721013.5 + UT/24
     *
     * Note that this is only valid for the year range 1900 - 2099.
     */
    @JvmStatic
    fun calculateJulianDay(date: Date?): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.time = date
        val hour = (cal[Calendar.HOUR_OF_DAY]
                + cal[Calendar.MINUTE] / 60.0f + cal[Calendar.SECOND] / 3600.0f).toDouble()
        val year = cal[Calendar.YEAR]
        val month = cal[Calendar.MONTH] + 1
        val day = cal[Calendar.DAY_OF_MONTH]
        return (367.0 * year - Math.floor(
            7.0 * (year
                    + Math.floor((month + 9.0) / 12.0)) / 4.0
        ) + Math.floor(275.0 * month / 9.0) + day
                + 1721013.5 + hour / 24.0)
    }

    /**
     * Convert the given Julian Day to Gregorian Date (in UT time zone).
     * Based on the formula given in the Explanitory Supplement to the
     * Astronomical Almanac, pg 604.
     */
    fun calculateGregorianDate(jd: Double): Date {
        var l = jd.toInt() + 68569
        val n = 4 * l / 146097
        l = l - (146097 * n + 3) / 4
        val i = 4000 * (l + 1) / 1461001
        l = l - 1461 * i / 4 + 31
        val j = 80 * l / 2447
        val d = l - 2447 * j / 80
        l = j / 11
        val m = j + 2 - 12 * l
        val y = 100 * (n - 49) + i + l
        val fraction = jd - Math.floor(jd)
        val dHours = fraction * 24.0
        val hours = dHours.toInt()
        val dMinutes = (dHours - hours) * 60.0
        val minutes = dMinutes.toInt()
        val seconds = ((dMinutes - minutes) * 60.0) as Int
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UT"))
        cal[y, m - 1, d, hours + 12, minutes] = seconds
        return cal.time
    }

    /**
     * Calculate local mean sidereal time in degrees. Note that longitude is
     * negative for western longitude values.
     */
    @JvmStatic
    fun meanSiderealTime(date: Date?, longitude: Float): Float {
        // First, calculate number of Julian days since J2000.0.
        val jd = calculateJulianDay(date)
        val delta = jd - 2451545.0f

        // Calculate the global and local sidereal times
        val gst = 280.461f + 360.98564737f * delta
        val lst = normalizeAngle(gst + longitude)
        return lst.toFloat()
    }

    /**
     * Normalize the angle to the range 0 <= value < 360.
     */
    fun normalizeAngle(angle: Double): Double {
        var remainder = angle % 360
        if (remainder < 0) remainder += 360.0
        return remainder
    }

    /**
     * Normalize the time to the range 0 <= value < 24.
     */
    @JvmStatic
    fun normalizeHours(time: Double): Double {
        var remainder = time % 24
        if (remainder < 0) remainder += 24.0
        return remainder
    }

    /**
     * Take a universal time between 0 and 24 and return a triple
     * [hours, minutes, seconds].
     *
     * @param ut Universal time - presumed to be between 0 and 24.
     * @return [hours, minutes, seconds]
     */
    fun clockTimeFromHrs(ut: Double): IntArray {
        val hms = IntArray(3)
        hms[0] = Math.floor(ut).toInt()
        val remainderMins = 60 * (ut - hms[0])
        hms[1] = Math.floor(remainderMins).toInt()
        hms[2] = Math.floor(remainderMins - hms[1]).toInt()
        return hms
    }
}