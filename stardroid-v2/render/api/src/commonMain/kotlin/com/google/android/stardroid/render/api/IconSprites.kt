/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.Vector3
import kotlin.math.floor

/**
 * One layer's icon points — deep-sky type markers, meteor-shower radiants — as screen-space quads
 * anchored at sky positions and drawn at a fixed dp size, so they do not inflate with zoom (D12).
 * Shared by every backend (D117), transcribed from `:render:gles3`'s IconDrawer.
 *
 * [build] resolves sizes and groups the icons by image; [layout] projects them each frame into
 * [SpriteInstances], one run per distinct image, whose texture index is a position in [refs].
 * A backend claims a texture per ref and skips a run whose image is unavailable (D24).
 */
class IconSprites private constructor(
    /** The distinct images, in first-seen order. */
    val refs: List<ImageRef>,
    private val sprites: List<Sprite>,
) {
    private class Sprite(
        val pos: Vector3,
        val sizePx: Float,
        val refIndex: Int,
        val tint: Rgba,
    )

    fun layout(
        camera: SkyCamera,
        projection: SkyProjection,
        viewport: Viewport,
        out: SpriteInstances,
    ) {
        if (sprites.isEmpty()) return
        val dotThreshold = ScreenSpace.frustumDotThreshold(camera, viewport)
        val lookDir = camera.lineOfSight
        val height = viewport.heightPx.toFloat()
        // Sprites are sorted by image, so each image is at most one run, started only once one
        // of its sprites is actually on screen.
        var activeRef = -1
        for (sprite in sprites) {
            if ((sprite.pos dot lookDir).toFloat() < dotThreshold) continue
            val screen = projection.worldToScreen(sprite.pos) ?: continue
            if (sprite.refIndex != activeRef) {
                out.beginRun(sprite.refIndex)
                activeRef = sprite.refIndex
            }
            // Flip to the bottom-left origin, and pixel-snap as GLES1 does to reduce aliasing.
            out.add(
                floor(screen.xPx) + ScreenSpace.PIXEL_SNAP,
                floor(height - screen.yPx) + ScreenSpace.PIXEL_SNAP,
                sprite.sizePx,
                sprite.sizePx,
                0f,
                1f,
                1f,
                0f,
                sprite.tint,
                1f,
                SpriteInstances.MODE_ICON,
            )
        }
    }

    companion object {
        val EMPTY = IconSprites(emptyList(), emptyList())

        fun build(
            points: List<PointPrimitive>,
            density: Float,
        ): IconSprites {
            val icons =
                points.mapNotNull {
                        p ->
                    (p.appearance as? PointAppearance.Icon)?.let { p.pos to it }
                }
            if (icons.isEmpty()) return EMPTY
            val refIndices = LinkedHashMap<ImageRef, Int>()
            for ((_, icon) in icons) refIndices.getOrPut(icon.image) { refIndices.size }
            val sprites =
                icons
                    .map { (pos, icon) ->
                        Sprite(
                            pos,
                            (icon.sizeDp * density).toFloat(),
                            refIndices.getValue(icon.image),
                            icon.tint,
                        )
                    }.sortedBy { it.refIndex }
            return IconSprites(refIndices.keys.toList(), sprites)
        }
    }
}
