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
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.Viewport
import kotlin.math.min
import kotlin.math.tan

/**
 * Uploads the camera basis and field of view that let a full-screen-quad fragment shader
 * reconstruct its own view direction, avoiding a matrix inverse per pixel.
 *
 * Shared by the two passes that shade the sphere rather than draw things on it — the sky dome and
 * the ground — because they must agree to the last bit. They meet along the horizon, and a basis
 * derived even slightly differently in the two shaders would show up as a seam exactly where both
 * are at their most opaque.
 */
internal object ViewRayUniforms {
    /**
     * Sets `uCamForward`, `uCamRight`, `uCamUp` and `uTanHalfFov` on [program], which must already
     * be in use. Must be called on the GL thread.
     */
    fun set(
        program: ShaderProgram,
        camera: SkyCamera,
        viewport: Viewport,
    ) {
        // Mirrors Matrix4.view's construction exactly: right = forward × up, then up is
        // re-orthogonalized as right × forward.
        val forward = camera.lineOfSight.normalized()
        val right = (forward cross camera.up).normalized()
        val up = right cross forward
        GLES30.glUniform3f(
            program.uniform("uCamForward"),
            forward.x.toFloat(),
            forward.y.toFloat(),
            forward.z.toFloat(),
        )
        GLES30.glUniform3f(
            program.uniform("uCamRight"),
            right.x.toFloat(),
            right.y.toFloat(),
            right.z.toFloat(),
        )
        GLES30.glUniform3f(
            program.uniform("uCamUp"),
            up.x.toFloat(),
            up.y.toFloat(),
            up.z.toFloat(),
        )

        // fovDeg spans the short viewport side (Matrix4.perspective), so the long side's tangent
        // is scaled up by the aspect ratio rather than the other way around.
        val tanHalfFov = tan(camera.fovDeg * DEGREES_TO_RADIANS * 0.5)
        val shortSidePx = min(viewport.widthPx, viewport.heightPx).coerceAtLeast(1)
        GLES30.glUniform2f(
            program.uniform("uTanHalfFov"),
            (tanHalfFov * viewport.widthPx / shortSidePx).toFloat(),
            (tanHalfFov * viewport.heightPx / shortSidePx).toFloat(),
        )
    }
}
