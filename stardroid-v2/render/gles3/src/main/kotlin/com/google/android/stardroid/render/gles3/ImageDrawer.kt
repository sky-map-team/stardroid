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
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.SizeFloor
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.Viewport
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One layer's images and the texture claims held on their behalf. */
class ImageGpuData(
    val images: List<ImagePrimitive>,
)

/**
 * Planet discs and deep-sky photographs: world-anchored textured quads.
 *
 * Three things GLES1 does on the CPU happen on the GPU here, and one deliberately does not:
 *
 * - **Corner expansion** is a vertex shader, so there is no vertex buffer and nothing is
 *   rebuilt per frame. GLES1 rewrites four corners per image every frame because their size
 *   depends on the field of view.
 * - **The phase** is a set of uniforms rather than a bitmap. `PhaseCompositor` paints the
 *   terminator into the texture on the CPU, cached against a *quantised* phase, which is why
 *   the Moon's terminator steps on GLES1; here it glides, because it is evaluated per pixel per
 *   frame. That is the one visible difference in this drawer, and it is an improvement rather
 *   than a parity break.
 * - **The eclipse tint** comes along for free with the phase, for the same reason.
 * - **The half-axis geometry** stays on the CPU, because it is a per-image constant given the
 *   FOV and it is the part worth unit-testing. [SizeFloor] is the same pure function the Compose
 *   search arrow uses, so the drawn disc and the arrow pointing at it can never disagree.
 */
object ImageDrawer {
    private val WORLD_UP = Vector3.UNIT_Y
    private val WORLD_UP_FALLBACK = Vector3.UNIT_Z

    /** The quad's rotated, scaled half-axes in world units: `(u, v)`. */
    data class HalfAxes(val u: Vector3, val v: Vector3)

    /**
     * Pure: the quad's half-axes at [drawnDiameterDeg], matching GLES1's `quadCorners` frame.
     *
     * - `horizontal = −(center × WORLD_UP).normalized` (right on screen by convention)
     * - `vertical = horizontal × center` (up on screen by convention)
     *
     * [ImagePrimitive.rotationDeg] rotates the frame around the centre; the half-axis scale is
     * `sin(drawnDiameterDeg / 2)`, the chord half-length on the unit sphere, so a 10° image
     * covers 10° of sky. Falls back to [WORLD_UP_FALLBACK] when the centre is within 8° of the
     * Y-pole and the cross product degenerates (D30).
     */
    fun halfAxes(
        image: ImagePrimitive,
        drawnDiameterDeg: Double = image.angularSizeDeg,
    ): HalfAxes {
        val center = image.center
        var cross = center cross WORLD_UP
        // sin²(8°) ≈ 0.01937.
        if (cross.length2 < 0.01937) cross = center cross WORLD_UP_FALLBACK
        val horizontal = -cross.normalized()
        val vertical = horizontal cross center

        val rotRad = image.rotationDeg * DEGREES_TO_RADIANS
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)
        val u = horizontal * cosR + vertical * sinR
        val v = horizontal * (-sinR) + vertical * cosR

