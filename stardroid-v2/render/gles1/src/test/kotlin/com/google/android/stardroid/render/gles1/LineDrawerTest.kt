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
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.Rgba
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LineDrawerTest {
    @Test
    fun `each primitive becomes one segment with its own subdivided vertex range`() {
        val lines =
            listOf(
                LinePrimitive(listOf(Vector3.UNIT_X, Vector3.UNIT_Y), Rgba.WHITE, widthDp = 2.0),
                LinePrimitive(listOf(Vector3.UNIT_Y, Vector3.UNIT_Z), Rgba.WHITE, widthDp = 2.0),
            )
        val buffers = LineDrawer.build(lines, density = 1f)

        assertThat(buffers.segments).hasSize(2)
        val first = buffers.segments[0]
        val second = buffers.segments[1]
        assertThat(first.offset).isEqualTo(0)
        assertThat(second.offset).isEqualTo(first.count)
        assertThat(buffers.vertices.remaining()).isEqualTo((first.count + second.count) * 3)
    }

    @Test
    fun `width in pixels scales with density`() {
        val line = LinePrimitive(listOf(Vector3.UNIT_X, Vector3.UNIT_Y), Rgba.WHITE, widthDp = 2.0)
        val at1x = LineDrawer.build(listOf(line), density = 1f).segments.single()
        val at2x = LineDrawer.build(listOf(line), density = 2f).segments.single()
        assertThat(at2x.widthPx).isEqualTo(at1x.widthPx * 2f)
    }

    @Test
    fun `segments keep the producer color — night mode is applied at draw time`() {
        val color = Rgba(0.2f, 0.4f, 0.6f)
        val line = LinePrimitive(listOf(Vector3.UNIT_X, Vector3.UNIT_Y), color, widthDp = 1.0)
        val segment = LineDrawer.build(listOf(line), density = 1f).segments.single()
        // The night-mode red transform itself is covered by StellarStylerTest.
        assertThat(segment.color).isEqualTo(color)
    }

    @Test
    fun `empty scene produces no segments`() {
        val buffers = LineDrawer.build(emptyList(), density = 1f)
        assertThat(buffers.segments).isEmpty()
    }
}
