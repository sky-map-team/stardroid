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

/**
 * One layer's [LinePrimitive]s as mitered triangle strips, for a backend with no line width to
 * set (Metal has none; D117). render-gles3.md §3.1 names this as the answer and rules out the
 * obvious alternative: our lines are translucent (the grid is 8% alpha), so independent
 * per-segment quads would double-blend at every joint and read as beaded.
 *
 * Every polyline vertex becomes two strip vertices, one per side, and each carries the polyline's
 * previous, current and next positions. The vertex shader projects all three and pushes the
 * current one out along the miter of its two screen-space directions, so consecutive segments
 * share their joint vertices and a translucent line blends exactly once everywhere. Width is in dp
 * (the shader scales by density) and colour is per vertex, so a whole layer is one draw call and
 * neither a density change nor a colour mix forces a split.
 *
 * Layout per vertex: `(prev x, y, z, cur x, y, z, next x, y, z, side, r, g, b, a, halfWidthDp)`,
 * `side` being −1 or +1. At a polyline's ends the missing neighbour repeats the current vertex,
 * which the shader reads as "no joint here".
 */
class LineStrips private constructor(
    val vertices: FloatArray,
    val vertexCount: Int,
    val indices: IntArray,
) {
    companion object {
        const val FLOATS_PER_VERTEX = 15

        /** Chords longer than this are subdivided — the same threshold as the GLES backends. */
        const val MAX_SEGMENT_ANGLE_DEG = 5.0

        fun build(lines: List<LinePrimitive>): LineStrips {
            val polylines =
                lines.mapNotNull { line ->
                    val path =
                        GreatCircleSubdivision.subdivide(line.vertices, MAX_SEGMENT_ANGLE_DEG)
                            .dropConsecutiveDuplicates()
                    if (path.size >= 2) line to path else null
                }
            val vertexCount = polylines.sumOf { (_, path) -> path.size * 2 }
            val segmentCount = polylines.sumOf { (_, path) -> path.size - 1 }
            val vertices = FloatArray(vertexCount * FLOATS_PER_VERTEX)
            val indices = IntArray(segmentCount * 6)

            var v = 0
            var ix = 0
            var base = 0
            for ((line, path) in polylines) {
                val halfWidthDp = (line.widthDp / 2.0).toFloat()
                for (i in path.indices) {
                    val prev = path[maxOf(i - 1, 0)]
                    val cur = path[i]
                    val next = path[minOf(i + 1, path.size - 1)]
                    for (side in SIDES) {
                        v = vertices.putVector(v, prev)
                        v = vertices.putVector(v, cur)
                        v = vertices.putVector(v, next)
                        vertices[v++] = side
                        vertices[v++] = line.color.r
                        vertices[v++] = line.color.g
                        vertices[v++] = line.color.b
                        vertices[v++] = line.color.a
                        vertices[v++] = halfWidthDp
                    }
                }
                for (i in 0 until path.size - 1) {
                    val left = base + 2 * i
                    // Two triangles across segment i: its start pair and its end pair.
                    indices[ix++] = left
                    indices[ix++] = left + 1
                    indices[ix++] = left + 2
                    indices[ix++] = left + 1
                    indices[ix++] = left + 3
                    indices[ix++] = left + 2
                }
                base += path.size * 2
            }
            return LineStrips(vertices, vertexCount, indices)
        }

        private val SIDES = floatArrayOf(-1f, 1f)

        private fun FloatArray.putVector(
            at: Int,
            p: Vector3,
        ): Int {
            this[at] = p.x.toFloat()
            this[at + 1] = p.y.toFloat()
            this[at + 2] = p.z.toFloat()
            return at + 3
        }

        /** A zero-length segment has no direction to miter against. */
        private fun List<Vector3>.dropConsecutiveDuplicates(): List<Vector3> =
            filterIndexed { i, p -> i == 0 || (p - this[i - 1]).length2 > 1e-24 }
    }
}
