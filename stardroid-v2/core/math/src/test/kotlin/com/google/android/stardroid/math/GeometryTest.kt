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
import kotlin.math.cos

private const val TOL = 1e-9

/** Ported from v1 `GeometryTest`. */
class GeometryTest {
    @Test
    fun zeroRotationIsIdentity() {
        assertClose(rotationMatrix(0.0, Vector3(1.0, 2.0, 3.0).normalized()), Matrix3.IDENTITY)
    }

    @Test
    fun rotationAboutZ() {
        assertClose(
            rotationMatrix(90.0, Vector3.UNIT_Z),
            Matrix3(0.0, 1.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0),
        )
    }

    @Test
    fun rotationThenInverseRotationIsIdentity() {
        val axis = Vector3(2.0, -4.0, 1.0).normalized()
        assertClose(rotationMatrix(30.0, axis) * rotationMatrix(-30.0, axis), Matrix3.IDENTITY)
    }

    @Test
    fun rotationPreservesAnglesAndPerpendicularity() {
        val axis = Vector3(2.0, -4.0, 1.0).normalized()
        val rot = rotationMatrix(30.0, axis)
        val perpendicular = Vector3(4.0, 2.0, 0.0)
        val rotated = rot * perpendicular
        // A vector perpendicular to the axis stays perpendicular.
        assertThat(axis dot rotated).isWithin(TOL).of(0.0)
        // ...and is rotated by exactly 30 degrees.
        assertThat(perpendicular.normalized() dot rotated.normalized())
            .isWithin(TOL).of(cos(30.0 * DEGREES_TO_RADIANS))
    }

    @Test
    fun angularSeparationDeg_basics() {
        assertThat(angularSeparationDeg(RaDec(0.0, 0.0), RaDec(90.0, 0.0))).isWithin(1e-7).of(90.0)
        assertThat(angularSeparationDeg(RaDec(10.0, 20.0), RaDec(10.0, 20.0)))
            .isWithin(1e-7).of(0.0)
        assertThat(angularSeparationDeg(RaDec(0.0, -90.0), RaDec(0.0, 90.0)))
            .isWithin(1e-7).of(180.0)
    }
}
