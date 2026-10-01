/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sin

class ImageDrawerTest {
    private fun image(
        center: Vector3,
        angularSizeDeg: Double = 10.0,
        rotationDeg: Double = 0.0,
    ) = ImagePrimitive(
        center = center,
        angularSizeDeg = angularSizeDeg,
        rotationDeg = rotationDeg,
        image = ImageRef("planet/mars"),
    )

    @Test
    fun `half axes are perpendicular to each other and to the centre`() {
        val axes = ImageDrawer.halfAxes(image(Vector3(1.0, 0.0, 0.0)))
        assertThat(abs(axes.u dot axes.v)).isLessThan(1e-9)
        assertThat(abs(axes.u dot Vector3(1.0, 0.0, 0.0))).isLessThan(1e-9)
        assertThat(abs(axes.v dot Vector3(1.0, 0.0, 0.0))).isLessThan(1e-9)
    }

    @Test
    fun `half axis length is the chord half-length for the angular size`() {
        val axes = ImageDrawer.halfAxes(image(Vector3(1.0, 0.0, 0.0), angularSizeDeg = 10.0))
        val expected = sin(Math.toRadians(5.0))
        assertThat(axes.u.length).isWithin(1e-9).of(expected)
        assertThat(axes.v.length).isWithin(1e-9).of(expected)
    }

    @Test
    fun `rotation turns the frame without changing its scale`() {
        val unrotated = ImageDrawer.halfAxes(image(Vector3(1.0, 0.0, 0.0)))
        val rotated =
            ImageDrawer.halfAxes(image(Vector3(1.0, 0.0, 0.0), rotationDeg = 90.0))
        assertThat(rotated.u.length).isWithin(1e-9).of(unrotated.u.length)
        // A quarter turn carries u onto v.
        assertThat(rotated.u.x).isWithin(1e-9).of(unrotated.v.x)
        assertThat(rotated.u.y).isWithin(1e-9).of(unrotated.v.y)
        assertThat(rotated.u.z).isWithin(1e-9).of(unrotated.v.z)
    }

    @Test
    fun `a centre near the Y pole falls back to a usable frame`() {
        // Within 8 degrees of +Y the cross product with WORLD_UP degenerates (D30); the axes
        // must still come out finite, unit-scaled and perpendicular rather than collapsing.
        val nearPole = Vector3(0.02, 0.9998, 0.0).normalized()
        val axes = ImageDrawer.halfAxes(image(nearPole))
        assertThat(axes.u.length).isGreaterThan(0.0)
        assertThat(abs(axes.u dot axes.v)).isLessThan(1e-9)
        assertThat(abs(axes.u dot nearPole)).isLessThan(1e-9)
    }

    @Test
    fun `the drawn size overrides the primitive's true size`() {
        // The floored size (D86) is what the disc is actually drawn at, which is why the size
        // floor has to be applied at draw time rather than baked by the producer.
        val axes = ImageDrawer.halfAxes(image(Vector3(1.0, 0.0, 0.0), 0.5), drawnDiameterDeg = 4.0)
        assertThat(axes.u.length).isWithin(1e-9).of(sin(Math.toRadians(2.0)))
    }
}
