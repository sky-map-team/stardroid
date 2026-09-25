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
import com.google.android.stardroid.render.api.SkyGradient
import com.google.android.stardroid.render.api.Viewport
import kotlin.math.min
import kotlin.math.tan

/**
 * The daytime and twilight sky, evaluated per pixel.
 *
 * This is the one drawer that deliberately does **not** match GLES1, and it is the clearest
 * example of why the port is worth doing. GLES1 draws v1's `SkyBox`: eight latitude bands, a
 * linear blue ramp of intensity 70→50 toward the sun and a grey 40→0 away from it, Gouraud-
 * interpolated. The band count is a rendering artifact, not a fact about the sky, and the ramp
 * is a gesture at daylight.
 *
 * Here the sky is an analytic scattering model (Preetham et al., 1999) sampled once per pixel,
 * so the circumsolar aureole, the darker band about 90° from the sun and the brightening toward
 * the horizon all fall out rather than being drawn in. Added to that is the regime v1 never
 * attempted at all: twilight — the warm band over the set sun, Earth's own shadow rising
 * opposite it, and the Belt of Venus above that.
 *
 * There is no mesh. The sky is a full-screen quad and the geometry is `gl_VertexID`.
 */
object SkyGradientDrawer {
    /** Must be called on the GL thread. */
    @Suppress("LongParameterList")
    fun draw(
        gl: GlState,
        program: ShaderProgram,
        gradient: SkyGradient,
        camera: SkyCamera,
        viewport: Viewport,
        emptyVao: Int,
        frameSeed: Float,
    ) {
        gl.useProgram(program)
        // The sky is the backdrop: it writes every pixel it covers, so it needs no blending.
        gl.blend(GlState.BlendMode.NONE)

        // The camera basis, so each pixel can reconstruct its own view direction without a
        // matrix inverse. This mirrors Matrix4.view's construction exactly: right = f × up,
        // then up is re-orthogonalized as right × f.
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
        GLES30.glUniform1f(program.uniform("uTurbidity"), gradient.turbidity.toFloat())
        GLES30.glUniform1f(program.uniform("uDitherSeed"), frameSeed)

        gl.bindVertexArray(emptyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }
}
