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

private const val TOL = 1e-9

/** Ported from v1 `Matrix3x3Test`. */
class Matrix3Test {
    @Test
    fun determinant() {
        assertThat(Matrix3.IDENTITY.determinant).isWithin(TOL).of(1.0)
        assertThat(Matrix3(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0).determinant)
            .isWithin(TOL).of(0.0)
        assertThat(Matrix3(1.0, 2.0, 33.0, 4.0, 5.0, 6.0, 7.0, 8.0, 11.0).determinant)
            .isWithin(TOL).of(-96.0)
    }

    @Test
    fun identityIsItsOwnInverse() {
        assertClose(Matrix3.IDENTITY.inverse!!, Matrix3.IDENTITY)
    }

    @Test
    fun inverseTimesSelfIsIdentity() {
        val matrices =
            listOf(
                Matrix3(1.0, 2.0, 0.0, 0.0, 1.0, 5.0, 0.0, 0.0, 1.0),
                Matrix3(1.0, 2.0, 3.0, 6.0, 5.0, 4.0, 0.0, 0.0, 1.0),
            )
        for (m in matrices) {
            assertClose(m * m.inverse!!, Matrix3.IDENTITY)
        }
    }

    @Test
    fun inverseOfSingularIsNull() {
        assertThat(Matrix3(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0).inverse).isNull()
    }

    @Test
    fun multiply() {
        val m1 = Matrix3(1.0, 2.0, 4.0, -1.0, -3.0, 5.0, 3.0, 2.0, 6.0)
        val m2 = Matrix3(3.0, -1.0, 4.0, 0.0, 2.0, 1.0, 2.0, -1.0, 2.0)
        assertClose(m1 * m2, Matrix3(11.0, -1.0, 14.0, 7.0, -10.0, 3.0, 21.0, -5.0, 26.0))
        assertClose(m1 * Vector3(0.0, -1.0, 2.0), Vector3(6.0, 13.0, 10.0))
        assertClose(m1 * Vector3(2.0, -2.0, 3.0), Vector3(10.0, 19.0, 20.0))
    }

    @Test
    fun transposed() {
        val m = Matrix3(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
        assertClose(m.transposed(), Matrix3(1.0, 4.0, 7.0, 2.0, 5.0, 8.0, 3.0, 6.0, 9.0))
    }

    @Test
    fun fromColumnVectors() {
        val m =
            Matrix3.fromVectors(
                Vector3(1.0, 2.0, 3.0),
                Vector3(4.0, 5.0, 6.0),
                Vector3(7.0, 8.0, 9.0),
            )
        assertClose(m, Matrix3(1.0, 4.0, 7.0, 2.0, 5.0, 8.0, 3.0, 6.0, 9.0))
    }

    @Test
    fun fromRowVectors() {
        val m =
            Matrix3.fromVectors(
                Vector3(1.0, 2.0, 3.0),
                Vector3(4.0, 5.0, 6.0),
                Vector3(7.0, 8.0, 9.0),
                columnVectors = false,
            )
        assertClose(m, Matrix3(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
    }
}
