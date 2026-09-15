/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The [Matrix3] ↔ quaternion conversions the legacy sensor path smooths through. Each of
 * Shepperd's four branches is exercised, since picking the wrong one is exactly the failure
 * mode a single happy-path test would miss.
 */
class RotationQuaternionTest {
    @Test
    fun `round-trips the identity`() {
        assertMatrixRoundTrips(Matrix3.IDENTITY)
    }

    @Test
    fun `round-trips rotations about each axis`() {
        // 45 degrees keeps the trace positive (branch one); 170 degrees drives it negative and
        // makes a different component largest, selecting each of the other three branches.
        listOf(45.0, 170.0).forEach { degrees ->
            assertMatrixRoundTrips(rotationAboutX(degrees))
            assertMatrixRoundTrips(rotationAboutY(degrees))
            assertMatrixRoundTrips(rotationAboutZ(degrees))
        }
    }

    @Test
    fun `quaternion of a known rotation matches the axis-angle form`() {
        val quaternion = rotationAboutZ(90.0).writeQuaternion(FloatArray(4))
        val halfAngle = Math.toRadians(45.0)
        assertThat(quaternion[0].toDouble()).isWithin(TOLERANCE).of(0.0)
        assertThat(quaternion[1].toDouble()).isWithin(TOLERANCE).of(0.0)
        assertThat(quaternion[2].toDouble()).isWithin(TOLERANCE).of(sin(halfAngle))
        assertThat(quaternion[3].toDouble()).isWithin(TOLERANCE).of(cos(halfAngle))
    }

    @Test
    fun `a quaternion and its negation give the same matrix`() {
        val quaternion = rotationAboutY(120.0).writeQuaternion(FloatArray(4))
        val negated = FloatArray(4) { -quaternion[it] }
        assertMatricesEqual(negated.toRotationMatrix3(), quaternion.toRotationMatrix3())
    }

    @Test
    fun `round-trips a frame built the way orientationFromSensors builds one`() {
        // Rows of (east, north, up), the orthonormal right-handed triad the legacy path fuses.
        val up = Vector3(0.3, -0.4, 0.866).normalized()
        val north = (Vector3(0.0, 1.0, 0.0) - up * (Vector3(0.0, 1.0, 0.0) dot up)).normalized()
        val east = north cross up
        assertMatrixRoundTrips(Matrix3.fromVectors(east, north, up, columnVectors = false))
    }

    private companion object {
        const val TOLERANCE = 1e-5

        fun assertMatrixRoundTrips(matrix: Matrix3) {
            assertMatricesEqual(
                matrix.writeQuaternion(FloatArray(4)).toRotationMatrix3(),
                matrix,
            )
        }

        fun assertMatricesEqual(
            actual: Matrix3,
            expected: Matrix3,
        ) {
            listOf(
                actual.xx to expected.xx, actual.xy to expected.xy, actual.xz to expected.xz,
                actual.yx to expected.yx, actual.yy to expected.yy, actual.yz to expected.yz,
                actual.zx to expected.zx, actual.zy to expected.zy, actual.zz to expected.zz,
            ).forEach { (a, e) -> assertThat(a).isWithin(TOLERANCE).of(e) }
        }

        fun rotationAboutX(degrees: Double): Matrix3 {
            val c = cos(Math.toRadians(degrees))
            val s = sin(Math.toRadians(degrees))
            return Matrix3(
                1.0, 0.0, 0.0,
                0.0, c, -s,
                0.0, s, c,
            )
        }

        fun rotationAboutY(degrees: Double): Matrix3 {
            val c = cos(Math.toRadians(degrees))
            val s = sin(Math.toRadians(degrees))
            return Matrix3(
                c, 0.0, s,
                0.0, 1.0, 0.0,
                -s, 0.0, c,
            )
        }

        fun rotationAboutZ(degrees: Double): Matrix3 {
            val c = cos(Math.toRadians(degrees))
            val s = sin(Math.toRadians(degrees))
            return Matrix3(
                c, -s, 0.0,
                s, c, 0.0,
                0.0, 0.0, 1.0,
            )
        }
    }
}
