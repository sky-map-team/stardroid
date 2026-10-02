/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.testing.assertThat
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test

class ImageQuadTest {
    private fun image(
        center: Vector3 = Vector3.UNIT_X,
        angularSizeDeg: Double = 10.0,
        rotationDeg: Double = 0.0,
        minSizeDp: Double = 0.0,
        visibleBelowFovDeg: Double? = null,
    ) = ImagePrimitive(
        center = center,
        angularSizeDeg = angularSizeDeg,
        rotationDeg = rotationDeg,
        image = ImageRef("planet/mars"),
        minSizeDp = minSizeDp,
        visibleBelowFovDeg = visibleBelowFovDeg,
    )

    private val camera = SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, fovDeg = 40.0)
    private val viewport = Viewport(400, 800, density = 2f)

    // ---- halfAxes (as :render:gles3's ImageDrawer, which it was transcribed from) ----------

    @Test
    fun `half axes are perpendicular to each other and to the centre`() {
        val axes = ImageQuad.halfAxes(image())
        assertThat(abs(axes.u dot axes.v)).isLessThan(1e-9)
        assertThat(abs(axes.u dot Vector3.UNIT_X)).isLessThan(1e-9)
        assertThat(abs(axes.v dot Vector3.UNIT_X)).isLessThan(1e-9)
    }

    @Test
    fun `half axis length is the chord half-length for the angular size`() {
        val axes = ImageQuad.halfAxes(image(angularSizeDeg = 10.0))
        val expected = sin(5.0 * DEGREES_TO_RADIANS)
        assertThat(axes.u.length).isWithin(1e-9).of(expected)
        assertThat(axes.v.length).isWithin(1e-9).of(expected)
    }

    @Test
    fun `rotation turns the frame without changing its scale`() {
        val unrotated = ImageQuad.halfAxes(image())
        val rotated = ImageQuad.halfAxes(image(rotationDeg = 90.0))
        assertThat(rotated.u.length).isWithin(1e-9).of(unrotated.u.length)
        // A quarter turn carries u onto v.
        assertThat(rotated.u.x).isWithin(1e-9).of(unrotated.v.x)
        assertThat(rotated.u.y).isWithin(1e-9).of(unrotated.v.y)
        assertThat(rotated.u.z).isWithin(1e-9).of(unrotated.v.z)
    }

    @Test
    fun `a centre near the Y pole falls back to a usable frame`() {
        val nearPole = Vector3(0.02, 0.9998, 0.0).normalized()
        val axes = ImageQuad.halfAxes(image(center = nearPole))
        assertThat(axes.u.length).isGreaterThan(0.0)
        assertThat(abs(axes.u dot axes.v)).isLessThan(1e-9)
        assertThat(abs(axes.u dot nearPole)).isLessThan(1e-9)
    }

    @Test
    fun `the drawn size overrides the primitive's true size`() {
        val axes = ImageQuad.halfAxes(image(angularSizeDeg = 0.5), drawnDiameterDeg = 4.0)
        assertThat(axes.u.length).isWithin(1e-9).of(sin(2.0 * DEGREES_TO_RADIANS))
    }

    // ---- drawnDiameterDeg -------------------------------------------------------------------

    @Test
    fun `an image is drawn at its true size when that exceeds the floor`() {
        assertThat(ImageQuad.drawnDiameterDeg(image(angularSizeDeg = 10.0), camera, viewport))
            .isEqualTo(10.0)
    }

    @Test
    fun `a tiny image is floored to its minimum size in dp`() {
        // 20 dp at density 2 is 40 px of a 400 px short side spanning 40°: 4°.
        val drawn =
            ImageQuad.drawnDiameterDeg(
                image(angularSizeDeg = 0.01, minSizeDp = 20.0),
                camera,
                viewport,
            )
        assertThat(drawn).isNotNull()
        assertThat(drawn!!).isWithin(1e-9).of(4.0)
    }

    @Test
    fun `an image above its visible field of view is not drawn`() {
        assertThat(ImageQuad.drawnDiameterDeg(image(visibleBelowFovDeg = 30.0), camera, viewport))
            .isNull()
        assertThat(ImageQuad.drawnDiameterDeg(image(visibleBelowFovDeg = 50.0), camera, viewport))
            .isNotNull()
    }

    // ---- texture-space vectors --------------------------------------------------------------

    @Test
    fun `the lit limb faces north at position angle 0 and east - minus x - at 90`() {
        val north = ImageQuad.litDirection(Terminator(0.5, brightLimbAngleDeg = 0.0))
        assertThat(north.x).isWithin(1e-12).of(0.0)
        assertThat(north.y).isWithin(1e-12).of(1.0)
        val east = ImageQuad.litDirection(Terminator(0.5, brightLimbAngleDeg = 90.0))
        assertThat(east.x).isWithin(1e-12).of(-1.0)
        assertThat(east.y).isWithin(1e-12).of(0.0)
    }

    @Test
    fun `the shadow centre lies along its position angle at its offset`() {
        val center =
            ImageQuad.shadowCenter(
                EclipseShadow(
                    umbraRadius = 1.0,
                    penumbraRadius = 2.0,
                    offset = 0.5,
                    directionDeg = 270.0,
                ),
            )
        assertThat(center.x).isWithin(1e-12).of(0.5)
        assertThat(center.y).isWithin(1e-12).of(0.0)
    }
}
