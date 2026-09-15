/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.RenderState
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/** Client-side vertex/color arrays for one layer's points, plus the size-grouped draw calls. */
internal class PointBuffers(
    val vertices: FloatBuffer,
    val colors: FloatBuffer,
    val sizeRuns: List<SizeRun>,
) {
    /** A contiguous run of vertices sharing [sizePx], drawn with one `glPointSize`/draw call. */
    data class SizeRun(val sizePx: Float, val offset: Int, val count: Int)
}

/**
 * Builds and draws [PointPrimitive]s as `GL_POINTS`. Brightness is conveyed by vertex color
 * ([StellarStyler]); size is near-constant in screen pixels (D12) and GLES1's fixed pipeline only
 * controls point size per draw call (`glPointSize`), not per vertex — so points are sorted into a
 * handful of contiguous [PointBuffers.SizeRun]s by size, one draw call per run. This replaces v1's
 * textured-billboard-quad technique, whose world-space sizing is exactly what D12 disallows
 * (see render-api.md's port notes): native `glPointSize` pixels are zoom-invariant for free.
 */
internal object PointDrawer {
    fun build(
        allPoints: List<PointPrimitive>,
        state: RenderState,
        density: Float,
    ): PointBuffers {
        // Icon points are textured quads owned by IconDrawer; this drawer draws only the dots.
        val points = allPoints.filterNot { it.appearance is PointAppearance.Icon }
        val styled = points.map { StellarStyler.style(it.appearance, state, density) }
        val order = styled.indices.sortedBy { styled[it].sizePx }

        val vertices = directFloatBuffer(points.size * 3)
        val colors = directFloatBuffer(points.size * 4)
        val runs = ArrayList<PointBuffers.SizeRun>()
        var runStart = 0
        for ((i, idx) in order.withIndex()) {
            val pos = points[idx].pos
            vertices.put(pos.x.toFloat()).put(pos.y.toFloat()).put(pos.z.toFloat())
            val color = styled[idx].color
            colors.put(color.r).put(color.g).put(color.b).put(color.a)

            val isRunEnd = i == order.size - 1 || styled[order[i + 1]].sizePx != styled[idx].sizePx
            if (isRunEnd) {
                runs.add(PointBuffers.SizeRun(styled[idx].sizePx, runStart, i - runStart + 1))
                runStart = i + 1
            }
        }
        vertices.rewind()
        colors.rewind()
        return PointBuffers(vertices, colors, runs)
    }

    fun draw(
        gl: GL10,
        buffers: PointBuffers,
    ) {
        if (buffers.sizeRuns.isEmpty()) return
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, buffers.vertices)
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, buffers.colors)
        // TODO(device verification, D31): GLES1 permits GL_SMOOTH_POINT_SIZE_RANGE to be [1, 1],
        //   which would collapse every smoothed star to 1px on such hardware (the emulator won't
        //   show it). Query the range and skip GL_POINT_SMOOTH for runs above the supported max.
        gl.glEnable(GL10.GL_POINT_SMOOTH)
        for (run in buffers.sizeRuns) {
            gl.glPointSize(run.sizePx)
            gl.glDrawArrays(GL10.GL_POINTS, run.offset, run.count)
        }
        gl.glDisable(GL10.GL_POINT_SMOOTH)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}
