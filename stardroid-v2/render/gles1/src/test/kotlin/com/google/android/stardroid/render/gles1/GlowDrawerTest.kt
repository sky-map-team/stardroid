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
import com.google.android.stardroid.render.api.GlowPrimitive
import com.google.android.stardroid.render.api.GlowRing
import com.google.android.stardroid.render.api.Rgba
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GlowDrawerTest {
    private val white = Rgba.WHITE
    private val faint = Rgba(1f, 1f, 1f, 0.25f)

    /** A [rings]×[length] mesh of distinct vertices. */
    private fun glow(
        rings: Int,
        length: Int,
        color: (Int) -> Rgba = { white },
    ): GlowPrimitive =
        GlowPrimitive(
            (0 until rings).map { r ->
                GlowRing(
                    (0 until length).map { i -> Vector3(r.toDouble(), i.toDouble(), 1.0) },
                    color(r),
                )
            },
        )

    @Test
    fun `two rings of three vertices become two quads of two triangles`() {
        val buffers = GlowDrawer.build(listOf(glow(rings = 2, length = 3)))

        // 6 vertices, (2-1)*(3-1)=2 quads, 12 indices.
        assertThat(buffers.vertices.remaining()).isEqualTo(6 * 3)
        assertThat(buffers.colors.remaining()).isEqualTo(6 * 4)
        assertThat(buffers.nightColors.remaining()).isEqualTo(6 * 4)
        assertThat(buffers.indexCount).isEqualTo(12)

        // First quad: top row starts at 0, bottom row at ringLength=3.
        val indices = ShortArray(12).also { buffers.indices.get(it) }
        assertThat(indices.toList().take(6)).containsExactly(
            0.toShort(),
            3.toShort(),
            4.toShort(),
            0.toShort(),
            4.toShort(),
            1.toShort(),
        ).inOrder()
    }

    @Test
    fun `ring colors repeat per vertex and bake a night variant`() {
        val buffers =
            GlowDrawer.build(
                listOf(glow(rings = 2, length = 2, color = { r -> if (r == 0) white else faint })),
            )
        val colors = FloatArray(4 * 4).also { buffers.colors.get(it) }
        // Ring 0 vertices are opaque white, ring 1 vertices carry the faint alpha.
        assertThat(colors[3]).isEqualTo(1f)
        assertThat(colors[7]).isEqualTo(1f)
        assertThat(colors[11]).isEqualTo(0.25f)
        assertThat(colors[15]).isEqualTo(0.25f)

        val night = FloatArray(4 * 4).also { buffers.nightColors.get(it) }
        // The night transform zeroes green and blue (StellarStylerTest covers the luminance).
        assertThat(night[1]).isEqualTo(0f)
        assertThat(night[2]).isEqualTo(0f)
        assertThat(night[11]).isEqualTo(0.25f)
    }

    @Test
    fun `multiple primitives share the buffers with rebased indices`() {
        val buffers = GlowDrawer.build(listOf(glow(2, 2), glow(2, 2)))
        assertThat(buffers.vertices.remaining()).isEqualTo(8 * 3)
        assertThat(buffers.indexCount).isEqualTo(12)
        val indices = ShortArray(12).also { buffers.indices.get(it) }
        // The second primitive's first quad starts at its own base vertex, 4.
        assertThat(indices.toList().drop(6)).containsExactly(
            4.toShort(),
            6.toShort(),
            7.toShort(),
            4.toShort(),
            7.toShort(),
            5.toShort(),
        ).inOrder()
    }

    @Test
    fun `degenerate meshes are skipped`() {
        // One ring, and rings of one vertex: neither can form a band of quads.
        val oneRing = glow(rings = 1, length = 5)
        val thinRings = glow(rings = 3, length = 1)
        val buffers = GlowDrawer.build(listOf(oneRing, thinRings))
        assertThat(buffers.indexCount).isEqualTo(0)
    }

    @Test
    fun `meshes overflowing the unsigned-short index range are dropped`() {
        // 2 rings × 35 000 vertices = 70 000 vertices > 65536.
        val buffers = GlowDrawer.build(listOf(glow(rings = 2, length = 35_000)))
        assertThat(buffers.indexCount).isEqualTo(0)
        assertThat(buffers.vertices.remaining()).isEqualTo(0)
    }

    @Test
    fun `empty scene produces no indices`() {
        assertThat(GlowDrawer.build(emptyList()).indexCount).isEqualTo(0)
    }
}
