/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * The camera dimmer (`RenderState.cameraScrim`, camera-ar-mode.md/D64): a full-screen black
 * quad at the given opacity, drawn first in the frame while the background is transparent. It
 * darkens the video plane composited *under* the GL surface without touching the map drawn on
 * top — the "map first, camera as ghost context" knob, and the night-vision floor.
 *
 * Drawn in clip space on identity matrices; the caller loads the real projection afterwards,
 * so nothing is saved or restored here beyond the color state.
 */
internal object CameraScrimDrawer {
    private val vertices: FloatBuffer =
        directFloatBuffer(12)
            .put(
                floatArrayOf(
                    -1f, -1f, 0f,
                    1f, -1f, 0f,
                    -1f, 1f, 0f,
                    1f, 1f, 0f,
                ),
            ).also { it.rewind() }

    fun draw(
        gl: GL10,
        opacity: Float,
    ) {
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glLoadIdentity()
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glLoadIdentity()
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glColor4f(0f, 0f, 0f, opacity.coerceIn(0f, 1f))
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertices)
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4)
        gl.glColor4f(1f, 1f, 1f, 1f)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}
