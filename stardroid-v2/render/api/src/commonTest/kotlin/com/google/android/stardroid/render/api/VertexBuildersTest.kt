/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

/** [PointVertices], [GlowMesh] and [LineStrips]: the layouts the shaders read, pinned. */
class VertexBuildersTest {
    private val red = Rgba(1f, 0f, 0f, 0.5f)

    @Test
    fun points_bakeStellarStyleAndSkipIcons() {
        val points =
            listOf(
                PointPrimitive(Vector3.UNIT_X, PointAppearance.Stellar(magnitude = 2.0)),
                PointPrimitive(Vector3.UNIT_Y, PointAppearance.Icon(ImageRef("icon"), 12.0)),
                PointPrimitive(Vector3.UNIT_Z, PointAppearance.Fixed(red, sizeDp = 4.0)),
            )
        val built = PointVertices.build(points)
        assertThat(built.vertexCount).isEqualTo(2)
        assertThat(built.data.size).isEqualTo(2 * PointVertices.FLOATS_PER_VERTEX)

        val star = built.data.copyOfRange(0, PointVertices.FLOATS_PER_VERTEX).toList()
        val shade = StellarRamps.magnitudeShade(2.0)
        val tint = StellarRamps.colorForIndex(null)
        assertThat(star)
            .containsExactly(
                1f, 0f, 0f,
                tint.r * shade, tint.g * shade, tint.b * shade, 1f,
                StellarRamps.sizeDp(2.0), 2f,
            ).inOrder()

        val fixed = built.data.copyOfRange(PointVertices.FLOATS_PER_VERTEX, built.data.size)
        assertThat(fixed.toList())
            .containsExactly(0f, 0f, 1f, 1f, 0f, 0f, 0.5f, 4f, PointVertices.NO_MAGNITUDE)
            .inOrder()
    }

    @Test
    fun glow_fillsTheBandsBetweenRings() {
        val rings =
            listOf(
                GlowRing(listOf(Vector3.UNIT_X, Vector3.UNIT_Y, Vector3.UNIT_Z), red),
                GlowRing(listOf(Vector3.UNIT_X, Vector3.UNIT_Y, Vector3.UNIT_Z), Rgba.WHITE),
            )
        val mesh = GlowMesh.build(listOf(GlowPrimitive(rings), GlowPrimitive(rings.take(1))))
        // The single-ring glow is skipped; the other is 2 rings × 3 vertices, 2 quads.
        assertThat(mesh.vertexCount).isEqualTo(6)
        assertThat(mesh.indices.toList())
            .containsExactly(0, 3, 4, 0, 4, 1, 1, 4, 5, 1, 5, 2)
            .inOrder()
        // Ring colour lands on each of the ring's vertices.
        assertThat(mesh.vertices[3 * GlowMesh.FLOATS_PER_VERTEX + 3]).isEqualTo(1f)
        assertThat(mesh.vertices[6]).isEqualTo(0.5f)
    }

    @Test
    fun lines_emitTwoSidesPerVertexWithNeighbours() {
        val a = Vector3.UNIT_X
        val b = Vector3(1.0, 0.01, 0.0).normalized()
        val c = Vector3(1.0, 0.02, 0.0).normalized()
        val strips = LineStrips.build(listOf(LinePrimitive(listOf(a, b, c), red, widthDp = 3.0)))

        assertThat(strips.vertexCount).isEqualTo(6)
        assertThat(strips.indices.toList())
            .containsExactly(0, 1, 2, 1, 3, 2, 2, 3, 4, 3, 5, 4)
            .inOrder()

        fun vertex(i: Int) =
            strips.vertices.copyOfRange(
                i * LineStrips.FLOATS_PER_VERTEX,
                (i + 1) * LineStrips.FLOATS_PER_VERTEX,
            ).toList()

        // The first vertex has no predecessor, so prev repeats cur; next is b.
        val first = vertex(0)
        assertThat(first.subList(0, 3)).isEqualTo(first.subList(3, 6))
        assertThat(first.subList(6, 9))
            .containsExactly(b.x.toFloat(), b.y.toFloat(), b.z.toFloat())
            .inOrder()
        assertThat(first.subList(9, 15))
            .containsExactly(-1f, 1f, 0f, 0f, 0.5f, 1.5f)
            .inOrder()
        assertThat(vertex(1)[9]).isEqualTo(1f)
        // The last vertex has no successor, so next repeats cur.
        val last = vertex(5)
        assertThat(last.subList(6, 9)).isEqualTo(last.subList(3, 6))
    }

    @Test
    fun lines_subdivideLongArcsAndDropDegenerates() {
        val quarterCircle = listOf(Vector3.UNIT_X, Vector3.UNIT_Y)
        val strips =
            LineStrips.build(
                listOf(
                    LinePrimitive(quarterCircle, red, 1.0),
                    // A single point and a zero-length line have nothing to stroke.
                    LinePrimitive(listOf(Vector3.UNIT_Z), red, 1.0),
                    LinePrimitive(listOf(Vector3.UNIT_Z, Vector3.UNIT_Z), red, 1.0),
                ),
            )
        // 90° at no more than 5° per chord is at least 18 segments, so at least 19 vertices.
        assertThat(strips.vertexCount / 2).isAtLeast(19)
        assertThat(strips.indices.size).isEqualTo((strips.vertexCount / 2 - 1) * 6)
    }
}
