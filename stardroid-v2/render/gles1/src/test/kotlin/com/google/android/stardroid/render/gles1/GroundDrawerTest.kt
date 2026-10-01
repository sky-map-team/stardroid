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
import com.google.android.stardroid.render.api.Ground
import com.google.android.stardroid.render.api.GroundRamp
import com.google.android.stardroid.render.api.Rgba
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Covers the pure half of `GroundDrawer` — `build` produces client-side buffers, so everything up
 * to the `draw` call is testable without GL. Replaces the coverage lost when `GlowDrawerTest` went
 * with the glow it tested.
 */
class GroundDrawerTest {
    private val ground =
        Ground(
            nightColor = Rgba(0.2f, 0.4f, 0.1f, 1f),
            dayColor = Rgba(0.6f, 0.7f, 0.5f, 1f),
            opacity = 0.55,
        )
    private val zenith = Vector3(0.0, 0.0, 1.0)
    private val sunUp = Vector3(1.0, 0.0, 1.0)
    private val sunDown = Vector3(1.0, 0.0, -1.0)

    private fun ringAlphas(buffers: GroundBuffers): List<Float> {
        val colors = buffers.colors.duplicate()
        colors.rewind()
        val perRing = colors.remaining() / 4 / GroundDrawer.RING_ALTITUDES.size
        return GroundDrawer.RING_ALTITUDES.indices.map { ring ->
            colors.get((ring * perRing) * 4 + 3)
        }
    }

    @Test
    fun `zero opacity draws nothing at all`() {
        val buffers = GroundDrawer.build(ground.copy(opacity = 0.0), sunUp, zenith)
        assertThat(buffers.indexCount).isEqualTo(0)
    }

    @Test
    fun `ring alphas sample the shared ramp, so the two backends agree on the curve`() {
        // The whole point of GroundRamp being pure: GLES1 samples it per ring where GLES3
        // evaluates it per pixel. If these drift, the backends disagree about what the ground is.
        val buffers = GroundDrawer.build(ground, sunUp, zenith)
        val alphas = ringAlphas(buffers)
        for ((i, altitude) in GroundDrawer.RING_ALTITUDES.withIndex()) {
            val expected = GroundRamp.alpha(altitude, ground.opacity).toFloat()
            assertThat(abs(alphas[i] - expected)).isLessThan(1e-5f)
        }
    }

    @Test
    fun `the mesh straddles the horizon, so its edge is not a step to half opacity`() {
        // The ramp is symmetric about altitude zero, so the mesh has to be too. Getting this
        // wrong produced a hard step to half opacity at the horizon followed by a one-sided
        // ramp -- invisible under the horizon line, but wrong, and the comment claimed otherwise.
        val altitudes = GroundDrawer.RING_ALTITUDES
        assertThat(altitudes[0]).isEqualTo(GroundRamp.EDGE_RAMP_DEG)
        assertThat(altitudes[1]).isEqualTo(0.0)
        assertThat(altitudes[2]).isEqualTo(-GroundRamp.EDGE_RAMP_DEG)

        val alphas = ringAlphas(GroundDrawer.build(ground, sunUp, zenith))
        assertThat(alphas[0]).isEqualTo(0f)
        assertThat(alphas[1]).isGreaterThan(0f)
        assertThat(alphas[2]).isGreaterThan(alphas[1])
    }

    @Test
    fun `alpha does not depend on the sun, only the colour does`() {
        val day = ringAlphas(GroundDrawer.build(ground, sunUp, zenith))
        val night = ringAlphas(GroundDrawer.build(ground, sunDown, zenith))
        assertThat(day).isEqualTo(night)
    }

    @Test
    fun `the colour is the night colour with the sun down and the day colour with it up`() {
        fun firstRgb(buffers: GroundBuffers): Triple<Float, Float, Float> {
            val c = buffers.colors.duplicate()
            c.rewind()
            return Triple(c.get(0), c.get(1), c.get(2))
        }
        val night = firstRgb(GroundDrawer.build(ground, sunDown, zenith))
        assertThat(night.first).isWithin(1e-5f).of(ground.nightColor.r)
        assertThat(night.second).isWithin(1e-5f).of(ground.nightColor.g)

        val day = firstRgb(GroundDrawer.build(ground, sunUp, zenith))
        assertThat(day.first).isWithin(1e-5f).of(ground.dayColor.r)
        assertThat(day.second).isWithin(1e-5f).of(ground.dayColor.g)
    }

    @Test
    fun `the shell is built from any zenith, needing no frame from the producer`() {
        // A full circle is the same set of points whichever perpendicular basis traces it, which
        // is why the ground needs nothing SkyGradient was not already carrying.
        for (up in listOf(Vector3(0.0, 0.0, 1.0), Vector3(1.0, 0.0, 0.0), Vector3(0.3, -0.7, 0.6))) {
            val buffers = GroundDrawer.build(ground, sunUp, up)
            assertThat(buffers.indexCount).isGreaterThan(0)
            val verts = buffers.vertices.duplicate()
            verts.rewind()
            // Every vertex is on the unit sphere.
            while (verts.remaining() >= 3) {
                val x = verts.get()
                val y = verts.get()
                val z = verts.get()
                assertThat(abs(kotlin.math.sqrt(x * x + y * y + z * z) - 1f)).isLessThan(1e-4f)
            }
        }
    }
}
