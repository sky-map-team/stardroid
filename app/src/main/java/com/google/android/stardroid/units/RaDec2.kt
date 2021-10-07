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
package com.google.android.stardroid.units

import com.google.android.stardroid.units.RaDec
import com.google.android.stardroid.units.LatLong
import com.google.android.stardroid.units.HeliocentricCoordinates
import com.google.android.stardroid.util.Geometry
import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.GeocentricCoordinates
import com.google.android.stardroid.util.MathUtil
import java.util.*

class RaDec(// In degrees
    var ra: Float, // In degrees
    var dec: Float
) {
    constructor(
        raHours: Float, raMinutes: Float, raSeconds: Float,
        decDegrees: Float, decMinutes: Float, decSeconds: Float
    ) : this(
        raDegreesFromHMS(raHours, raMinutes, raSeconds),
        decDegreesFromDMS(decDegrees, decMinutes, decSeconds)
    ) {
    }

    override fun toString(): String {
        return """RA: $ra degrees
Dec: $dec degrees
"""
    }
    // This should be relatively easy to do. In the northern hemisphere,
    // objects never set if dec > 90 - lat and never rise if dec < lat -
    // 90. In the southern hemisphere, objects never set if dec < -90 - lat
    // and never rise if dec > 90 + lat. There must be a better way to do
    // this...
    /**
     * Return true if the given Ra/Dec is always above the horizon. Return
     * false otherwise.
     * In the northern hemisphere, objects never set if dec > 90 - lat.
     * In the southern hemisphere, objects never set if dec < -90 - lat.
     */
    private fun isCircumpolarFor(loc: LatLong): Boolean {
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
        fun raDegreesFromHMS(h: Float, m: Float, s: Float): Float {
            return 360 / 24 * (h + m / 60 + s / 60 / 60)
        }

        fun decDegreesFromDMS(d: Float, m: Float, s: Float): Float {
            return d + m / 60 + s / 60 / 60
        }

        fun calculateRaDecDist(coords: HeliocentricCoordinates): RaDec {
            // find the RA and DEC from the rectangular equatorial coords
            val ra =
                Geometry.mod2pi(MathUtil.atan2(coords.y, coords.x)) * Geometry.RADIANS_TO_DEGREES
            val dec =
                (MathUtil.atan(coords.z / MathUtil.sqrt(coords.x * coords.x + coords.y * coords.y))
                        * Geometry.RADIANS_TO_DEGREES)
            return RaDec(ra, dec)
        }

        @Deprecated("Use Universe.getPlanet instead.")
        fun getInstanceDontUse(
            planet: Planet, time: Date?,
            earthCoordinates: HeliocentricCoordinates
        ): RaDec {
            // TODO(serafini): This is a temporary hack until we re-factor the Planetary calculations.
            if (planet == Planet.Moon) {
                return Planet.calculateLunarGeocentricLocation(time)
            }
            var coords: HeliocentricCoordinates? = null
            if (planet == Planet.Sun) {
                // Invert the view, since we want the Sun in earth coordinates, not the Earth in sun
                // coordinates.
                coords = HeliocentricCoordinates(
                    earthCoordinates.radius, earthCoordinates.x * -1.0f,
                    earthCoordinates.y * -1.0f, earthCoordinates.z * -1.0f
                )
            } else {
                coords = HeliocentricCoordinates.getInstance(planet, time)
                coords.Subtract(earthCoordinates)
            }
            val equ = coords!!.CalculateEquatorialCoordinates()
            return calculateRaDecDist(equ)
        }

        fun getInstance(coords: GeocentricCoordinates): RaDec {
            var raRad = MathUtil.atan2(coords.y, coords.x)
            if (raRad < 0) raRad += MathUtil.TWO_PI
            val decRad = MathUtil.atan2(
                coords.z,
                MathUtil.sqrt(coords.x * coords.x + coords.y * coords.y)
            )
            return RaDec(
                raRad * Geometry.RADIANS_TO_DEGREES,
                decRad * Geometry.RADIANS_TO_DEGREES
            )
        }
    }
}