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
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.Rgba
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LineDrawerTest {
    @Test
    fun `segments cover the buffer contiguously`() {
        val lines =
            listOf(
                LinePrimitive(
                    listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
                    Rgba.WHITE,
                    widthDp = 1.5,
                ),
                LinePrimitive(
                    listOf(Vector3(0.0, 0.0, 1.0), Vector3(0.0, 1.0, 0.0)),
                    Rgba.BLACK,
                    widthDp = 2.5,
                ),
            )
        val buffers = LineDrawer.build(lines, density = 2f)
        assertThat(buffers.segments).hasSize(2)
        assertThat(buffers.segments[0].offset).isEqualTo(0)
        assertThat(buffers.segments[1].offset).isEqualTo(buffers.segments[0].count)
        assertThat(buffers.segments.sumOf { it.count }).isEqualTo(buffers.vertexCount)
    }

    @Test
    fun `width is resolved to pixels at build time`() {
        val line =
            LinePrimitive(
                listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
                Rgba.WHITE,
                widthDp = 2.5,
            )
        val buffers = LineDrawer.build(listOf(line), density = 3f)
        assertThat(buffers.segments.single().widthPx).isWithin(1e-5f).of(7.5f)
    }

    @Test
    fun `a quarter turn is subdivided rather than cutting a chord`() {
        val line =
            LinePrimitive(
                listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
                Rgba.WHITE,
                widthDp = 1.0,
            )
        // 90 degrees at a 5-degree maximum segment: many more than the two vertices given.
        assertThat(LineDrawer.build(listOf(line), 1f).vertexCount).isAtLeast(19)
    }

    @Test
    fun `colour is carried, not pre-transformed for night mode`() {
        // Night mode is a shader uniform here, so unlike GLES1 there is no second baked colour
        // and a night-mode toggle does not invalidate the buffer.
        val color = Rgba(0.3f, 0.6f, 0.9f, 0.5f)
        val line =
            LinePrimitive(
                listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
                color,
                widthDp = 1.0,
            )
        assertThat(LineDrawer.build(listOf(line), 1f).segments.single().color).isEqualTo(color)
    }

    @Test
    fun `an empty scene builds nothing`() {
        val buffers = LineDrawer.build(emptyList(), 1f)
        assertThat(buffers.segments).isEmpty()
        assertThat(buffers.vertexCount).isEqualTo(0)
    }
}
