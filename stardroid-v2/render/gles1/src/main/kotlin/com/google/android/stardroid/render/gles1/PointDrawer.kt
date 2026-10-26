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

/** Max point sizes (px) the driver supports; null if the query failed. */
internal data class PointLimits(val smoothMax: Float?, val aliasedMax: Float?)

/** How one size run is rasterised: smoothed (round) or aliased (square), at [sizePx]. */
internal data class PointStyle(val smooth: Boolean, val sizePx: Float)

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

    /**
     * Drivers reporting a 1..1 smooth range render stars as faint 1px specks, so fall back to
     * aliased points there. Any wider range is left to the driver, which clamps the size itself.
     */
    internal fun styleFor(
        sizePx: Float,
        limits: PointLimits,
    ): PointStyle {
        val smoothMax = limits.smoothMax ?: return PointStyle(smooth = true, sizePx = sizePx)
        if (smoothMax > 1f) return PointStyle(smooth = true, sizePx = minOf(sizePx, smoothMax))
        return PointStyle(smooth = false, sizePx = minOf(sizePx, limits.aliasedMax ?: sizePx))
    }

    fun draw(
        gl: GL10,
        buffers: PointBuffers,
        limits: PointLimits,
    ) {
        if (buffers.sizeRuns.isEmpty()) return
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, buffers.vertices)
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, buffers.colors)
        for (run in buffers.sizeRuns) {
            val style = styleFor(run.sizePx, limits)
            val smoothCap = GL10.GL_POINT_SMOOTH
            if (style.smooth) gl.glEnable(smoothCap) else gl.glDisable(smoothCap)
            gl.glPointSize(style.sizePx)
            gl.glDrawArrays(GL10.GL_POINTS, run.offset, run.count)
        }
        gl.glDisable(GL10.GL_POINT_SMOOTH)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}
