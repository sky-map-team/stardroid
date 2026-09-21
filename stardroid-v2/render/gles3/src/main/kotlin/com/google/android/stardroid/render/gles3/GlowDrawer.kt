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
import com.google.android.stardroid.render.api.GlowPrimitive
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** One layer's glow mesh: interleaved `(x, y, z, r, g, b, a)` vertices plus triangle indices. */
class GlowBuffers(
    val data: FloatBuffer,
    val vertexCount: Int,
    val indices: ShortBuffer,
    val indexCount: Int,
) {
    companion object {
        val EMPTY = GlowBuffers(directFloatBuffer(0), 0, directShortBuffer(0), 0)
    }
}

/**
 * The horizon glow: concentric vertex rings filled into additively-blended bands.
 *
 * A faithful port. The producer submits eight rings, which exist only to piecewise-approximate
 * an exponential falloff that this backend's fragment shader could evaluate in one line — but
 * collapsing them to two rings changes both the producer and the pixels, so it belongs in Part B
 * rather than in a parity port. See §10 of render-gles3.md.
 */
object GlowDrawer {
    /** Floats per vertex: position 3, colour 4. */
    const val FLOATS_PER_VERTEX = 7

    private const val MAX_UNSIGNED_SHORT_VERTICES = 65536

    /** Pure: builds the mesh. No GL. */
    fun build(glows: List<GlowPrimitive>): GlowBuffers {
        val drawable = glows.filter { isDrawable(it) }
        var numVertices = 0
        var numQuads = 0
        for (glow in drawable) {
            val rings = glow.rings.size
            val ringLength = glow.rings[0].vertices.size
            numVertices += rings * ringLength
            numQuads += (rings - 1) * (ringLength - 1)
        }
        if (numVertices == 0) return GlowBuffers.EMPTY
        // Indices are unsigned shorts: .toShort() preserves the bit pattern GL reads back as
        // 0..65535, so the cap is 65536, not Short.MAX_VALUE. Bail rather than corrupt.
        if (numVertices > MAX_UNSIGNED_SHORT_VERTICES) return GlowBuffers.EMPTY

        val data = directFloatBuffer(numVertices * FLOATS_PER_VERTEX)
        val indices = directShortBuffer(numQuads * 6)

        var baseVertex = 0
        for (glow in drawable) {
            val rings = glow.rings
            val ringLength = rings[0].vertices.size
            for (ring in rings) {
                val c = ring.color
                for (v in ring.vertices) {
                    data.put(v.x.toFloat()).put(v.y.toFloat()).put(v.z.toFloat())
                    data.put(c.r).put(c.g).put(c.b).put(c.a)
                }
            }
            for (ring in 0 until rings.size - 1) {
                val topRowStart = baseVertex + ring * ringLength
                val bottomRowStart = topRowStart + ringLength
                for (i in 0 until ringLength - 1) {
                    val topLeft = topRowStart + i
                    val topRight = topLeft + 1
                    val bottomLeft = bottomRowStart + i
                    val bottomRight = bottomLeft + 1
                    indices.put(topLeft.toShort())
                    indices.put(bottomLeft.toShort())
                    indices.put(bottomRight.toShort())
                    indices.put(topLeft.toShort())
                    indices.put(bottomRight.toShort())
                    indices.put(topRight.toShort())
                }
            }
            baseVertex += rings.size * ringLength
        }
        data.rewind()
        indices.rewind()
        return GlowBuffers(data, numVertices, indices, numQuads * 6)
    }

    /** A glow needs at least two rings of at least two vertices each to form a band of quads. */
    private fun isDrawable(glow: GlowPrimitive): Boolean =
        glow.rings.size >= 2 && glow.rings[0].vertices.size >= 2

    /**
     * Must be called on the GL thread. Binds through [gl] rather than raw GL calls — see
     * [PointDrawer.upload]'s KDoc for why a freed-and-regenerated VAO id makes that matter here.
     */
    fun upload(
        gl: GlState,
        buffers: GlowBuffers,
        program: ShaderProgram,
    ): Mesh {
        val vao = Mesh.genVertexArray()
        val vbos = Mesh.genBuffers(2)
        gl.bindVertexArray(vao)
        Mesh.uploadFloats(vbos[0], buffers.data, buffers.vertexCount * FLOATS_PER_VERTEX)
        Mesh.floatAttrib(program.attrib("aPos"), 3, FLOATS_PER_VERTEX, 0)
        Mesh.floatAttrib(program.attrib("aColor"), 4, FLOATS_PER_VERTEX, 3)
        Mesh.uploadIndices(vbos[1], buffers.indices, buffers.indexCount)
        gl.bindVertexArray(0)
        return Mesh(vao, vbos, buffers.indexCount)
    }

    /** Must be called on the GL thread. */
    fun draw(
        gl: GlState,
        program: ShaderProgram,
        mesh: Mesh,
        viewProj: FloatArray,
        nightMode: Boolean,
    ) {
        if (mesh.count == 0) return
        gl.useProgram(program)
        // Additive, so the glow adds light to whatever is behind it — black sky or twilight
        // gradient alike — instead of blending toward it.
        gl.blend(GlState.BlendMode.ADDITIVE)
        GLES30.glUniformMatrix4fv(program.uniform("uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform1f(program.uniform("uNightMode"), if (nightMode) 1f else 0f)
        gl.bindVertexArray(mesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.count, GLES30.GL_UNSIGNED_SHORT, 0)
    }
}
