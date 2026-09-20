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
import com.google.android.stardroid.render.api.Rgba
import java.nio.FloatBuffer

/**
 * Screen-space quads, accumulated and drawn with one instanced call per texture.
 *
 * This is the single largest structural change in the port. GLES1 draws each icon and each label
 * with its own `glPushMatrix` / `glScalef` / `glDrawArrays`, and `LabelDrawer` carries a TODO
 * saying so — fine at tens of labels, not at the hundreds a catalog layer submits. Here a whole
 * atlas page's worth of labels is one `glDrawArraysInstanced`, and the quad geometry itself comes
 * from `gl_VertexID`, so there is no per-quad vertex data at all.
 *
 * Instances are screen-space and therefore rebuilt every frame, which is why the buffer is
 * `GL_DYNAMIC_DRAW` and why it grows to a high-water mark and is never shrunk: the alternative is
 * an allocation inside the draw loop, which on Android shows up as frame-time spikes rather than
 * a lower average (audit-2026-08 M2).
 *
 * GL-thread-only.
 */
class SpriteBatch(private val program: ShaderProgram) {
    /** Floats per instance: centre 2, size 2, uv0 2, uv1 2, tint 4, mode 1. */
    private val floatsPerInstance = FLOATS_PER_INSTANCE

    private var data: FloatBuffer = directFloatBuffer(INITIAL_CAPACITY * floatsPerInstance)
    private var capacity = INITIAL_CAPACITY
    private var vao = 0
    private var vbo = 0

    /** Instances added since the last [clear]. */
    var size: Int = 0
        private set

    fun clear() {
        size = 0
        data.clear()
    }

    /**
     * Queues one quad.
     *
     * @param centerXPx centre in pixels, bottom-left viewport origin (GL's, and GLES1's for the
     *   same screen-space passes).
     * @param uv0 the texture coordinate at the quad's lower-left corner, [uv1] at its upper
     *   right. Label atlases are uploaded without a vertical flip, so callers pass the *larger*
     *   v in [uv0] — the same "negative crop height" convention `LabelDrawer` has always used.
     * @param mode one of [MODE_ICON], [MODE_GLYPH] or [MODE_MARKER].
     */
    @Suppress("LongParameterList")
    fun add(
        centerXPx: Float,
        centerYPx: Float,
        widthPx: Float,
        heightPx: Float,
        uv0: FloatArray,
        uv1: FloatArray,
        tint: Rgba,
        alpha: Float,
        mode: Int,
    ) {
        grow(size + 1)
        data.position(size * floatsPerInstance)
        data.put(centerXPx).put(centerYPx)
        data.put(widthPx).put(heightPx)
        data.put(uv0[0]).put(uv0[1])
        data.put(uv1[0]).put(uv1[1])
        data.put(tint.r).put(tint.g).put(tint.b).put(tint.a * alpha)
        data.put(mode.toFloat())
        size++
    }

    private fun grow(needed: Int) {
        if (needed <= capacity) return
        val newCapacity = maxOf(needed, capacity * 2)
        val replacement = directFloatBuffer(newCapacity * floatsPerInstance)
        data.position(0)
        data.limit(size * floatsPerInstance)
        replacement.put(data)
        data.limit(data.capacity())
        data = replacement
        capacity = newCapacity
    }

    /**
     * Uploads the queued instances and draws them as one instanced call.
     *
     * [texelSize] is one texel of the bound texture, which the halo taps step by; pass zeroes
     * for a mode that does not sample a texture.
     */
    @Suppress("LongParameterList")
    fun flush(
        gl: GlState,
        textureId: Int,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        texelSize: FloatArray,
        nightMode: Boolean,
        haloColor: Rgba,
        haloTexels: Float,
    ) {
        if (size == 0) return
        ensureVao()
        gl.useProgram(program)
        gl.blend(GlState.BlendMode.ALPHA)
        GLES30.glUniform2f(program.uniform("uViewportPx"), viewportWidthPx, viewportHeightPx)
        GLES30.glUniform2f(program.uniform("uTexelSize"), texelSize[0], texelSize[1])
        GLES30.glUniform1f(program.uniform("uNightMode"), if (nightMode) 1f else 0f)
        GLES30.glUniform4f(
            program.uniform("uHaloColor"),
            haloColor.r,
            haloColor.g,
            haloColor.b,
            haloColor.a,
        )
        GLES30.glUniform1f(program.uniform("uHaloTexels"), haloTexels)
        GLES30.glUniform1i(program.uniform("uTexture"), 0)
        if (textureId != 0) gl.bindTexture(textureId)

        data.position(0)
        data.limit(size * floatsPerInstance)
        gl.bindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            size * floatsPerInstance * FLOAT_BYTES,
            data,
            GLES30.GL_DYNAMIC_DRAW,
        )
        data.limit(data.capacity())
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, size)
        clear()
    }

    private fun ensureVao() {
        if (vao != 0) return
        vao = Mesh.genVertexArray()
        vbo = Mesh.genBuffers(1)[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        // Every attribute advances once per instance, not once per vertex: the four vertices of
        // the quad are gl_VertexID arithmetic and read no buffer at all.
        Mesh.floatAttrib(program.attrib("aCenterPx"), 2, floatsPerInstance, 0, divisor = 1)
        Mesh.floatAttrib(program.attrib("aSizePx"), 2, floatsPerInstance, 2, divisor = 1)
        Mesh.floatAttrib(program.attrib("aUv0"), 2, floatsPerInstance, 4, divisor = 1)
        Mesh.floatAttrib(program.attrib("aUv1"), 2, floatsPerInstance, 6, divisor = 1)
        Mesh.floatAttrib(program.attrib("aTint"), 4, floatsPerInstance, 8, divisor = 1)
        Mesh.floatAttrib(program.attrib("aMode"), 1, floatsPerInstance, 12, divisor = 1)
        GLES30.glBindVertexArray(0)
    }

    /** Forgets the GL objects after EGL context loss; the CPU-side instance buffer is kept. */
    fun onContextLost() {
        vao = 0
        vbo = 0
        clear()
    }

    companion object {
        const val FLOATS_PER_INSTANCE = 13
        private const val INITIAL_CAPACITY = 128

        /** An RGBA texture modulated by a tint: deep-sky markers, shower radiants. */
        const val MODE_ICON = 0

        /** An R8 coverage mask: one label's glyph run, with a halo under it. */
        const val MODE_GLYPH = 1

        /** A procedural filled dot: the "this object has an info card" marker. */
        const val MODE_MARKER = 2

        /** Full-texture coordinates, in the atlas's unflipped convention. */
        val FULL_UV0 = floatArrayOf(0f, 1f)
        val FULL_UV1 = floatArrayOf(1f, 0f)

        /** No texture is bound, so the halo taps must not step anywhere. */
        val NO_TEXEL = floatArrayOf(0f, 0f)
    }
}
