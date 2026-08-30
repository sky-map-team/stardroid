/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.render.api.GlowPrimitive
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * Client-side arrays for one layer's glow meshes. [colors] and [nightColors] are per-vertex RGBA
 * (4 floats each); [draw] picks between them per frame, so a night-mode flip never invalidates
 * built buffers.
 */
internal class GlowBuffers(
    val vertices: FloatBuffer,
    val colors: FloatBuffer,
    val nightColors: FloatBuffer,
    val indices: ShortBuffer,
    val indexCount: Int,
) {
    companion object {
        val EMPTY =
            GlowBuffers(
                directFloatBuffer(0),
                directFloatBuffer(0),
                directFloatBuffer(0),
                directShortBuffer(0),
                indexCount = 0,
            )
    }
}

/**
 * Builds and draws [GlowPrimitive]s as smooth-shaded, **additively-blended** triangle meshes
 * (ports v1's `HorizonGlowObjectManager`, upstream #924).
 *
 * The bands between consecutive rings are filled as triangle pairs; the fixed pipeline's Gouraud
 * shading interpolates the per-vertex ring colors — including alpha — across each band, so the
 * gradient is one seamless surface. Drawing with `GL_SRC_ALPHA, GL_ONE` adds light to whatever
 * is behind the mesh (black sky or a twilight gradient alike) instead of blending toward it.
 */
internal object GlowDrawer {
    private const val MAX_UNSIGNED_SHORT_VERTICES = 65536

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
        // Vertices are indexed with unsigned shorts (glDrawElements(GL_UNSIGNED_SHORT, ...)):
        // .toShort() preserves the bit pattern GL reads back as 0..65535, so the cap is 65536,
        // not Short.MAX_VALUE. Bail out rather than overflow into corruption.
        if (numVertices > MAX_UNSIGNED_SHORT_VERTICES) return GlowBuffers.EMPTY

        val vertexBuffer = directFloatBuffer(numVertices * 3)
        val colorBuffer = directFloatBuffer(numVertices * 4)
        val nightColorBuffer = directFloatBuffer(numVertices * 4)
        val indexBuffer = directShortBuffer(numQuads * 6)

        var baseVertex = 0
        for (glow in drawable) {
            val rings = glow.rings
            val ringLength = rings[0].vertices.size

            // Vertices, ring by ring, each ring painted in its own color.
            for (ring in rings) {
                val night = StellarStyler.applyNightMode(ring.color, true)
                for (v in ring.vertices) {
                    vertexBuffer.put(v.x.toFloat()).put(v.y.toFloat()).put(v.z.toFloat())
                    colorBuffer
                        .put(ring.color.r)
                        .put(ring.color.g)
                        .put(ring.color.b)
                        .put(ring.color.a)
                    nightColorBuffer.put(night.r).put(night.g).put(night.b).put(night.a)
                }
            }

            // Indices: two triangles per quad between consecutive rings.
            for (ring in 0 until rings.size - 1) {
                val topRowStart = baseVertex + ring * ringLength
                val bottomRowStart = topRowStart + ringLength
                for (i in 0 until ringLength - 1) {
                    val topLeft = topRowStart + i
                    val topRight = topLeft + 1
                    val bottomLeft = bottomRowStart + i
                    val bottomRight = bottomLeft + 1
                    indexBuffer.put(topLeft.toShort())
                    indexBuffer.put(bottomLeft.toShort())
                    indexBuffer.put(bottomRight.toShort())
                    indexBuffer.put(topLeft.toShort())
                    indexBuffer.put(bottomRight.toShort())
                    indexBuffer.put(topRight.toShort())
                }
            }
            baseVertex += rings.size * ringLength
        }
        vertexBuffer.rewind()
        colorBuffer.rewind()
        nightColorBuffer.rewind()
        indexBuffer.rewind()
        return GlowBuffers(
            vertexBuffer,
            colorBuffer,
            nightColorBuffer,
            indexBuffer,
            indexCount = numQuads * 6,
        )
    }

    /** A glow needs at least two rings of at least two vertices each to form a band of quads. */
    private fun isDrawable(glow: GlowPrimitive): Boolean =
        glow.rings.size >= 2 && glow.rings[0].vertices.size >= 2

    fun draw(
        gl: GL10,
        buffers: GlowBuffers,
        nightMode: Boolean,
    ) {
        if (buffers.indexCount == 0) return
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, buffers.vertices)
        gl.glColorPointer(
            4,
            GL10.GL_FLOAT,
            0,
            if (nightMode) buffers.nightColors else buffers.colors,
        )
        // The gradient depends on Gouraud interpolation of the ring colors across each band.
        gl.glShadeModel(GL10.GL_SMOOTH)
        // Additive: the glow adds light to whatever is behind it. Restored below to the standard
        // alpha blend the rest of the frame expects (GLSkyRenderer.onSurfaceCreated).
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE)
        gl.glDrawElements(
            GL10.GL_TRIANGLES,
            buffers.indexCount,
            GL10.GL_UNSIGNED_SHORT,
            buffers.indices,
        )
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}
