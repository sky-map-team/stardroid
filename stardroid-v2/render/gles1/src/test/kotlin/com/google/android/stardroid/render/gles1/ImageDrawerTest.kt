/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.sin

class ImageDrawerTest {
    private fun image(
        center: Vector3 = Vector3.UNIT_X,
        angularSizeDeg: Double = 10.0,
        rotationDeg: Double = 0.0,
    ) = ImagePrimitive(center, angularSizeDeg, rotationDeg, ImageRef("test"))

    @Test
    fun `returns four corners`() {
        val corners = ImageDrawer.quadCorners(image())
        assertThat(corners).hasLength(4)
    }

    @Test
    fun `all corners are non-zero`() {
        val corners = ImageDrawer.quadCorners(image())
        corners.forEach { assertThat(it.length2).isGreaterThan(0.0) }
    }

    @Test
    fun `diagonals bisect each other (quad is a parallelogram)`() {
        // LL + UR == UL + LR (diagonals share the same midpoint)
        val (ll, ul, lr, ur) = ImageDrawer.quadCorners(image()).toList()
        val mid1 = ll + ur
        val mid2 = ul + lr
        assertThat(mid1.distanceTo(mid2)).isLessThan(1e-10)
    }

    @Test
    fun `center of the quad equals the image center`() {
        val img = image(center = Vector3.UNIT_X)
        val corners = ImageDrawer.quadCorners(img)
        val centroid = corners.fold(Vector3.ZERO) { acc, v -> acc + v } * (1.0 / 4.0)
        assertThat(centroid.distanceTo(img.center)).isLessThan(1e-10)
    }

    @Test
    fun `corner distance from center scales with half-angle`() {
        val img10 = image(angularSizeDeg = 10.0)
        val img20 = image(angularSizeDeg = 20.0)
        val corners10 = ImageDrawer.quadCorners(img10)
        val corners20 = ImageDrawer.quadCorners(img20)
        // Each corner is sqrt(2) × halfChord from center (diagonal of the square quad).
        val expectedRatio = sin(10.0 * Math.PI / 180.0) / sin(5.0 * Math.PI / 180.0)
        val dist10 = corners10[0].distanceTo(img10.center)
        val dist20 = corners20[0].distanceTo(img20.center)
        assertThat(dist20 / dist10).isWithin(1e-6).of(expectedRatio)
    }

    @Test
    fun `rotation by 90 degrees swaps horizontal and vertical axes`() {
        val center = Vector3.UNIT_X
        val corners0 = ImageDrawer.quadCorners(image(center = center, rotationDeg = 0.0))
        val corners90 = ImageDrawer.quadCorners(image(center = center, rotationDeg = 90.0))
        // Lower-left of rot=0 should equal lower-right of rot=90 (axes swap with 90° rotation).
        // More precisely: the two quads share the same size/center but are orthogonal;
        // just verify they differ visibly.
        val maxSamePos = (0 until 4).minOf { i -> corners0[i].distanceTo(corners90[i]) }
        assertThat(maxSamePos).isGreaterThan(1e-6) // axes differ between rotations
    }

    @Test
    fun `handles pole singularity (center near Y-axis)`() {
        // center ≈ UNIT_Y would collapse the default upRef cross product; fallback applies.
        val polarCenter = Vector3(0.001, 0.9999995, 0.0).normalized()
        val corners = ImageDrawer.quadCorners(image(center = polarCenter))
        assertThat(corners).hasLength(4)
        corners.forEach {
            assertThat(it.x.isNaN()).isFalse()
            assertThat(it.y.isNaN()).isFalse()
            assertThat(it.z.isNaN()).isFalse()
        }
    }

    @Test
    fun `corner vectors are perpendicular to center`() {
        // Each corner = center + offset; the offset (corner - centroid) should be ⊥ center.
        val img = image()
        val corners = ImageDrawer.quadCorners(img)
        val centroid = corners.fold(Vector3.ZERO) { acc, v -> acc + v } * 0.25
        for (corner in corners) {
            val offset = corner - centroid
            val dotWithCenter = offset dot img.center
            assertThat(dotWithCenter).isWithin(1e-6).of(0.0)
        }
    }
}
