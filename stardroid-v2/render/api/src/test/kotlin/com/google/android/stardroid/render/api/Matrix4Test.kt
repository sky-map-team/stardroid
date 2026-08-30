/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.tan

private const val TOL = 1e-12

class Matrix4Test {
    @Test
    fun identity_isNeutralUnderMultiplication() {
        val a = Matrix4(DoubleArray(16) { it.toDouble() })
        assertThat(a * Matrix4.IDENTITY).isEqualTo(a)
        assertThat(Matrix4.IDENTITY * a).isEqualTo(a)
    }

    @Test
    fun get_readsColumnMajorStorage() {
        // Column-major: index = col * 4 + row, so this is the transpose-looking literal.
        val m =
            Matrix4(
                doubleArrayOf(
                    // column 0
                    1.0, 2.0, 3.0, 4.0,
                    // column 1
                    5.0, 6.0, 7.0, 8.0,
                    // column 2
                    9.0, 10.0, 11.0, 12.0,
                    // column 3
                    13.0, 14.0, 15.0, 16.0,
                ),
            )
        assertThat(m[0, 0]).isEqualTo(1.0) // row 0, col 0
        assertThat(m[1, 0]).isEqualTo(2.0) // row 1, col 0
        assertThat(m[0, 1]).isEqualTo(5.0) // row 0, col 1
        assertThat(m[3, 3]).isEqualTo(16.0)
    }

    @Test
    fun times_matchesHandComputedProduct() {
        // A = column-major of rows [[1,2,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,1]] (a shear).
        val a =
            Matrix4(
                doubleArrayOf(
                    1.0, 0.0, 0.0, 0.0,
                    2.0, 1.0, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                ),
            )
        val product = a * a // shear applied twice doubles the shear term
        assertThat(product[0, 1]).isEqualTo(4.0)
        assertThat(product[0, 0]).isEqualTo(1.0)
        assertThat(product[1, 1]).isEqualTo(1.0)
    }

    @Test
    fun toFloatArray_isColumnMajor() {
        val m = Matrix4(DoubleArray(16) { it.toDouble() })
        val floats = m.toFloatArray()
        assertThat(floats.toList()).containsExactlyElementsIn(
            (0..15).map { it.toFloat() },
        ).inOrder()
    }

    @Test
    fun view_rowsAreTheOrthonormalCameraBasis() {
        // Looking down +X with up = +Z.  right = lookDir × up = (1,0,0)×(0,0,1) = (0,-1,0).
        val los = Vector3.UNIT_X
        val up = Vector3.UNIT_Z
        val v = Matrix4.view(los, up)
        // Row 0 = right, row 1 = up, row 2 = -lookDir.
        assertVectorRow(v, 0, Vector3(0.0, -1.0, 0.0))
        assertVectorRow(v, 1, Vector3(0.0, 0.0, 1.0))
        assertVectorRow(v, 2, Vector3(-1.0, 0.0, 0.0))
    }

    @Test
    fun view_reorthogonalizesANonPerpendicularUp() {
        val los = Vector3.UNIT_X
        val tiltedUp = Vector3(0.5, 0.5, 1.0) // not perpendicular to the line of sight
        val v = Matrix4.view(los, tiltedUp)
        // Each row must be a unit vector and the three rows mutually orthogonal.
        val r0 = row(v, 0)
        val r1 = row(v, 1)
        val r2 = row(v, 2)
        assertThat(r0.length).isWithin(1e-9).of(1.0)
        assertThat(r1.length).isWithin(1e-9).of(1.0)
        assertThat(r2.length).isWithin(1e-9).of(1.0)
        assertThat(r0 dot r1).isWithin(1e-9).of(0.0)
        assertThat(r0 dot r2).isWithin(1e-9).of(0.0)
        assertThat(r1 dot r2).isWithin(1e-9).of(0.0)
    }

    @Test
    fun view_rejectsCollinearUpAndLineOfSight() {
        // A degenerate basis would collapse every point to the screen centre; fail fast instead.
        assertThrows<IllegalArgumentException> { Matrix4.view(Vector3.UNIT_Z, Vector3.UNIT_Z) }
    }

    @Test
    fun perspective_rejectsNonPositiveDimensions() {
        assertThrows<IllegalArgumentException> { Matrix4.perspective(0.0, 600.0, 90.0) }
        assertThrows<IllegalArgumentException> { Matrix4.perspective(600.0, 0.0, 90.0) }
    }

    @Test
    fun perspective_rejectsOutOfRangeFov() {
        // fov 0 → tan(0)=0 → 1/0, fov 180 → tan(90°)=∞: both yield a degenerate/NaN matrix.
        assertThrows<IllegalArgumentException> { Matrix4.perspective(600.0, 600.0, 0.0) }
        assertThrows<IllegalArgumentException> { Matrix4.perspective(600.0, 600.0, 180.0) }
    }

    @Test
    fun perspective_appliesHalfTheFovToTheShorterSideAndAspectToTheLonger() {
        val fovDeg = 90.0
        val width = 800.0
        val height = 400.0
        val p = Matrix4.perspective(width, height, fovDeg)
        val cot = 1.0 / tan(Math.toRadians(fovDeg / 2.0)) // = 1.0 for 90°
        // Landscape: the shorter side is vertical, so fovDeg is the vertical FOV.
        assertThat(p[1, 1]).isWithin(TOL).of(cot)
        assertThat(p[0, 0]).isWithin(TOL).of(cot * height / width)
        // The projection's w-row picks out -(view z): m[3][2] == -1.
        assertThat(p[3, 2]).isEqualTo(-1.0)
        assertThat(p[3, 3]).isEqualTo(0.0)
    }

    @Test
    fun perspective_isRotationInvariant() {
        // The same fov on swapped dimensions swaps the x/y scales: what fit across the short
        // side of the landscape surface fits across the short side of the portrait one, so the
        // sky's apparent scale does not jump when the display rotates.
        val landscape = Matrix4.perspective(800.0, 400.0, 60.0)
        val portrait = Matrix4.perspective(400.0, 800.0, 60.0)
        assertThat(portrait[0, 0]).isWithin(TOL).of(landscape[1, 1])
        assertThat(portrait[1, 1]).isWithin(TOL).of(landscape[0, 0])
    }

    private fun assertVectorRow(
        m: Matrix4,
        row: Int,
        expected: Vector3,
    ) {
        val r = row(m, row)
        assertThat(r.x).isWithin(1e-9).of(expected.x)
        assertThat(r.y).isWithin(1e-9).of(expected.y)
        assertThat(r.z).isWithin(1e-9).of(expected.z)
    }

    private fun row(
        m: Matrix4,
        row: Int,
    ) = Vector3(m[row, 0], m[row, 1], m[row, 2])
}
