/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

private const val TOL = 1e-9

/** Ported from v1 `Vector3Test`, adapted to immutable `Double` semantics. */
class Vector3Test {
    private fun assertClose(
        actual: Vector3,
        expected: Vector3,
    ) {
        assertThat(actual.x).isWithin(TOL).of(expected.x)
        assertThat(actual.y).isWithin(TOL).of(expected.y)
        assertThat(actual.z).isWithin(TOL).of(expected.z)
    }

    @Test
    fun equals_andHashCode() {
        val one = Vector3(1.0, 2.0, 3.0) * 2.0
        val two = Vector3(2.0, 4.0, 6.0)
        assertThat(one).isEqualTo(two)
        assertThat(one.hashCode()).isEqualTo(two.hashCode())
    }

    @Test
    fun length_andLength2() {
        val pythag = Vector3(3.0, 4.0, 0.0)
        assertThat(pythag.length).isWithin(TOL).of(5.0)
        assertThat(pythag.length2).isWithin(TOL).of(25.0)
    }

    @Test
    fun normalized() {
        val v = Vector3(3.0, 4.0, 0.0).normalized()
        assertThat(v.length).isWithin(TOL).of(1.0)
        assertThat(v.x / v.y).isWithin(TOL).of(3.0 / 4.0)
    }

    @Test
    fun normalized_returnsZeroForTinyVector() {
        assertClose(Vector3(1e-9, 0.0, 0.0).normalized(), Vector3.ZERO)
    }

    @Test
    fun scaleAndDivide() {
        assertClose(Vector3(3.0, 4.0, 5.0) * 2.0, Vector3(6.0, 8.0, 10.0))
        assertClose(Vector3(4.0, 2.0, 6.0) / 2.0, Vector3(2.0, 1.0, 3.0))
    }

    @Test
    fun plusAndMinus() {
        val a = Vector3(1.0, 2.0, 3.0)
        val b = Vector3(-2.0, 2.0, 2.0)
        assertClose(a + b, Vector3(-1.0, 4.0, 5.0))
        assertClose(a - b, Vector3(3.0, 0.0, 1.0))
    }

    @Test
    fun unaryMinus() {
        assertClose(-Vector3(1.0, -2.0, 3.0), Vector3(-1.0, 2.0, -3.0))
    }

    @Test
    fun dotProduct() {
        assertThat(Vector3(1.0, 2.0, 3.0) dot Vector3(0.3, 0.4, 0.5)).isWithin(TOL).of(2.6)
    }

    @Test
    fun crossProduct_axes() {
        assertClose(Vector3.UNIT_X cross Vector3.UNIT_Y, Vector3.UNIT_Z)
    }

    @Test
    fun crossProduct_isPerpendicular() {
        val v1 = Vector3(1.0, 2.0, 3.0)
        val v2 = Vector3(-1.0, 3.0, -3.0)
        val v3 = v1 cross v2
        assertThat(v1 dot v3).isWithin(TOL).of(0.0)
        assertThat(v2 dot v3).isWithin(TOL).of(0.0)
    }

    @Test
    fun distanceTo() {
        assertThat(Vector3(1.0, 2.0, 5.0).distanceTo(Vector3(-2.0, 2.0, 1.0))).isWithin(TOL).of(5.0)
    }

    @Test
    fun projectOnto() {
        val v1 = Vector3(1.0, 1.0, 2.0)
        val unit = Vector3(2.0, 1.0, -1.0).normalized()
        val projection = v1.projectOnto(unit)
        // The component of v1 perpendicular to `unit` is orthogonal to the projection.
        assertThat((v1 - projection) dot projection).isWithin(TOL).of(0.0)
    }

    @Test
    fun cosineSimilarity() {
        val v1 = Vector3(1.0, 2.0, 3.0)
        val v2 = Vector3(2.0, -1.0, 10.0)
        val expected = (v1 dot v2) / sqrt((v1 dot v1) * (v2 dot v2))
        assertThat(v1.cosineSimilarity(v2)).isWithin(TOL).of(expected)
    }
}
