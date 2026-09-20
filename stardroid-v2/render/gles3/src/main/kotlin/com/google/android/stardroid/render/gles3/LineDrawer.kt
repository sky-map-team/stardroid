/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.opengl.GLES30
import com.google.android.stardroid.render.api.GreatCircleSubdivision
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.Rgba
import java.nio.FloatBuffer

/** One layer's subdivided line vertices, plus the per-primitive draw ranges. */
class LineBuffers(
    val vertices: FloatBuffer,
    val vertexCount: Int,
    val segments: List<Segment>,
) {
    /** One [LinePrimitive]'s vertex range, colour and pixel width. */
    data class Segment(
        val color: Rgba,
        val widthPx: Float,
        val offset: Int,
        val count: Int,
    )
}

/**
 * Constellation lines, the grid, the horizon, the ecliptic — drawn as `GL_LINE_STRIP` with
 * `glLineWidth`, exactly as on GLES1.
 *
 * The obvious GLES3 move is to extrude each segment into a screen-space quad in the vertex
 * shader. We are deliberately not doing that (render-gles3.md §3.1), and it is worth saying why
 * here so it is not helpfully reintroduced: our lines are translucent — the grid is 8% alpha —
 * and [GreatCircleSubdivision] splits every great circle into roughly 72 segments, so
 * independently extruded quads would double-blend at every joint and read as beaded rather than
 * drawn. `SkyColors.ECLIPTIC_LINE` is already forced opaque for exactly this reason. If a device
 * is ever measured clamping `glLineWidth`, the answer is a mitered triangle strip whose adjacent
 * segments share their joint vertices — never naive per-segment quads.
 *
 * Night mode is a uniform rather than a second baked colour per segment, so unlike GLES1 there
 * is no `nightColor` to carry.
 */
object LineDrawer {
    /** Chords longer than this are subdivided; smooth enough to look curved, cheap to compute. */
    private const val MAX_SEGMENT_ANGLE_DEG = 5.0

    /** Pure: subdivides and interleaves. No GL. */
    fun build(
        lines: List<LinePrimitive>,
        density: Float,
    ): LineBuffers {
        val subdivided =
            lines.map { GreatCircleSubdivision.subdivide(it.vertices, MAX_SEGMENT_ANGLE_DEG) }
        val totalVertices = subdivided.sumOf { it.size }
        val vertices = directFloatBuffer(totalVertices * 3)
        val segments = ArrayList<LineBuffers.Segment>(lines.size)
        var offset = 0
        for ((i, line) in lines.withIndex()) {
            val lineVertices = subdivided[i]
            for (v in lineVertices) {
                vertices.put(v.x.toFloat()).put(v.y.toFloat()).put(v.z.toFloat())
            }
            segments.add(
                LineBuffers.Segment(
                    color = line.color,
                    widthPx = (line.widthDp * density).toFloat(),
                    offset = offset,
                    count = lineVertices.size,
                ),
            )
            offset += lineVertices.size
        }
        vertices.rewind()
        return LineBuffers(vertices, totalVertices, segments)
    }

    /** Must be called on the GL thread. */
    fun upload(
        buffers: LineBuffers,
        program: ShaderProgram,
    ): Mesh {
        val vao = Mesh.genVertexArray()
        val vbo = Mesh.genBuffers(1)
        GLES30.glBindVertexArray(vao)
        Mesh.uploadFloats(vbo[0], buffers.vertices, buffers.vertexCount * 3)
        Mesh.floatAttrib(program.attrib("aPos"), 3, 3, 0)
        GLES30.glBindVertexArray(0)
        return Mesh(vao, vbo, buffers.vertexCount)
    }

    /** Must be called on the GL thread. */
    fun draw(
        gl: GlState,
        program: ShaderProgram,
        mesh: Mesh,
        segments: List<LineBuffers.Segment>,
        viewProj: FloatArray,
        nightMode: Boolean,
    ) {
        if (segments.isEmpty() || mesh.count == 0) return
        gl.useProgram(program)
        gl.blend(GlState.BlendMode.ALPHA)
        GLES30.glUniformMatrix4fv(program.uniform("uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform1f(program.uniform("uNightMode"), if (nightMode) 1f else 0f)
        gl.bindVertexArray(mesh.vao)

        // Indexed loop and cached state, as on GLES1: a for-in over a List allocates an iterator
        // every frame, and most segments in a layer share a colour and a width.
        val colorLocation = program.uniform("uColor")
        var activeWidth = -1f
        var activeColor: Rgba? = null
        for (i in segments.indices) {
            val segment = segments[i]
            // A degenerate single-point line has nothing to stroke.
            if (segment.count < 2) continue
            if (segment.color != activeColor) {
                val c = segment.color
                GLES30.glUniform4f(colorLocation, c.r, c.g, c.b, c.a)
                activeColor = segment.color
            }
            if (segment.widthPx != activeWidth) {
                GLES30.glLineWidth(segment.widthPx)
                activeWidth = segment.widthPx
            }
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, segment.offset, segment.count)
        }
    }
}
