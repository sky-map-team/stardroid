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
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.RenderState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PointDrawerTest {
    @Test
    fun `points are grouped into contiguous runs by size`() {
        val faint = PointAppearance.Stellar(magnitude = 5.0)
        val bright = PointAppearance.Stellar(magnitude = 0.0)
        val points =
            listOf(
                PointPrimitive(Vector3.UNIT_X, faint),
                PointPrimitive(Vector3.UNIT_Y, bright),
                PointPrimitive(Vector3.UNIT_Z, faint),
            )
        val buffers = PointDrawer.build(points, RenderState(), density = 1f)

        assertThat(buffers.sizeRuns).hasSize(2)
        val totalCount = buffers.sizeRuns.sumOf { it.count }
        assertThat(totalCount).isEqualTo(points.size)
        // runs are contiguous and cover the whole buffer with no gaps or overlap
        val sortedByOffset = buffers.sizeRuns.sortedBy { it.offset }
        assertThat(sortedByOffset.first().offset).isEqualTo(0)
        for (i in 0 until sortedByOffset.size - 1) {
            assertThat(sortedByOffset[i].offset + sortedByOffset[i].count)
                .isEqualTo(sortedByOffset[i + 1].offset)
        }
    }

    @Test
    fun `vertex buffer holds one position triple per point`() {
        val points =
            listOf(
                PointPrimitive(Vector3.UNIT_X, PointAppearance.Stellar(2.0)),
                PointPrimitive(Vector3.UNIT_Y, PointAppearance.Stellar(2.0)),
            )
        val buffers = PointDrawer.build(points, RenderState(), density = 1f)

        assertThat(buffers.vertices.remaining()).isEqualTo(points.size * 3)
        assertThat(buffers.colors.remaining()).isEqualTo(points.size * 4)
    }

    @Test
    fun `icon points are excluded from the dot buffers`() {
        val points =
            listOf(
                PointPrimitive(Vector3.UNIT_X, PointAppearance.Stellar(2.0)),
                PointPrimitive(
                    Vector3.UNIT_Y,
                    PointAppearance.Icon(ImageRef("icon/galaxy"), sizeDp = 20.0),
                ),
            )
        val buffers = PointDrawer.build(points, RenderState(), density = 1f)

        assertThat(buffers.vertices.remaining()).isEqualTo(3)
        assertThat(buffers.sizeRuns.sumOf { it.count }).isEqualTo(1)
    }

    @Test
    fun `empty scene produces no size runs`() {
        val buffers = PointDrawer.build(emptyList(), RenderState(), density = 1f)
        assertThat(buffers.sizeRuns).isEmpty()
    }
}
