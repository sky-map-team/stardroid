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
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class SkyGradientDrawerTest {
    private val bands = 8
    private val stepsInBand = 10
    private val numVertices = bands * stepsInBand

    @Test
    fun `dome has v1 SkyBox's vertex and index counts`() {
        val (positions, colors) = SkyGradientDrawer.buildGeometry()
        val indices = SkyGradientDrawer.buildIndices()
        assertThat(positions.size).isEqualTo(numVertices * 3)
        assertThat(colors.size).isEqualTo(numVertices * 4)
        assertThat(indices.size).isEqualTo((bands - 1) * stepsInBand * 6)
    }

    @Test
    fun `every index points at a real vertex`() {
        val indices = SkyGradientDrawer.buildIndices()
        for (index in indices) {
            assertThat(index.toInt()).isAtLeast(0)
            assertThat(index.toInt()).isLessThan(numVertices)
        }
    }

    @Test
    fun `first band sits at the sun pole with the brightest blue, v1 intensity 70`() {
        val (positions, colors) = SkyGradientDrawer.buildGeometry()
        for (i in 0 until stepsInBand) {
            assertThat(positions[i * 3].toDouble()).isWithin(TOL).of(0.0)
            assertThat(positions[i * 3 + 1].toDouble()).isWithin(TOL).of(1.0)
            assertThat(positions[i * 3 + 2].toDouble()).isWithin(TOL).of(0.0)
            // v1 packed ABGR (intensity << 16 | 0xff000000): blue = 70/255, red = green = 0.
            assertThat(colors[i * 4].toDouble()).isWithin(TOL).of(0.0)
            assertThat(colors[i * 4 + 1].toDouble()).isWithin(TOL).of(0.0)
            assertThat(colors[i * 4 + 2].toDouble()).isWithin(TOL).of(70 / 255.0)
            assertThat(colors[i * 4 + 3].toDouble()).isWithin(TOL).of(1.0)
        }
    }

    @Test
    fun `bands facing the sun are blue, bands beyond ninety degrees fade grey to black`() {
        val (positions, colors) = SkyGradientDrawer.buildGeometry()
        for (v in 0 until numVertices) {
            val y = positions[v * 3 + 1]
            val r = colors[v * 4]
            val g = colors[v * 4 + 1]
            val b = colors[v * 4 + 2]
            if (y > 0) {
                assertThat(r).isEqualTo(0f)
                assertThat(g).isEqualTo(0f)
                assertThat(b.toDouble()).isAtLeast(50 / 255.0 - TOL)
                assertThat(b.toDouble()).isAtMost(70 / 255.0 + TOL)
            } else {
                // Greyscale, never blacker than black (the epsilon overshoot clamps at 0).
                assertThat(r).isEqualTo(g)
                assertThat(g).isEqualTo(b)
                assertThat(r.toDouble()).isAtLeast(0.0)
                assertThat(r.toDouble()).isAtMost(40 / 255.0 + TOL)
            }
        }
    }

    @Test
    fun `vertices lie on the unit sphere`() {
        val (positions, _) = SkyGradientDrawer.buildGeometry()
        for (v in 0 until numVertices) {
            val x = positions[v * 3].toDouble()
            val y = positions[v * 3 + 1].toDouble()
            val z = positions[v * 3 + 2].toDouble()
            // The last band's y overshoots -1 by v1's epsilon guard (its ring collapses to a
            // point); everything else is unit-length.
            assertThat(sqrt(x * x + y * y + z * z)).isWithin(0.01).of(1.0)
        }
    }

    @Test
    fun `rotation carries the dome pole onto the sun`() {
        // Sun along +x: rotating +y about the returned axis by the returned angle lands on +x.
        val (angleDeg, axis) = SkyGradientDrawer.rotationToSun(Vector3.UNIT_X)
        assertThat(angleDeg.toDouble()).isWithin(TOL).of(90.0)
        val rotated = rodrigues(Vector3.UNIT_Y, axis, angleDeg.toDouble())
        assertThat(rotated.distanceTo(Vector3.UNIT_X)).isLessThan(TOL)
    }

    @Test
    fun `rotation degenerates safely when the sun is along the dome axis`() {
        val (angleUp, axisUp) = SkyGradientDrawer.rotationToSun(Vector3.UNIT_Y)
        assertThat(angleUp.toDouble()).isWithin(TOL).of(0.0)
        assertThat(axisUp.length).isGreaterThan(0.0)

        val (angleDown, axisDown) = SkyGradientDrawer.rotationToSun(-Vector3.UNIT_Y)
        assertThat(angleDown.toDouble()).isWithin(TOL).of(180.0)
        assertThat(axisDown.length).isGreaterThan(0.0)
        val rotated = rodrigues(Vector3.UNIT_Y, axisDown, angleDown.toDouble())
        assertThat(rotated.distanceTo(-Vector3.UNIT_Y)).isLessThan(TOL)
    }

    /** Rodrigues' rotation of [v] about unit [axis] by [angleDeg] — an independent check. */
    private fun rodrigues(
        v: Vector3,
        axis: Vector3,
        angleDeg: Double,
    ): Vector3 {
        val theta = Math.toRadians(angleDeg)
        val k = axis.normalized()
        return v * kotlin.math.cos(theta) +
            (k cross v) * kotlin.math.sin(theta) +
            k * ((k dot v) * (1 - kotlin.math.cos(theta)))
    }

    companion object {
        private const val TOL = 1e-5
    }
}
