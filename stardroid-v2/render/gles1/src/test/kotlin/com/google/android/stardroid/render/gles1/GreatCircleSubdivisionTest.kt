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
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.acos

class GreatCircleSubdivisionTest {
    @Test
    fun `short segments are left untouched`() {
        val vertices = listOf(Vector3.UNIT_X, Vector3.UNIT_Y)
        val result = GreatCircleSubdivision.subdivide(vertices, maxSegmentAngleDeg = 100.0)
        assertThat(result).isEqualTo(vertices)
    }

    @Test
    fun `fewer than two vertices is returned unchanged`() {
        val vertices = listOf(Vector3.UNIT_X)
        assertThat(GreatCircleSubdivision.subdivide(vertices, 10.0)).isEqualTo(vertices)
        assertThat(GreatCircleSubdivision.subdivide(emptyList(), 10.0)).isEmpty()
    }

    @Test
    fun `endpoints are preserved`() {
        val vertices = listOf(Vector3.UNIT_X, Vector3.UNIT_Y, Vector3.UNIT_Z)
        val result = GreatCircleSubdivision.subdivide(vertices, maxSegmentAngleDeg = 5.0)
        assertThat(result.first()).isEqualTo(vertices.first())
        assertThat(result.last()).isEqualTo(vertices.last())
    }

    @Test
    fun `every consecutive pair is within the threshold`() {
        val vertices = listOf(Vector3.UNIT_X, Vector3.UNIT_Y, Vector3.UNIT_Z, -Vector3.UNIT_X)
        val maxAngle = 5.0
        val result = GreatCircleSubdivision.subdivide(vertices, maxAngle)
        for (i in 0 until result.size - 1) {
            assertThat(angleDeg(result[i], result[i + 1])).isAtMost(maxAngle + 1e-6)
        }
    }

    @Test
    fun `inserted points stay on the unit sphere`() {
        val vertices = listOf(Vector3.UNIT_X, Vector3.UNIT_Y)
        val result = GreatCircleSubdivision.subdivide(vertices, maxSegmentAngleDeg = 1.0)
        assertThat(result.size).isGreaterThan(2)
        for (v in result) {
            assertThat(v.length).isWithin(1e-9).of(1.0)
        }
    }

    @Test
    fun `exactly antipodal endpoints terminate instead of recursing forever`() {
        val vertices = listOf(Vector3.UNIT_X, -Vector3.UNIT_X)
        val result = GreatCircleSubdivision.subdivide(vertices, maxSegmentAngleDeg = 5.0)
        assertThat(result.first()).isEqualTo(Vector3.UNIT_X)
        assertThat(result.last()).isEqualTo(-Vector3.UNIT_X)
    }

    @Test
    fun `rejects a non-positive threshold`() {
        val vertices = listOf(Vector3.UNIT_X, Vector3.UNIT_Y)
        assertThrows<IllegalArgumentException> {
            GreatCircleSubdivision.subdivide(vertices, maxSegmentAngleDeg = 0.0)
        }
    }

    private fun angleDeg(
        a: Vector3,
        b: Vector3,
    ): Double = acos((a dot b).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
}
