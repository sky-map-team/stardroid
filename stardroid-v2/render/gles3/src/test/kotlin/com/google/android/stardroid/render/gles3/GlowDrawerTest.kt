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
import com.google.android.stardroid.render.api.GlowPrimitive
import com.google.android.stardroid.render.api.GlowRing
import com.google.android.stardroid.render.api.Rgba
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GlowDrawerTest {
    private fun ring(
        count: Int,
        color: Rgba,
    ) = GlowRing(List(count) { Vector3(1.0, it.toDouble(), 0.0).normalized() }, color)

    @Test
    fun `bands between consecutive rings become two triangles each`() {
        val glow = GlowPrimitive(listOf(ring(4, Rgba.WHITE), ring(4, Rgba.BLACK)))
        val buffers = GlowDrawer.build(listOf(glow))
        assertThat(buffers.vertexCount).isEqualTo(8)
        // Three quads between four-vertex rings, six indices each.
        assertThat(buffers.indexCount).isEqualTo(18)
    }

    @Test
    fun `each ring's colour is written to its own vertices`() {
        val outer = Rgba(1f, 0f, 0f, 0.5f)
        val inner = Rgba(0f, 0f, 1f, 0.25f)
        val glow = GlowPrimitive(listOf(ring(3, outer), ring(3, inner)))
        val buffers = GlowDrawer.build(listOf(glow))
        val stride = GlowDrawer.FLOATS_PER_VERTEX
        buffers.data.position(3)
        assertThat(buffers.data.get()).isEqualTo(outer.r)
        // The first vertex of the second ring starts three vertices in.
        buffers.data.position(3 * stride + 3)
        assertThat(buffers.data.get()).isEqualTo(inner.r)
        assertThat(buffers.data.get()).isEqualTo(inner.g)
        assertThat(buffers.data.get()).isEqualTo(inner.b)
        assertThat(buffers.data.get()).isEqualTo(inner.a)
    }

    @Test
    fun `a glow with too few rings or vertices draws nothing`() {
        assertThat(GlowDrawer.build(listOf(GlowPrimitive(listOf(ring(4, Rgba.WHITE))))).indexCount)
            .isEqualTo(0)
        val thinRings = GlowPrimitive(listOf(ring(1, Rgba.WHITE), ring(1, Rgba.BLACK)))
        assertThat(GlowDrawer.build(listOf(thinRings)).indexCount).isEqualTo(0)
    }

    @Test
    fun `an empty scene builds nothing`() {
        assertThat(GlowDrawer.build(emptyList()).indexCount).isEqualTo(0)
    }
}
