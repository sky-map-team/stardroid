/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import kotlin.math.floor

/** One icon ready to draw: sky anchor, resolved size, tint, and its texture slot. */
data class IconSprite(
    val pos: Vector3,
    val sizePx: Float,
    /** Index into [IconGpuData.refs]; sprites are sorted by this so each texture binds once. */
    val textureIndex: Int,
    val tint: Rgba,
)

/** Distinct texture refs (in first-seen order) and the sprites that use them, for one layer. */
class IconGpuData(
    val refs: List<ImageRef>,
    val sprites: List<IconSprite>,
)

/**
 * Deep-sky type markers and meteor-shower radiants: screen-space textured quads anchored at a
 * sky position, drawn at a fixed dp size so they do not inflate with zoom (D12).
 *
 * Where GLES1 issues a matrix push and a draw per sprite, this fills a [SpriteBatch] and draws
 * one instanced call per texture. Icons are never phased — they are markers, not bodies catching
 * sunlight — so their texture key is the bare [ImageRef].
 */
object IconDrawer {
    /** Claims textures and resolves sizes. Must be called on the GL thread. */
    fun build(
        points: List<PointPrimitive>,
        cache: TextureCache,
        density: Float,
    ): IconGpuData {
        val icons =
            points.mapNotNull { p ->
                (p.appearance as? PointAppearance.Icon)?.let { Pair(p.pos, it) }
            }
        if (icons.isEmpty()) return IconGpuData(emptyList(), emptyList())

        val refIndices = LinkedHashMap<ImageRef, Int>()
        for ((_, icon) in icons) refIndices.getOrPut(icon.image) { refIndices.size }
        val refs = refIndices.keys.toList()
        for (ref in refs) cache.retain(ref)

        val sprites =
            icons
                .map { (pos, icon) ->
                    IconSprite(
                        pos = pos,
                        sizePx = (icon.sizeDp * density).toFloat(),
                        textureIndex = refIndices.getValue(icon.image),
                        tint = icon.tint,
                    )
                }
                .sortedBy { it.textureIndex }
        return IconGpuData(refs, sprites)
    }

    /** Must be called on the GL thread. */
    @Suppress("LongParameterList")
    fun draw(
        gl: GlState,
        batch: SpriteBatch,
        gpu: IconGpuData,
        cache: TextureCache,
        camera: SkyCamera,
        projection: SkyProjection,
        viewport: Viewport,
        nightMode: Boolean,
    ) {
        if (gpu.sprites.isEmpty()) return
        val dotThreshold = frustumDotThreshold(camera, viewport)
        val lookDir = camera.lineOfSight
        val width = viewport.widthPx.toFloat()
        val height = viewport.heightPx.toFloat()

        // Sprites arrive sorted by texture, so one pass emits at most one batch per texture.
        var activeIndex = -1
        for (sprite in gpu.sprites) {
            if (sprite.textureIndex != activeIndex) {
                if (activeIndex >= 0) {
                    flush(gl, batch, cache, gpu.refs[activeIndex], width, height, nightMode)
                }
                activeIndex = sprite.textureIndex
            }
            if ((sprite.pos dot lookDir).toFloat() < dotThreshold) continue
            val screen = projection.worldToScreen(sprite.pos) ?: continue
            // Flip to GL's bottom-left origin, and pixel-snap as GLES1 does to reduce aliasing.
            val x = floor(screen.xPx) + PIXEL_SNAP
            val y = floor(height - screen.yPx) + PIXEL_SNAP
            batch.add(
                centerXPx = x,
                centerYPx = y,
                widthPx = sprite.sizePx,
                heightPx = sprite.sizePx,
                uv0 = SpriteBatch.FULL_UV0,
                uv1 = SpriteBatch.FULL_UV1,
                tint = sprite.tint,
                alpha = 1f,
                mode = SpriteBatch.MODE_ICON,
            )
        }
        if (activeIndex >= 0) {
            flush(gl, batch, cache, gpu.refs[activeIndex], width, height, nightMode)
        }
    }

    private fun flush(
        gl: GlState,
        batch: SpriteBatch,
        cache: TextureCache,
        ref: ImageRef,
        widthPx: Float,
        heightPx: Float,
        nightMode: Boolean,
    ) {
        val textureId = cache.textureId(ref)
        // An icon the loader could not resolve is silently skipped (D24).
        if (textureId == 0) {
            batch.clear()
            return
        }
        batch.flush(
            gl = gl,
            textureId = textureId,
            viewportWidthPx = widthPx,
            viewportHeightPx = heightPx,
            texelSize = SpriteBatch.NO_TEXEL,
            nightMode = nightMode,
            haloColor = Rgba.TRANSPARENT,
            haloTexels = 0f,
        )
    }

    /** Drops this data's claim on its textures. */
    fun release(
        cache: TextureCache,
        gpu: IconGpuData,
    ) {
        for (ref in gpu.refs) cache.release(ref)
    }

    /** v1's `MAGIC_OFFSET`: snapping to a half-open pixel centre reduces texture aliasing. */
    const val PIXEL_SNAP = 0.25f
}
