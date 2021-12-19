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

import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt

data class RaDec(
    var ra: Float, // In degrees
    var dec: Float // In degrees
) {

    /**
     * Return true if the given Ra/Dec is always above the horizon. Return
     * false otherwise.
     * In the northern hemisphere, objects never set if dec > 90 - lat.
     * In the southern hemisphere, objects never set if dec < -90 - lat.
     */
    private fun isCircumpolarFor(loc: LatLong): Boolean {
        // This should be relatively easy to do. In the northern hemisphere,
        // objects never set if dec > 90 - lat and never rise if dec < lat -
        // 90. In the southern hemisphere, objects never set if dec < -90 - lat
        // and never rise if dec > 90 + lat. There must be a better way to do
        // this...
        return if (loc.latitude > 0.0f) {
            dec > 90.0f - loc.latitude
        } else {
            dec < -90.0f - loc.latitude
        }
    }

    /**
     * Return true if the given Ra/Dec is always below the horizon. Return
     * false otherwise.
     * In the northern hemisphere, objects never rise if dec < lat - 90.
     * In the southern hemisphere, objects never rise if dec > 90 - lat.
     */
    private fun isNeverVisible(loc: LatLong): Boolean {
        return if (loc.latitude > 0.0f) {
            dec < loc.latitude - 90.0f
        } else {
            dec > 90.0f + loc.latitude
        }
    }

    companion object {
        @JvmStatic
        fun raDegreesFromHMS(h: Float, m: Float, s: Float): Float {
            return 360 / 24 * (h + m / 60 + s / 60 / 60)
        }

        @JvmStatic
        fun decDegreesFromDMS(d: Float, m: Float, s: Float): Float {
            return d + m / 60 + s / 60 / 60
        }

        @JvmStatic
        fun fromGeocentricCoords(coords: Vector3): RaDec {
            // find the RA and DEC from the rectangular equatorial coords
            val ra = mod2pi(atan2(coords.y, coords.x)) * RADIANS_TO_DEGREES
            val dec =
                (atan(coords.z / sqrt(coords.x * coords.x + coords.y * coords.y))
                        * RADIANS_TO_DEGREES)
            return RaDec(ra, dec)
        }

        @JvmStatic
        fun fromHoursMinutesSeconds(
            raHours: Float, raMinutes: Float, raSeconds: Float,
            decDegrees: Float, decMinutes: Float, decSeconds: Float
        ) = RaDec(
            raDegreesFromHMS(raHours, raMinutes, raSeconds),
            decDegreesFromDMS(decDegrees, decMinutes, decSeconds)
        )
    }
}