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
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.StellarRamps
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PointDrawerTest {
    private fun vertex(
        buffers: PointBuffers,
        index: Int,
    ): FloatArray {
        val out = FloatArray(PointDrawer.FLOATS_PER_VERTEX)
        buffers.data.position(index * PointDrawer.FLOATS_PER_VERTEX)
        buffers.data.get(out)
        buffers.data.position(0)
        return out
    }

    @Test
    fun `a stellar point bakes tint, shade and size but not the magnitude limit`() {
        val point =
            PointPrimitive(
                Vector3(1.0, 0.0, 0.0),
                PointAppearance.Stellar(magnitude = 2.0, colorIndex = 0.0),
            )
        val v = vertex(PointDrawer.build(listOf(point)), 0)

        val shade = StellarRamps.magnitudeShade(2.0)
        val tint = StellarRamps.colorForIndex(0.0)
        assertThat(v[0]).isEqualTo(1f)
        assertThat(v[3]).isWithin(1e-5f).of(tint.r * shade)
        assertThat(v[4]).isWithin(1e-5f).of(tint.g * shade)
        assertThat(v[5]).isWithin(1e-5f).of(tint.b * shade)
        // Alpha is left at 1: the magnitude-limit fade is a shader uniform, which is exactly why
        // changing the limit does not invalidate this buffer the way it does on GLES1.
        assertThat(v[6]).isEqualTo(1f)
        assertThat(v[7]).isEqualTo(StellarRamps.sizeDp(2.0))
        assertThat(v[8]).isEqualTo(2f)
    }

    @Test
    fun `a fixed point keeps its own colour and size`() {
        val color = Rgba(0.2f, 0.4f, 0.6f, 0.8f)
        val point =
            PointPrimitive(
                Vector3(0.0, 1.0, 0.0),
                PointAppearance.Fixed(color = color, sizeDp = 7.0),
            )
        val v = vertex(PointDrawer.build(listOf(point)), 0)

        assertThat(v[1]).isEqualTo(1f)
        assertThat(v[3]).isEqualTo(color.r)
        assertThat(v[6]).isEqualTo(color.a)
        assertThat(v[7]).isEqualTo(7f)
    }

    @Test
    fun `a fixed point is never dimmed by the magnitude limit`() {
        val point =
            PointPrimitive(
                Vector3(0.0, 1.0, 0.0),
                PointAppearance.Fixed(color = Rgba.WHITE, sizeDp = 4.0),
            )
        val magnitude = vertex(PointDrawer.build(listOf(point)), 0)[8].toDouble()
        // Whatever limit the state carries, the shader's fade never reaches this magnitude.
        assertThat(StellarRamps.magnitudeAlpha(magnitude, -30.0)).isEqualTo(1f)
    }

    @Test
    fun `icon points are left to the icon drawer`() {
        val points =
            listOf(
                PointPrimitive(
                    Vector3(1.0, 0.0, 0.0),
                    PointAppearance.Icon(ImageRef("icon/galaxy"), sizeDp = 20.0),
                ),
                PointPrimitive(Vector3(0.0, 1.0, 0.0), PointAppearance.Stellar(magnitude = 3.0)),
            )
        assertThat(PointDrawer.build(points).vertexCount).isEqualTo(1)
    }

    @Test
    fun `an empty scene builds an empty buffer`() {
        assertThat(PointDrawer.build(emptyList()).vertexCount).isEqualTo(0)
    }

    @Test
    fun `every point lands in one buffer regardless of size`() {
        // The whole point of the port: GLES1 has to group these into per-size draw calls.
        val points =
            (0..20).map {
                PointPrimitive(
                    Vector3(1.0, 0.0, 0.0),
                    PointAppearance.Stellar(magnitude = it * 0.3),
                )
            }
        assertThat(PointDrawer.build(points).vertexCount).isEqualTo(21)
    }
}
