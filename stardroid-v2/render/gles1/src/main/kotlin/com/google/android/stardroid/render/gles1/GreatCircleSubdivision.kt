/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import kotlin.math.acos

/**
 * Subdivides a polyline's vertices so consecutive points are at most [maxSegmentAngleDeg] apart on
 * the unit sphere. A straight 3D chord between two distant points on the sphere cuts inside it; v1
 * baked this into line construction. It moves to the backend because "is this chord visibly short
 * of the sphere" depends on the projection, which only the backend has (render-api.md).
 */
object GreatCircleSubdivision {
    /** [vertices] unchanged if it has fewer than 2 points (nothing to subdivide). */
    fun subdivide(
        vertices: List<Vector3>,
        maxSegmentAngleDeg: Double,
    ): List<Vector3> {
        require(maxSegmentAngleDeg > 0.0) {
            "maxSegmentAngleDeg must be positive, got $maxSegmentAngleDeg"
        }
        if (vertices.size < 2) return vertices
        val out = ArrayList<Vector3>(vertices.size)
        out.add(vertices.first())
        for (i in 0 until vertices.size - 1) {
            subdivideSegment(vertices[i], vertices[i + 1], maxSegmentAngleDeg, out)
        }
        return out
    }

    private fun subdivideSegment(
        a: Vector3,
        b: Vector3,
        maxSegmentAngleDeg: Double,
        out: MutableList<Vector3>,
    ) {
        if (angleBetweenDeg(a, b) <= maxSegmentAngleDeg) {
            out.add(b)
            return
        }
        val sum = a + b
        // a and b (anti)podal: their great-circle midpoint is undefined (any point on the
        // perpendicular equator qualifies). No real line primitive has exactly antipodal
        // consecutive vertices, but bail out rather than recurse on a zero-length sum forever.
        if (sum.length2 < ANTIPODAL_EPSILON || !sum.length2.isFinite()) {
            out.add(b)
            return
        }
        val midpoint = sum.normalized()
        subdivideSegment(a, midpoint, maxSegmentAngleDeg, out)
        subdivideSegment(midpoint, b, maxSegmentAngleDeg, out)
    }

    private fun angleBetweenDeg(
        a: Vector3,
        b: Vector3,
    ): Double {
        val cosTheta = (a dot b).coerceIn(-1.0, 1.0)
        return acos(cosTheta) * RADIANS_TO_DEGREES
    }

    /** Matches [Vector3.normalized]'s own too-short-to-normalize-reliably threshold, squared. */
    private const val ANTIPODAL_EPSILON = 1e-12
}
