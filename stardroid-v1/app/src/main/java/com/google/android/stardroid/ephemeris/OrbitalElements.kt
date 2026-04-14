// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.ephemeris

import android.util.Log
import com.google.android.stardroid.math.MathUtils.sin
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.abs
import com.google.android.stardroid.math.MathUtils.sqrt
import com.google.android.stardroid.math.MathUtils.tan
import com.google.android.stardroid.math.mod2pi
import com.google.android.stardroid.util.MiscUtil

/**
 * This class wraps the six parameters which define the path an object takes as
 * it orbits the sun.
 *
 * The equations come from JPL's Solar System Dynamics site:
 * http://ssd.jpl.nasa.gov/?planet_pos
 *
 * The original source for the calculations is based on the approximations described in:
 * Van Flandern T. C., Pulkkinen, K. F. (1979): "Low-Precision Formulae for
 * Planetary Positions", 1979, Astrophysical Journal Supplement Series, Vol. 41,
 * pp. 391-411.
 *
 *
 * @author Kevin Serafini
 * @author Brent Bryan
 */
data class OrbitalElements(
    val distance: Float, // Mean distance (AU)
    val eccentricity: Float, // Eccentricity of orbit
    val inclination: Float, // Inclination of orbit (AngleUtils.RADIANS)
    val ascendingNode: Float, // Longitude of ascending node (AngleUtils.RADIANS)
    val perihelion: Float, // Longitude of perihelion (AngleUtils.RADIANS)
    val meanLongitude: Float // Mean longitude (AngleUtils.RADIANS)
) {
    val anomaly: Float
        get() = calculateTrueAnomaly(meanLongitude - perihelion, eccentricity)

    private val TAG = MiscUtil.getTag(OrbitalElements::class.java)

    // calculation error

    // compute the true anomaly from mean anomaly using iteration
    // m - mean anomaly in radians
    // e - orbit eccentricity
    // Return value is in radians.
    private fun calculateTrueAnomaly(m: Float, e: Float): Float {
        // initial approximation of eccentric anomaly
        var e0 = m + e * sin(m) * (1.0f + e * cos(m))
        var e1: Float

        // iterate to improve accuracy
        var counter = 0
        do {
            e1 = e0
            e0 = e1 - (e1 - e * sin(e1) - m) / (1.0f - e * cos(e1))
            if (counter++ > 100) {
                Log.d(TAG, "Failed to converge! Exiting.")
                Log.d(TAG, "e1 = $e1, e0 = $e0")
                Log.d(TAG, "diff = " + abs(e0 - e1))
                break
            }
        } while (abs(e0 - e1) > EPSILON)

        // convert eccentric anomaly to true anomaly
        val v = 2f * kotlin.math.atan(
            sqrt((1 + e) / (1 - e))
                    * tan(0.5f * e0)
        )
        return mod2pi(v)
    }
}

private const val EPSILON = 1.0e-6f
