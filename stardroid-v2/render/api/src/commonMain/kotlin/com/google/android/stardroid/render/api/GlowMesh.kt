/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

/**
 * One layer's [GlowPrimitive]s as an indexed triangle mesh: interleaved `(x, y, z, r, g, b, a)`
 * vertices, one per ring vertex, and the two triangles filling each quad between consecutive
 * rings — the portable `build` half of a programmable backend's glow drawer (D117).
 *
 * Colour, including alpha, is per ring vertex, so the rasterizer interpolates it across each band
 * and the result is one smooth gradient. Indices are 32-bit, so unlike a 16-bit mesh there is no
 * vertex cap to bail out at.
 */
class GlowMesh private constructor(
    val vertices: FloatArray,
    val vertexCount: Int,
    val indices: IntArray,
) {
    companion object {
        /** Floats per vertex: position 3, colour 4. */
        const val FLOATS_PER_VERTEX = 7

        fun build(glows: List<GlowPrimitive>): GlowMesh {
            // A band needs at least two rings of at least two vertices each.
            val drawable = glows.filter { it.rings.size >= 2 && it.rings[0].vertices.size >= 2 }
            val vertexCount = drawable.sumOf { it.rings.size * it.rings[0].vertices.size }
            val quadCount =
                drawable.sumOf { (it.rings.size - 1) * (it.rings[0].vertices.size - 1) }
            val vertices = FloatArray(vertexCount * FLOATS_PER_VERTEX)
            val indices = IntArray(quadCount * 6)

            var v = 0
            var ix = 0
            var baseVertex = 0
            for (glow in drawable) {
                val ringLength = glow.rings[0].vertices.size
                for (ring in glow.rings) {
                    val c = ring.color
                    for (p in ring.vertices) {
                        vertices[v++] = p.x.toFloat()
                        vertices[v++] = p.y.toFloat()
                        vertices[v++] = p.z.toFloat()
                        vertices[v++] = c.r
                        vertices[v++] = c.g
                        vertices[v++] = c.b
                        vertices[v++] = c.a
                    }
                }
                for (ring in 0 until glow.rings.size - 1) {
                    val top = baseVertex + ring * ringLength
                    val bottom = top + ringLength
                    for (i in 0 until ringLength - 1) {
                        indices[ix++] = top + i
                        indices[ix++] = bottom + i
                        indices[ix++] = bottom + i + 1
                        indices[ix++] = top + i
                        indices[ix++] = bottom + i + 1
                        indices[ix++] = top + i + 1
                    }
                }
                baseVertex += glow.rings.size * ringLength
            }
            return GlowMesh(vertices, vertexCount, indices)
        }
    }
}
