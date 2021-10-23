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

import com.google.android.stardroid.math.MathUtils.ceil
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.floor
import com.google.android.stardroid.math.MathUtils.sin
import java.util.*

/**
 * Utilities for working with angles, distances, matrices, and time.
 *
 * @author Kevin Serafini
 * @author Brent Bryan
 * @author Dominic Widdows
 * @author John Taylor
 */
object Geometry {
    // Convert Degrees to Radians
    const val DEGREES_TO_RADIANS = MathUtils.PI / 180.0f

    // Convert Radians to Degrees
    const val RADIANS_TO_DEGREES = 180.0f / MathUtils.PI

    /**
     * Return the integer part of a number
     */
    fun abs_floor(x: Float): Float {
        val result: Float
        result =
            if (x >= 0.0) floor(x) else ceil(
                x
            )
        return result
    }

    /**
     * Returns the modulo the given value by 2\pi. Returns an angle in the range 0
     * to 2\pi radians.
     */
    @JvmStatic
    fun mod2pi(x: Float): Float {
        val factor = x / MathUtils.TWO_PI
        var result = MathUtils.TWO_PI * (factor - abs_floor(factor))
        if (result < 0.0) {
            result = MathUtils.TWO_PI + result
        }
        return result
    }

    /**
     * Convert ra and dec to x,y,z where the point is place on the unit sphere.
     */
    // TODO(jontayler): is this a dupe method?
    @JvmStatic
    fun getXYZ(raDec: RaDec): Vector3 {
        val raRadians = raDec.ra * DEGREES_TO_RADIANS
        val decRadians = raDec.dec * DEGREES_TO_RADIANS
        return Vector3(
            cos(raRadians) * cos(decRadians),
            sin(raRadians) * cos(decRadians),
            sin(decRadians)
        )
    }

    /**
     * Compute celestial coordinates of zenith from utc, lat long.
     */
    @JvmStatic
    fun calculateRADecOfZenith(utc: Date?, location: LatLong): RaDec {
        // compute overhead RA in degrees
        val my_ra = TimeUtil.meanSiderealTime(utc, location.longitude)
        val my_dec = location.latitude
        return RaDec(my_ra, my_dec)
    }

    /**
     * Calculate the rotation matrix for a certain number of degrees about the
     * give axis.
     * @param degrees
     * @param axis - must be a unit vector.
     */
    @JvmStatic
    fun calculateRotationMatrix(degrees: Float, axis: Vector3): Matrix3x3 {
        // Construct the rotation matrix about this vector
        val cosD = cos(degrees * DEGREES_TO_RADIANS)
        val sinD = sin(degrees * DEGREES_TO_RADIANS)
        val oneMinusCosD = 1f - cosD
        val x = axis.x
        val y = axis.y
        val z = axis.z
        val xs = x * sinD
        val ys = y * sinD
        val zs = z * sinD
        val xm = x * oneMinusCosD
        val ym = y * oneMinusCosD
        val zm = z * oneMinusCosD
        val xym = x * ym
        val yzm = y * zm
        val zxm = z * xm
        return Matrix3x3(
            x * xm + cosD, xym + zs, zxm - ys,
            xym - zs, y * ym + cosD, yzm + xs,
            zxm + ys, yzm - xs, z * zm + cosD
        )
    }
}

