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

import com.google.android.stardroid.ephemeris.OrbitalElements
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin

class HeliocentricCoordinates() {

    companion object {
        // Value of the obliquity of the ecliptic for J2000
        private const val OBLIQUITY = 23.439281f * Geometry.DEGREES_TO_RADIANS

        /**
         * Converts OrbitalElements into "HeliocentricCoordinates" - cartesian coordinates
         * centered on the sun with a z-axis pointing normal to Earth's orbital plane
         * and measured in Astronomical units.
         */
        @JvmStatic
        fun heliocentricCoordinatesFromOrbitalElements(elem: OrbitalElements): Vector3 {
            val anomaly = elem.anomaly
            val ecc = elem.eccentricity
            val radius = elem.distance * (1 - ecc * ecc) / (1 + ecc * cos(anomaly))

            // heliocentric rectangular coordinates of planet
            val per = elem.perihelion
            val asc = elem.ascendingNode
            val inc = elem.inclination
            val xh = radius *
                    (cos(asc) * cos(anomaly + per - asc) -
                            sin(asc) * sin(anomaly + per - asc) *
                            cos(inc))
            val yh = radius *
                    (sin(asc) * cos(anomaly + per - asc) +
                            cos(asc) * sin(anomaly + per - asc) *
                            cos(inc))
            val zh = radius * (sin(anomaly + per - asc) * sin(inc))
            return Vector3(xh, yh, zh)
        }

        /**
         * Converts to coordinates centered on Earth in the Earth's rotational plane to
         * coordinates in Earth's equatorial plane.
         */
        @JvmStatic
        fun convertToEquatorialCoordinates(earthOrbitalPlane : Vector3): Vector3 {
            return Vector3(
                earthOrbitalPlane.x,
                earthOrbitalPlane.y * cos(OBLIQUITY) - earthOrbitalPlane.z * sin(OBLIQUITY),
                earthOrbitalPlane.y * sin(OBLIQUITY) + earthOrbitalPlane.z * cos(OBLIQUITY)
            )
        }
    }
}