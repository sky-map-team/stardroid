/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/** Client-side vertex array for one layer's lines, plus the per-primitive draw calls. */
internal class LineBuffers(val vertices: FloatBuffer, val segments: List<Segment>) {
    /**
     * One [LinePrimitive]'s subdivided vertex range, producer color, and pixel width. [color] is
     * the primitive's day-mode color and [nightColor] its pre-applied night-mode transform;
     * [draw] picks between them per frame, so a [RenderState] change never invalidates built
     * line buffers and drawing allocates no [Rgba].
     */
    data class Segment(
        val color: Rgba,
        val nightColor: Rgba,
        val widthPx: Float,
        val offset: Int,
        val count: Int,
    )
}

/**
 * Builds and draws [LinePrimitive]s as `GL_LINE_STRIP`. Width is constant in screen pixels (D12),
 * set per draw call via `glLineWidth` — like point size, GL's line width is native pixel space,
 * so no width-extruded quad geometry is needed (v1 built quads because it wanted *zoom-scaled*
 * width, exactly what D12 replaces; see render-api.md's port notes). Long chords are subdivided
 * ([GreatCircleSubdivision]) so they hug the sphere instead of cutting a straight chord through it.
 */
internal object LineDrawer {
    /** Chords longer than this are subdivided; smooth enough to look curved, cheap to compute. */
    private const val MAX_SEGMENT_ANGLE_DEG = 5.0

    fun build(
        lines: List<LinePrimitive>,
        density: Float,
    ): LineBuffers {
        val subdivided =
            lines.map {
                GreatCircleSubdivision.subdivide(it.vertices, MAX_SEGMENT_ANGLE_DEG)
            }
        val totalVertices = subdivided.sumOf { it.size }
        val vertexBuffer = directFloatBuffer(totalVertices * 3)
        val segments = ArrayList<LineBuffers.Segment>(lines.size)
        var offset = 0
        for ((i, line) in lines.withIndex()) {
            val vertices = subdivided[i]
            for (v in vertices) {
                vertexBuffer.put(v.x.toFloat()).put(v.y.toFloat()).put(v.z.toFloat())
            }
            val widthPx = (line.widthDp * density).toFloat()
            segments.add(
                LineBuffers.Segment(
                    color = line.color,
                    nightColor = StellarStyler.applyNightMode(line.color, true),
                    widthPx = widthPx,
                    offset = offset,
                    count = vertices.size,
                ),
            )
            offset += vertices.size
        }
        vertexBuffer.rewind()
        return LineBuffers(vertexBuffer, segments)
    }

    fun draw(
        gl: GL10,
        buffers: LineBuffers,
        nightMode: Boolean,
    ) {
        if (buffers.segments.isEmpty()) return
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, buffers.vertices)
        // Indexed loop: a for-in over a List allocates an Iterator on every frame. Width and
        // color are cached because most segments in a layer share them, and redundant
        // glLineWidth/glColor4f calls are wasted GL state changes.
        var activeWidth = -1f
        var activeColor: Rgba? = null
        for (i in buffers.segments.indices) {
            val segment = buffers.segments[i]
            if (segment.count < 2) continue // a degenerate single-point line has nothing to stroke
            val color = if (nightMode) segment.nightColor else segment.color
            if (color != activeColor) {
                gl.glColor4f(color.r, color.g, color.b, color.a)
                activeColor = color
            }
            if (segment.widthPx != activeWidth) {
                gl.glLineWidth(segment.widthPx)
                activeWidth = segment.widthPx
            }
            gl.glDrawArrays(GL10.GL_LINE_STRIP, segment.offset, segment.count)
        }
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}
