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
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One icon point ready to draw: sky anchor, resolved size, tints, and its texture slot. */
internal data class IconSprite(
    val pos: Vector3,
    val sizePx: Float,
    /** Index into [IconGpuData.textureIds]; sprites are sorted by this to minimize binds. */
    val textureIndex: Int,
    /** Day-mode texture modulate color, and its pre-applied night-mode transform. */
    val tint: Rgba,
    val nightTint: Rgba,
)

/** Retained texture keys (distinct, in first-seen order) and sprites for one layer. */
internal class IconGpuData(
    val keys: Array<TextureKey>,
    val sprites: List<IconSprite>,
)

/**
 * Builds and draws [PointAppearance.Icon] points as **screen-space** textured quads (the render-API
 * addendum in catalog-and-schema.md): each icon is anchored at its projected sky position and drawn
 * [PointAppearance.Icon.sizeDp] square, so it does not scale with zoom — v1's world-space-sized
 * DSO markers inflated with zoom, exactly what D12 disallows.
 *
 * Textures are retained once per distinct [ImageRef] in the scene (v1's per-shape drawables map to
 * few distinct refs) from the shared [TextureCache], in a normal and a red-shifted night-mode
 * variant like [ImageDrawer]. Icons the cache cannot load are silently skipped (texture ID 0, D24).
 */
internal object IconDrawer {
    /** Unit quad vertices (2D), GL_TRIANGLE_STRIP: LL, UL, LR, UR. */
    private val UNIT_QUAD: FloatBuffer =
        directFloatBuffer(8).apply {
            put(-0.5f).put(-0.5f)
            put(-0.5f).put(0.5f)
            put(0.5f).put(-0.5f)
            put(0.5f).put(0.5f)
            rewind()
        }

    /**
     * Full-texture coords with the same negative-crop-height convention as [LabelDrawer]:
     * `GLUtils.texImage2D` uploads bitmaps top-to-bottom, so the lower-left vertex takes v = 1.
     */
    private val QUAD_TEX_COORDS: FloatBuffer =
        directFloatBuffer(8).apply {
            put(0f).put(1f)
            put(0f).put(0f)
            put(1f).put(1f)
            put(1f).put(0f)
            rewind()
        }

    /**
     * Selects the [PointAppearance.Icon] points of [points], retains their textures in [cache],
     * and resolves sizes to pixels. Must be called on the GL thread.
     */
    fun build(
        gl: GL10,
        points: List<PointPrimitive>,
        cache: TextureCache,
        density: Float,
    ): IconGpuData {
        val icons =
            points.mapNotNull { p ->
                (p.appearance as? PointAppearance.Icon)?.let { Pair(p.pos, it) }
            }
        if (icons.isEmpty()) return IconGpuData(emptyArray(), emptyList())

        val refIndices = LinkedHashMap<ImageRef, Int>()
        for ((_, icon) in icons) refIndices.getOrPut(icon.image) { refIndices.size }
        // Insertion-ordered, so index i is the ref that maps to textureIndex i. Icons are never
        // phased -- they are markers, not bodies catching sunlight.
        val keys = refIndices.keys.map { TextureKey(it) }.toTypedArray()
        for (key in keys) cache.retain(gl, key)

        val sprites =
            icons
                .map { (pos, icon) ->
                    IconSprite(
                        pos,
                        (icon.sizeDp * density).toFloat(),
                        refIndices[icon.image]!!,
                        tint = icon.tint,
                        nightTint = StellarStyler.applyNightMode(icon.tint, true),
                    )
                }
                .sortedBy { it.textureIndex }
        return IconGpuData(keys, sprites)
    }

    /**
     * Per-frame draw: frustum-filter, project each sprite to screen, and draw surviving icons as
     * textured quads in a bottom-left-origin orthographic projection (same convention as
     * [LabelDrawer]). Must be called on the GL thread.
     */
    fun draw(
        gl: GL10,
        gpu: IconGpuData,
        cache: TextureCache,
        camera: SkyCamera,
        projection: SkyProjection,
        viewport: Viewport,
        nightMode: Boolean,
    ) {
        if (gpu.sprites.isEmpty()) return

        // Same conservative frustum dot-product pre-filter as LabelDrawer: the cull cone
        // scales by the long/short side ratio because fovDeg spans the short viewport side.
        val lookDir = camera.lineOfSight
        val longSideRatio =
            max(viewport.widthPx, viewport.heightPx).toFloat() /
                min(viewport.widthPx, viewport.heightPx).coerceAtLeast(1)
        val halfFovRad = camera.fovDeg * DEGREES_TO_RADIANS * 0.5
        val thresholdAngleRad = (halfFovRad * (1.0 + longSideRatio)).coerceAtMost(Math.PI)
        val dotThreshold = cos(thresholdAngleRad).toFloat()

        // Resolved once per frame rather than per sprite: a layer has thousands of icons and a
        // handful of distinct refs, and in night mode the first resolution builds the red variant.
        val ids = IntArray(gpu.keys.size) { cache.textureId(gl, gpu.keys[it], nightMode) }

        gl.glTexEnvx(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE)
        gl.glEnable(GL10.GL_TEXTURE_2D)
        gl.glActiveTexture(GL10.GL_TEXTURE0)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glColor4f(1f, 1f, 1f, 1f)

        val w = viewport.widthPx.toFloat()
        val h = viewport.heightPx.toFloat()
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glPushMatrix()
        gl.glLoadIdentity()
        gl.glOrthof(0f, w, 0f, h, -1f, 1f)
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glPushMatrix()
        gl.glLoadIdentity()

        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, UNIT_QUAD)
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, QUAD_TEX_COORDS)

        // Sprites are sorted by textureIndex at build time, so each texture binds at most once.
        // The tint (GL_MODULATE) is cached the same way: most layers share one tint, and
        // redundant glColor4f calls are wasted GL state changes.
        var activeTexture = -1
        var activeTint: Rgba? = null
        for (i in gpu.sprites.indices) {
            val sprite = gpu.sprites[i]
            val texId = ids[sprite.textureIndex]
            if (texId == 0) continue
            val dot =
                (
                    sprite.pos.x * lookDir.x +
                        sprite.pos.y * lookDir.y +
                        sprite.pos.z * lookDir.z
                ).toFloat()
            if (dot < dotThreshold) continue
            val sp = projection.worldToScreen(sprite.pos) ?: continue
            if (texId != activeTexture) {
                gl.glBindTexture(GL10.GL_TEXTURE_2D, texId)
                activeTexture = texId
            }
            val tint = if (nightMode) sprite.nightTint else sprite.tint
            if (tint != activeTint) {
                gl.glColor4f(tint.r, tint.g, tint.b, tint.a)
                activeTint = tint
            }
            // Flip to GL bottom-left origin and pixel-snap like LabelDrawer.
            val snappedX = floor(sp.xPx) + 0.25f
            val snappedY = floor(viewport.heightPx - sp.yPx) + 0.25f
            gl.glPushMatrix()
            gl.glTranslatef(snappedX, snappedY, 0f)
            gl.glScalef(sprite.sizePx, sprite.sizePx, 1f)
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4)
            gl.glPopMatrix()
        }

        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glPopMatrix()
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glPopMatrix()
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glDisable(GL10.GL_TEXTURE_2D)
        gl.glColor4f(1f, 1f, 1f, 1f)
    }

    /** Drops [gpu]'s claim on its textures; they stay in [cache] for the next scene. */
    fun release(
        cache: TextureCache,
        gpu: IconGpuData,
    ) {
        for (key in gpu.keys) cache.release(key)
    }
}
