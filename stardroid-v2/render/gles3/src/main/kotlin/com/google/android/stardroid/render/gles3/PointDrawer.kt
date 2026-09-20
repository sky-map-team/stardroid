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
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.StellarRamps
import java.nio.FloatBuffer

/** One layer's points, interleaved as `(x, y, z, r, g, b, a, sizeDp, magnitude)` per vertex. */
class PointBuffers(
    val data: FloatBuffer,
    val vertexCount: Int,
)

/**
 * Stars and other point sources: one buffer, **one draw call** per layer.
 *
 * The GLES1 backend has to sort points into runs of equal size and issue a draw call per run,
 * because `glPointSize` is per-draw-call state there. Here size is a per-vertex shader output,
 * so the runs are gone.
 *
 * The split between what is baked and what is a uniform is the interesting part. Colour tint,
 * magnitude shade and size depend only on the scene, so they are baked here. Alpha's dependence
 * on `RenderState.magnitudeLimit`, and the night-mode transform, are *not* — they go to the
 * shader as uniforms, which is why this buffer survives a magnitude-limit change or a night-mode
 * toggle that would rebuild every GLES1 point buffer in the scene.
 *
 * Also gone with the fixed-function pipeline: `GL_POINT_SMOOTH`, which GL ES is permitted to
 * ignore. A field report on 2026-09-18 had a real Samsung device drawing hard-edged squares
 * instead of stars for exactly that reason (the D31 TODO). The fragment shader computes coverage
 * itself, so the disc is the same shape on every device.
 */
object PointDrawer {
    /** Floats per vertex: position 3, colour 4, size 1, magnitude 1. */
    const val FLOATS_PER_VERTEX = 9

    /**
     * A magnitude no real object reaches, for points whose brightness is fixed rather than
     * catalogued. `magnitudeAlpha` leaves these alone at any limit.
     */
    private const val NO_MAGNITUDE = -1e9f

    /** Stands in for a `null` magnitude limit — nothing is ever fainter than this. */
    const val NO_MAGNITUDE_LIMIT = 1e9f

    /** Pure: interleaves [allPoints] into one vertex buffer. No GL, no [RenderState]. */
    fun build(allPoints: List<PointPrimitive>): PointBuffers {
        // Icon points are textured quads owned by IconDrawer; this drawer draws only the dots.
        val points = allPoints.filterNot { it.appearance is PointAppearance.Icon }
        val data = directFloatBuffer(points.size * FLOATS_PER_VERTEX)
        for (point in points) {
            val pos = point.pos
            data.put(pos.x.toFloat()).put(pos.y.toFloat()).put(pos.z.toFloat())
            when (val appearance = point.appearance) {
                is PointAppearance.Stellar -> {
                    val shade = StellarRamps.magnitudeShade(appearance.magnitude)
                    val tint = StellarRamps.colorForIndex(appearance.colorIndex)
                    data.put(tint.r * shade).put(tint.g * shade).put(tint.b * shade).put(1f)
                    data.put(StellarRamps.sizeDp(appearance.magnitude))
                    data.put(appearance.magnitude.toFloat())
                }
                is PointAppearance.Fixed -> {
                    val color = appearance.color
                    data.put(color.r).put(color.g).put(color.b).put(color.a)
                    data.put(appearance.sizeDp.toFloat())
                    data.put(NO_MAGNITUDE)
                }
                is PointAppearance.Icon ->
                    error("Icon points are textured quads drawn by IconDrawer, not styled dots")
            }
        }
        data.rewind()
        return PointBuffers(data, points.size)
    }

    /** Uploads [buffers] into a VAO. Must be called on the GL thread. */
    fun upload(
        buffers: PointBuffers,
        program: ShaderProgram,
    ): Mesh {
        val vao = Mesh.genVertexArray()
        val vbo = Mesh.genBuffers(1)
        GLES30.glBindVertexArray(vao)
        Mesh.uploadFloats(vbo[0], buffers.data, buffers.vertexCount * FLOATS_PER_VERTEX)
        Mesh.floatAttrib(program.attrib("aPos"), 3, FLOATS_PER_VERTEX, 0)
        Mesh.floatAttrib(program.attrib("aColor"), 4, FLOATS_PER_VERTEX, 3)
        Mesh.floatAttrib(program.attrib("aSizeDp"), 1, FLOATS_PER_VERTEX, 7)
        Mesh.floatAttrib(program.attrib("aMagnitude"), 1, FLOATS_PER_VERTEX, 8)
        GLES30.glBindVertexArray(0)
        return Mesh(vao, vbo, buffers.vertexCount)
    }

    /** Must be called on the GL thread. */
    fun draw(
        gl: GlState,
        program: ShaderProgram,
        mesh: Mesh,
        viewProj: FloatArray,
        density: Float,
        magnitudeLimit: Double?,
        nightMode: Boolean,
    ) {
        if (mesh.count == 0) return
        gl.useProgram(program)
        gl.blend(GlState.BlendMode.ALPHA)
        GLES30.glUniformMatrix4fv(program.uniform("uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform1f(program.uniform("uDensity"), density)
        GLES30.glUniform1f(
            program.uniform("uMagnitudeLimit"),
            magnitudeLimit?.toFloat() ?: NO_MAGNITUDE_LIMIT,
        )
        GLES30.glUniform1f(program.uniform("uNightMode"), if (nightMode) 1f else 0f)
        gl.bindVertexArray(mesh.vao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, mesh.count)
    }
}
