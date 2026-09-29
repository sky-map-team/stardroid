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
import com.google.android.stardroid.render.api.Ground
import com.google.android.stardroid.render.api.GroundRamp
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyGradient
import com.google.android.stardroid.render.api.Viewport

/**
 * The translucent ground below the horizon, evaluated per pixel.
 *
 * Replaces the additive eight-ring horizon glow that `HorizonLayer` used to submit as a
 * `GlowPrimitive`. The glow was doing two jobs at once — marking the horizon and faking a sense of
 * depth — and its ring count existed only to trace an exponential falloff with piecewise-linear
 * alpha. Separating them leaves the horizon layer drawing a plain line and its cardinal labels,
 * and moves every bit of shading here, where the falloff is one `exp` in a fragment shader.
 *
 * Like [SkyGradientDrawer] there is no mesh: the ground is a full-screen quad and the geometry is
 * `gl_VertexID`. Unlike it, this blends rather than overwriting — additive blending cannot darken,
 * and darkening is the entire job — which is why the caller draws it part-way through the layer
 * order, after the objects it occludes, rather than as a backdrop.
 *
 * The falloff is [GroundRamp], transcribed into `ground.frag` and conformance-tested against the
 * Kotlin original.
 */
object GroundDrawer {
    /**
     * Must be called on the GL thread. A zero-opacity [Ground] still issues the draw; the shader
     * discards immediately, which costs one degenerate pass and keeps the branch in one place.
     */
    fun draw(
        gl: GlState,
        program: ShaderProgram,
        gradient: SkyGradient,
        camera: SkyCamera,
        viewport: Viewport,
        emptyVao: Int,
    ) {
        gl.useProgram(program)
        gl.blend(GlState.BlendMode.ALPHA)

        ViewRayUniforms.set(program, camera, viewport)

        val sun = gradient.sunDirection.normalized()
        val zenith = gradient.zenithDirection.normalized()
        GLES30.glUniform3f(
            program.uniform("uSunDir"),
            sun.x.toFloat(),
            sun.y.toFloat(),
            sun.z.toFloat(),
        )
        GLES30.glUniform3f(
            program.uniform("uZenithDir"),
            zenith.x.toFloat(),
            zenith.y.toFloat(),
            zenith.z.toFloat(),
        )

        val ground = gradient.ground
        GLES30.glUniform3f(
            program.uniform("uGroundColor"),
            ground.color.r,
            ground.color.g,
            ground.color.b,
        )
        GLES30.glUniform1f(program.uniform("uGroundOpacity"), ground.opacity.toFloat())

        gl.bindVertexArray(emptyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }
}
