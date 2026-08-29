/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.SizeFloor
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.Viewport
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Retained texture keys and static per-image data for one layer's image primitives.
 *
 * Vertices are *not* here: D86's size floor depends on the field of view, so the quad corners are
 * recomputed every frame in [ImageDrawer.draw] rather than baked at build time. That costs four
 * vertices per image on a list that is never more than a dozen long, and it is what lets a disc
 * track a continuous pinch instead of the producer's ~1 Hz resubmission.
 */
internal class ImageGpuData(
    val images: List<ImagePrimitive>,
    // 4 verts × 2 floats × imageCount, concat
    val texCoords: FloatBuffer,
    // one key per image, parallel to the quads; the textures live in the shared TextureCache
    val keys: Array<TextureKey>,
) {
    /** Reused across frames so a steady-state frame allocates nothing. */
    val vertices: FloatBuffer = directFloatBuffer(images.size * 4 * 3)
}

/**
 * Builds and draws [ImagePrimitive]s as textured `GL_TRIANGLE_STRIP` quads in world space.
 *
 * Each image is a planar quad on the celestial sphere, with four corners computed from
 * [ImagePrimitive.center], [ImagePrimitive.angularSizeDeg], and [ImagePrimitive.rotationDeg]
 * (see [quadCorners]). Textures come from the shared [TextureCache], which holds a normal and a
 * red-shifted variant per [ImageRef]; the backend selects between them at draw time via
 * [RenderState.nightMode], mirroring v1's `ImageObjectManager` dual-texture technique.
 *
 * Images the cache cannot load are silently skipped (texture ID 0); this satisfies D24's
 * "a texture/image decode failure skips that image and renders the rest" requirement.
 */
internal object ImageDrawer {
    private val WORLD_UP = Vector3.UNIT_Y
    private val WORLD_UP_FALLBACK = Vector3.UNIT_Z

    /**
     * Computes the four world-space corner positions of [image]'s quad in
     * `[lowerLeft, upperLeft, lowerRight, upperRight]` order (GL_TRIANGLE_STRIP winding).
     *
     * The local coordinate frame at [ImagePrimitive.center]:
     * - `horizontal = −(center × WORLD_UP).normalized` (right on screen by convention)
     * - `vertical = horizontal × center` (up on screen by convention)
     *
     * [ImagePrimitive.rotationDeg] rotates the frame around the center; the half-axis scale is
     * `sin(drawnDiameterDeg / 2)` world-units (the chord half-length on the unit sphere, so a
     * 10° image covers 10° of sky). [drawnDiameterDeg] is the floored size from [SizeFloor], not
     * necessarily the primitive's true one. Falls back to [WORLD_UP_FALLBACK] when the cross
     * product with [WORLD_UP] has near-zero squared length (center nearly collinear with the
     * Y-pole).
     */
    internal fun quadCorners(
        image: ImagePrimitive,
        drawnDiameterDeg: Double = image.angularSizeDeg,
    ): Array<Vector3> {
        val center = image.center
        var cross = center cross WORLD_UP
        // sin²(8°) ≈ 0.01937: fall back when center is within 8° of the Y-pole (D30).
        if (cross.length2 < 0.01937) cross = center cross WORLD_UP_FALLBACK
        val horizontal = -cross.normalized()
        val vertical = horizontal cross center

        val rotRad = image.rotationDeg * DEGREES_TO_RADIANS
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)
        val u = horizontal * cosR + vertical * sinR
        val v = horizontal * (-sinR) + vertical * cosR

        val scale = sin(drawnDiameterDeg / 2.0 * DEGREES_TO_RADIANS)
        val uu = u * scale
        val vv = v * scale