        val scale = sin(drawnDiameterDeg / 2.0 * DEGREES_TO_RADIANS)
        return HalfAxes(u * scale, v * scale)
    }

    /** Claims a texture for each image. Must be called on the GL thread. */
    fun build(
        images: List<ImagePrimitive>,
        cache: TextureCache,
    ): ImageGpuData {
        for (image in images) cache.retain(image.image)
        return ImageGpuData(images)
    }

    /** Must be called on the GL thread. */
    fun draw(
        gl: GlState,
        program: ShaderProgram,
        gpu: ImageGpuData,
        cache: TextureCache,
        camera: SkyCamera,
        viewport: Viewport,
        viewProj: FloatArray,
        nightMode: Boolean,
        emptyVao: Int,
    ) {
        if (gpu.images.isEmpty()) return
        gl.useProgram(program)
        // Blended, not alpha-tested (D85): an alpha test quantises the limb to a hard cutoff,
        // which is what gave every disc its stair-stepped edge. Correct back-to-front order
        // within the layer is guaranteed by D18's distance sort in SolarSystemLayer.
        gl.blend(GlState.BlendMode.ALPHA)
        GLES30.glUniformMatrix4fv(program.uniform("uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform1f(program.uniform("uNightMode"), if (nightMode) 1f else 0f)
        GLES30.glUniform1i(program.uniform("uTexture"), 0)
        // Quads come from gl_VertexID, so no vertex array is needed at all — but GL ES 3.0
        // still requires *some* vertex array object to be bound for the draw to be valid.
        gl.bindVertexArray(emptyVao)

        val fovDeg = camera.fovDeg
        val shortSidePx = min(viewport.widthPx, viewport.heightPx)
        for (image in gpu.images) {
            // Size and cull both depend on the field of view, so both belong here rather than in
            // the producer, whose resubmission cadence cannot follow a pinch (D86).
            val below = image.visibleBelowFovDeg
            if (below != null && fovDeg > below) continue
            val textureId = cache.textureId(image.image)
            if (textureId == 0) continue

            val drawnDeg =
                SizeFloor.drawnDiameterDeg(
                    image.angularSizeDeg,
                    image.minScreenFraction,
                    image.minSizeDp,
                    fovDeg,
                    shortSidePx,
                    viewport.density,
                )
            val axes = halfAxes(image, drawnDeg)
            GLES30.glUniform3f(
                program.uniform("uCenter"),
                image.center.x.toFloat(),
                image.center.y.toFloat(),
                image.center.z.toFloat(),
            )
            GLES30.glUniform3f(
                program.uniform("uHalfU"),
                axes.u.x.toFloat(),
                axes.u.y.toFloat(),
                axes.u.z.toFloat(),
            )
            GLES30.glUniform3f(
                program.uniform("uHalfV"),
                axes.v.x.toFloat(),
                axes.v.y.toFloat(),
                axes.v.z.toFloat(),
            )
            uploadTerminator(program, image)
            gl.bindTexture(textureId)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    /**
     * Pushes the phase and any Earth-shadow geometry as uniforms — the whole of what
     * `PhaseCompositor` does on the CPU, minus the bitmap and the re-upload.
     */
    private fun uploadTerminator(
        program: ShaderProgram,
        image: ImagePrimitive,
    ) {
        val terminator = image.terminator
        if (terminator == null) {
            GLES30.glUniform1f(program.uniform("uHasTerminator"), 0f)
            GLES30.glUniform1f(program.uniform("uHasEclipse"), 0f)
            return
        }
        GLES30.glUniform1f(program.uniform("uHasTerminator"), 1f)
        GLES30.glUniform1f(
            program.uniform("uIlluminatedFraction"),
            terminator.illuminatedFraction.toFloat(),
        )
        // The lit limb's direction in texture space. rotationDeg has already turned the quad so
        // +y is the body's north pole, and the texture frame is a view of the sky from inside,
        // so east runs to −x: a position angle east of north is (−sin, cos) here.
        val chi = terminator.brightLimbAngleDeg * DEGREES_TO_RADIANS
        GLES30.glUniform2f(
            program.uniform("uLitDirection"),
            (-sin(chi)).toFloat(),
            cos(chi).toFloat(),
        )

        val eclipse = terminator.eclipse
        if (eclipse == null) {
            GLES30.glUniform1f(program.uniform("uHasEclipse"), 0f)
            return
        }
        GLES30.glUniform1f(program.uniform("uHasEclipse"), 1f)
        GLES30.glUniform2f(
            program.uniform("uShadowRadii"),
            eclipse.umbraRadius.toFloat(),
            eclipse.penumbraRadius.toFloat(),
        )
        val shadowChi = eclipse.directionDeg * DEGREES_TO_RADIANS
        GLES30.glUniform2f(
            program.uniform("uShadowCenter"),
            (eclipse.offset * -sin(shadowChi)).toFloat(),
            (eclipse.offset * cos(shadowChi)).toFloat(),
        )
    }

    /** Drops this data's claim on its textures. They stay cached for the next scene. */
    fun release(
        cache: TextureCache,
        gpu: ImageGpuData,
    ) {
        for (image in gpu.images) cache.release(image.image)
    }
}
