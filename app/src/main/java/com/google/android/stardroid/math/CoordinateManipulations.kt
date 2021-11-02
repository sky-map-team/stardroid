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
import com.google.android.stardroid.math.MathUtils.asin
import com.google.android.stardroid.math.MathUtils.atan2
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin

/**
 * Utilities for manipulating different coordinate systems.
 *
 * |RaDec| represents right ascension and declination.  It's a pair of angles roughly analogous
 * to latitude and longitude. Centered on Earth, they are fixed in space.
 *
 * Geocentric coordinates. These are coordinates centered on Earth and fixed in space. They
 * can be freely converted to the angles |RaDec|. The z axis corresponds to a Dec of 90 degrees
 * and the x axis to a right ascension of zero and a dec of zero.
 */

/**
 * Updates the given vector with the supplied [RaDec].
 */
fun Vector3.updateFromRaDec(raDec: RaDec) {
    this.updateFromRaDec(raDec.ra, raDec.dec)
}

/**
 * Updates these coordinates with the given ra and dec in degrees.
 */
private fun Vector3.updateFromRaDec(ra: Float, dec: Float) {
    val raRadians = ra * DEGREES_TO_RADIANS
    val decRadians = dec * DEGREES_TO_RADIANS
    this.x = cos(raRadians) * cos(decRadians)
    this.y = sin(raRadians) * cos(decRadians)
    this.z = sin(decRadians)
}

/** Returns the RA in degrees from the given vector assuming it's a unit vector in Geocentric coordinates  */
fun getRaOfUnitGeocentricVector(v: Vector3): Float {
    // Assumes unit sphere.
    return RADIANS_TO_DEGREES * atan2(v.y, v.x)
}

/** Returns the declination in degrees from the given vector assuming it's a unit vector in Geocentric coordinates  */
fun getDecOfUnitGeocentricVector(v: Vector3): Float {
    // Assumes unit sphere.
    return RADIANS_TO_DEGREES * asin(v.z)
}

/**
 * Converts ra and dec to x,y,z Geocentric where the point is place on the unit sphere.
 */
fun getGeocentricCoords(raDec: RaDec): Vector3 {
    return getGeocentricCoords(raDec.ra, raDec.dec)
}

/**
 * Converts ra and dec to x,y,z Geocentric where the point is place on the unit sphere.
 */
fun getGeocentricCoords(ra: Float, dec: Float): Vector3 {
    val coords = Vector3(0.0f, 0.0f, 0.0f)
    coords.updateFromRaDec(ra, dec)
    return coords
}

// Value of the obliquity of the ecliptic for J2000
private const val OBLIQUITY = 23.439281f * DEGREES_TO_RADIANS

/**
 * Converts OrbitalElements into "HeliocentricCoordinates" - cartesian coordinates
 * centered on the sun with a z-axis pointing normal to Earth's orbital plane
 * and measured in Astronomical units.
 */
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
fun convertToEquatorialCoordinates(earthOrbitalPlane : Vector3): Vector3 {
    return Vector3(
        earthOrbitalPlane.x,
        earthOrbitalPlane.y * cos(OBLIQUITY) - earthOrbitalPlane.z * sin(OBLIQUITY),
        earthOrbitalPlane.y * sin(OBLIQUITY) + earthOrbitalPlane.z * cos(OBLIQUITY)
    )
}

