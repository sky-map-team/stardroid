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
 * The camera dimmer (camera-ar-mode.md/D64): a full-screen black quad drawn before all layers
 * while the background is transparent, darkening the video plane below without touching the map
 * drawn on top.
 */
object CameraScrimDrawer {
    /** Must be called on the GL thread. */
    fun draw(
        gl: GlState,
        program: ShaderProgram,
        opacity: Float,
        emptyVao: Int,
    ) {
        gl.useProgram(program)
        gl.blend(GlState.BlendMode.ALPHA)
        GLES30.glUniform1f(program.uniform("uOpacity"), opacity)
        gl.bindVertexArray(emptyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }
}
