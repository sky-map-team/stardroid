/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/*
 * Pure geometric helpers shared across the codebase: rotations and angular separation.
 */

/**
 * The rotation matrix for [degrees] about [axis].
 *
 * @param axis must be a unit vector.
 */
fun rotationMatrix(
    degrees: Double,
    axis: Vector3,
): Matrix3 {
    val cosD = cos(degrees * DEGREES_TO_RADIANS)
    val sinD = sin(degrees * DEGREES_TO_RADIANS)
    val oneMinusCosD = 1.0 - cosD
    val (x, y, z) = axis
    val xs = x * sinD
    val ys = y * sinD
    val zs = z * sinD
    val xm = x * oneMinusCosD
    val ym = y * oneMinusCosD
    val zm = z * oneMinusCosD
    val xym = x * ym
    val yzm = y * zm
    val zxm = z * xm
    return Matrix3(
        x * xm + cosD, xym + zs, zxm - ys,
        xym - zs, y * ym + cosD, yzm + xs,
        zxm + ys, yzm - xs, z * zm + cosD,
    )
}

/** Angular separation between two directions on the celestial sphere, in degrees. */
fun angularSeparationDeg(
    a: RaDec,
    b: RaDec,
): Double {
    // toGeocentricVector() returns unit vectors, so the dot product is already the cosine.
    val cosTheta = a.toGeocentricVector() dot b.toGeocentricVector()
    return acos(cosTheta.coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
}
