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

/**
 * Remembers what is currently bound so redundant GL calls are skipped.
 *
 * This is the same discipline the GLES1 drawers already apply by hand — `LabelDrawer` caches the
 * active `glColor4f` and atlas page, `LineDrawer` the active `glLineWidth` — generalised, because
 * a programmable pipeline has more state to thrash and the whole point of the port is that a
 * layer becomes one draw call rather than dozens.
 *
 * GL-thread-only. [invalidate] must be called whenever the EGL context is recreated, since the
 * remembered names then refer to objects that no longer exist.
 */
class GlState {
    private var program = 0
    private var vertexArray = 0
    private var texture2d = 0
    private var activeUnit = -1
    private var blendMode = BlendMode.NONE

    /** What a fragment's output does to what is already in the framebuffer. */
    enum class BlendMode {
        NONE,

        /** Ordinary translucency: `SRC_ALPHA, ONE_MINUS_SRC_ALPHA`. */
        ALPHA,

        /** Adds light to whatever is behind (the horizon glow): `SRC_ALPHA, ONE`. */
        ADDITIVE,
    }

    fun useProgram(shader: ShaderProgram) {
        if (program == shader.id) return
        GLES30.glUseProgram(shader.id)
        program = shader.id
    }

    fun bindVertexArray(name: Int) {
        if (vertexArray == name) return
        GLES30.glBindVertexArray(name)
        vertexArray = name
    }

    fun bindTexture(
        name: Int,
        unit: Int = 0,
    ) {
        if (activeUnit != unit) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            activeUnit = unit
            // A unit change invalidates what we believe is bound to the *new* unit.
            texture2d = -1
        }
        if (texture2d == name) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name)
        texture2d = name
    }

    fun blend(mode: BlendMode) {
        if (blendMode == mode) return
        when (mode) {
            BlendMode.NONE -> GLES30.glDisable(GLES30.GL_BLEND)
            BlendMode.ALPHA -> {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            }
            BlendMode.ADDITIVE -> {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            }
        }
        blendMode = mode
    }

    /** Forgets everything. Call after EGL context loss, before the first draw of a new context. */
    fun invalidate() {
        program = 0
        vertexArray = 0
        texture2d = 0
        activeUnit = -1
        blendMode = BlendMode.NONE
    }
}