        return arrayOf(
            // lower left
            center - uu - vv,
            // upper left
            center - uu + vv,
            // lower right
            center + uu - vv,
            // upper right
            center + uu + vv,
        )
    }

    /**
     * Builds the texcoord array for [images] and retains each one's texture in [cache], which
     * uploads any that are not already resident. Quad vertices are built per frame in [draw],
     * since their size depends on the field of view. Must be called on the GL thread.
     */
    fun build(
        gl: GL10,
        images: List<ImagePrimitive>,
        cache: TextureCache,
    ): ImageGpuData {
        val n = images.size
        val texCoords = directFloatBuffer(n * 4 * 2)
        repeat(n) {
            // Tex coords per corner: (u, v) with u right, v up (GL convention).
            texCoords.put(0f).put(1f) // lower left
            texCoords.put(0f).put(0f) // upper left
            texCoords.put(1f).put(1f) // lower right
            texCoords.put(1f).put(0f) // upper right
        }
        texCoords.rewind()

        // The phase is part of the key: the terminator is painted into the texture (D88), so two
        // scenes agree on a texture only if they agree on the phase as well as the image.
        val keys = Array(n) { TextureKey(images[it].image, PhaseKey.of(images[it].terminator)) }
        for (key in keys) cache.retain(gl, key)
        return ImageGpuData(images, texCoords, keys)
    }

    /**
     * Draws each image as a `GL_TRIANGLE_STRIP` quad, at the size [SizeFloor] gives it for the
     * current field of view; selects the red-shifted texture from [cache] when [nightMode] is
     * true. Images with texture ID 0 (failed loads) are silently skipped, as are those whose
     * [ImagePrimitive.visibleBelowFovDeg] the camera has not yet reached. Must be called on the
     * GL thread.
     */
    fun draw(
        gl: GL10,
        gpu: ImageGpuData,
        cache: TextureCache,
        camera: SkyCamera,
        viewport: Viewport,
        nightMode: Boolean,
    ) {
        if (gpu.keys.isEmpty()) return
        // Size and cull first: both depend on the field of view, so both belong here rather than
        // in the producer, whose resubmission cadence cannot follow a pinch (D86).
        val fovDeg = camera.fovDeg
        val shortSidePx = min(viewport.widthPx, viewport.heightPx)
        val visible = BooleanArray(gpu.images.size)
        gpu.vertices.clear()
        for (i in gpu.images.indices) {
            val image = gpu.images[i]
            val below = image.visibleBelowFovDeg
            if (below != null && fovDeg > below) continue
            visible[i] = true
            val drawnDeg =
                SizeFloor.drawnDiameterDeg(
                    image.angularSizeDeg,
                    image.minScreenFraction,
                    image.minSizeDp,
                    fovDeg,
                    shortSidePx,
                    viewport.density,
                )
            for (corner in quadCorners(image, drawnDeg)) {
                gpu.vertices
                    .put(corner.x.toFloat())
                    .put(corner.y.toFloat())
                    .put(corner.z.toFloat())
            }
        }
        gpu.vertices.rewind()
        if (visible.none { it }) return

        gl.glEnable(GL10.GL_TEXTURE_2D)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glColor4f(1f, 1f, 1f, 1f)
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, gpu.vertices)
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, gpu.texCoords)

        // Blended, not alpha-tested (D85, closing D30's deferral). The alpha test this replaces
        // quantised the limb to a hard 0.5 cutoff, which is what gave every disc its stair-
        // stepped edge; blending against the standard SRC_ALPHA/ONE_MINUS_SRC_ALPHA func set in
        // GLSkyRenderer.onSurfaceCreated renders the feathered limb as authored. Correct
        // back-to-front order within the layer is guaranteed by D18's descending-Earth-distance
        // sort in SolarSystemLayer.buildScene.
        // The buffer is packed with the visible images only, so it has its own running index.
        var slot = 0
        for (i in gpu.keys.indices) {
            if (!visible[i]) continue
            val quad = slot++
            val texId = cache.textureId(gl, gpu.keys[i], nightMode)
            if (texId == 0) continue
            gl.glBindTexture(GL10.GL_TEXTURE_2D, texId)
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 4 * quad, 4)
        }

        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glDisable(GL10.GL_TEXTURE_2D)
    }

    /**
     * Drops [gpu]'s claim on its textures. They stay in [cache] for the next scene that wants
     * them, and are deleted only under budget pressure.
     */
    fun release(
        cache: TextureCache,
        gpu: ImageGpuData,
    ) {
        for (key in gpu.keys) cache.release(key)
    }
}
