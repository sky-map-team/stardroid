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

import com.google.android.stardroid.math.MathUtil.ceil
import com.google.android.stardroid.math.MathUtil.cos
import com.google.android.stardroid.math.MathUtil.floor
import com.google.android.stardroid.math.MathUtil.sin
import com.google.android.stardroid.math.MathUtil.sqrt
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
    const val DEGREES_TO_RADIANS = MathUtil.PI / 180.0f

    // Convert Radians to Degrees
    const val RADIANS_TO_DEGREES = 180.0f / MathUtil.PI

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
        val factor = x / MathUtil.TWO_PI
        var result = MathUtil.TWO_PI * (factor - abs_floor(factor))
        if (result < 0.0) {
            result = MathUtil.TWO_PI + result
        }
        return result
    }

    @JvmStatic
    fun scalarProduct(v1: Vector3, v2: Vector3): Float {
        return v1 dot v2
    }

    @JvmStatic
    fun vectorProduct(v1: Vector3, v2: Vector3): Vector3 {
        return v1 * v2
    }

    /**
     * Scales the vector by the given amount and returns a new vector.
     */
    @JvmStatic
    fun scaleVector(v: Vector3, scale: Float): Vector3 {
        return v * scale
    }

    /**
     * Creates and returns a new Vector3 which is the sum of both arguments.
     * @param first
     * @param second
     * @return vector sum first + second
     */
    @JvmStatic
    fun addVectors(first: Vector3, second: Vector3): Vector3 {
        return first + second
    }

    @JvmStatic
    fun cosineSimilarity(v1: Vector3, v2: Vector3): Float {
        // We might want to optimize this implementation at some point.
        return (scalarProduct(v1, v2)
                / sqrt(
            scalarProduct(v1, v1)
                    * scalarProduct(v2, v2)
        ))
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
     * Multiply two 3X3 matrices m1 * m2.
     */
    @JvmStatic
    fun matrixMultiply(m1: Matrix3x3, m2: Matrix3x3): Matrix3x3 {
        return Matrix3x3(
            m1.xx * m2.xx + m1.xy * m2.yx + m1.xz * m2.zx,
            m1.xx * m2.xy + m1.xy * m2.yy + m1.xz * m2.zy,
            m1.xx * m2.xz + m1.xy * m2.yz + m1.xz * m2.zz,
            m1.yx * m2.xx + m1.yy * m2.yx + m1.yz * m2.zx,
            m1.yx * m2.xy + m1.yy * m2.yy + m1.yz * m2.zy,
            m1.yx * m2.xz + m1.yy * m2.yz + m1.yz * m2.zz,
            m1.zx * m2.xx + m1.zy * m2.yx + m1.zz * m2.zx,
            m1.zx * m2.xy + m1.zy * m2.yy + m1.zz * m2.zy,
            m1.zx * m2.xz + m1.zy * m2.yz + m1.zz * m2.zz
        )
    }

    /**
     * Calculate w = m * v where m is a 3X3 matrix and v a column vector.
     */
    @JvmStatic
    fun matrixVectorMultiply(m: Matrix3x3, v: Vector3): Vector3 {
        return Vector3(
            m.xx * v.x + m.xy * v.y + m.xz * v.z,
            m.yx * v.x + m.yy * v.y + m.yz * v.z,
            m.zx * v.x + m.zy * v.y + m.zz * v.z
        )
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