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

import com.google.android.stardroid.util.MathUtil.cos
import com.google.android.stardroid.util.MathUtil.sin
import com.google.android.stardroid.util.MathUtil.atan2
import com.google.android.stardroid.util.MathUtil.asin
import com.google.android.stardroid.util.Geometry

/**
 * Utilities for dealing with a vector representing an object's location in Euclidean space
 * when it is projected onto a unit sphere (with the Earth at the
 * center).
 *
 * @author Brent Bryan
 */
// TODO(jontayler): just make these functions somewhere.
object GeocentricCoordinates {
    /**
     * Updates the given vector with the supplied RaDec.
     * [RaDec].
     */
    @JvmStatic
    fun updateFromRaDec(v: Vector3, raDec: RaDec) {
        updateFromRaDec(v, raDec.ra, raDec.dec)
    }

    /**
     * Updates these coordinates with the given ra and dec in degrees.
     */
    private fun updateFromRaDec(v: Vector3, ra: Float, dec: Float) {
        val raRadians = ra * Geometry.DEGREES_TO_RADIANS
        val decRadians = dec * Geometry.DEGREES_TO_RADIANS
        v.x = cos(raRadians) * cos(decRadians)
        v.y = sin(raRadians) * cos(decRadians)
        v.z = sin(decRadians)
    }

    /** Returns the RA in degrees from the given vector assuming it's in Geocentric coordinates  */
    // TODO(jontayler): define the different coordinate systems somewhere.
    @JvmStatic
    fun getRaOfUnitGeocentricVector(v: Vector3): Float {
        // Assumes unit sphere.
        return Geometry.RADIANS_TO_DEGREES * atan2(v.y, v.x)
    }

    /** Returns the declination in degrees from the given vector assuming it's in Geocentric coordinates  */
    @JvmStatic
    fun getDecOfUnitGeocentricVector(v: Vector3): Float {
        // Assumes unit sphere.
        return Geometry.RADIANS_TO_DEGREES * asin(v.z)
    }

    /**
     * Convert ra and dec to x,y,z where the point is place on the unit sphere.
     */
    @JvmStatic
    fun getGeocentricCoords(raDec: RaDec): Vector3 {
        return getGeocentricCoords(raDec.ra, raDec.dec)
    }

    @JvmStatic
    fun getGeocentricCoords(ra: Float, dec: Float): Vector3 {
        val coords = Vector3(0.0f, 0.0f, 0.0f)
        updateFromRaDec(coords, ra, dec)
        return coords
    }
}