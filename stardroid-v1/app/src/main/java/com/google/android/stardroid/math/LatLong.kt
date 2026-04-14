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
package com.google.android.stardroid.math

/**
 * A simple struct for latitude and longitude.
 */
data class LatLong(private val _latitudeDeg: Float, private val _longitudeDeg: Float) {
    val latitude = _latitudeDeg.coerceIn(-90f, 90f)
    val longitude = flooredMod(_longitudeDeg + 180f, 360f) - 180f

    /**
     * This constructor automatically downcasts the latitude and longitude to
     * floats, so that the previous constructor can be used. It is added as a
     * convenience method, since many of the GPS methods return doubles.
     */
    constructor(latitude: Double, longitude: Double) : this(
        latitude.toFloat(),
        longitude.toFloat()
    )

    /**
     * Angular distance between the two points.
     * @param other
     * @return degrees
     */
    fun distanceFrom(other: LatLong): Float {
        // Some misuse of the astronomy math classes
        val otherPnt = getGeocentricCoords(
            other.longitude,
            other.latitude
        )
        val thisPnt = getGeocentricCoords(
            longitude,
            latitude
        )
        val cosTheta = thisPnt.cosineSimilarity(otherPnt)
        return MathUtils.acos(cosTheta) * 180f / PI
    }
}