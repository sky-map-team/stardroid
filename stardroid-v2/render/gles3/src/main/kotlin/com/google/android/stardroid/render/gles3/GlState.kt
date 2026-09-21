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
    // The bind-or-skip decisions live in BoundName so they can be unit-tested without a GL
    // context; what stays here is the GL call each decision guards.
    private val program = BoundName()
    private val vertexArray = BoundName()
    private val texture2d = BoundName()
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
        if (program.needsBind(shader.id)) GLES30.glUseProgram(shader.id)
    }

    fun bindVertexArray(name: Int) {
        if (vertexArray.needsBind(name)) GLES30.glBindVertexArray(name)
    }

    fun bindTexture(
        name: Int,
        unit: Int = 0,
    ) {
        if (activeUnit != unit) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            activeUnit = unit
            // A unit change invalidates what we believe is bound to the *new* unit.
            texture2d.invalidate()
        }
        if (texture2d.needsBind(name)) GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name)
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

    /**
     * Tells the cache that [name] is about to be deleted, so it stops believing it is bound.
     *
     * GL reverts the binding to zero when the *currently bound* vertex array is deleted, and
     * `glGenVertexArrays` commonly hands the freed name straight back — so a cache still
     * holding it would skip the next bind of what is nominally "the same" name, and the
     * attribute setup that followed would silently land in the default vertex array object
     * instead of the new mesh's. Setting the cache to 0 matches exactly what GL did.
     */
    fun onVertexArrayDeleted(name: Int) {
        vertexArray.onDeleted(name)
    }

    /**
     * The texture equivalent of [onVertexArrayDeleted]: deleting a bound texture acts as a bind
     * of texture zero on the units it was bound to, which the cache must not miss.
     */
    fun onTexturesDeleted(names: IntArray) {
        texture2d.onAnyDeleted(names)
    }

    /** Forgets everything. Call after EGL context loss, before the first draw of a new context. */
    fun invalidate() {
        program.reset()
        vertexArray.reset()
        texture2d.reset()
        activeUnit = -1
        blendMode = BlendMode.NONE
    }
}
