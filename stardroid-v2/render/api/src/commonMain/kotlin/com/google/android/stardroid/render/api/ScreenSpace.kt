/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Screen-space arithmetic for the drawers that project sky positions on the CPU — icons and
 * labels — rather than letting a vertex shader do it. Shared so the two cannot drift (a label is
 * placed relative to the thing it names), and so every backend agrees (D128). Transcribed from
 * `:render:gles3`'s ScreenSpace.
 */
object ScreenSpace {
    /** v1's `MAGIC_OFFSET`: snapping to a half-open pixel centre reduces texture aliasing. */
    const val PIXEL_SNAP = 0.25f

    /**
     * The minimum `pos · lineOfSight` for a sky position to be worth projecting.
     *
     * From v1's `LabelObjectManager.beginDrawing`, but scaled by the long/short side ratio:
     * `fovDeg` spans the **short** viewport side, so v1's raw width/height aspect under-covered
     * the long axis in portrait and labels vanished as they approached the top and bottom edges.
     */
    fun frustumDotThreshold(
        camera: SkyCamera,
        viewport: Viewport,
    ): Float {
        val longSideRatio =
            max(viewport.widthPx, viewport.heightPx).toFloat() /
                min(viewport.widthPx, viewport.heightPx).coerceAtLeast(1)
        val halfFovRad = camera.fovDeg * DEGREES_TO_RADIANS * 0.5
        val thresholdAngleRad = (halfFovRad * (1.0 + longSideRatio)).coerceAtMost(PI)
        return cos(thresholdAngleRad).toFloat()
    }

    /** Pixels per degree at the screen centre; [SkyCamera.fovDeg] spans the short side. */
    fun pixelsPerDegree(
        camera: SkyCamera,
        viewport: Viewport,
    ): Double {
        val halfFovRad = camera.fovDeg * DEGREES_TO_RADIANS * 0.5
        val shortSidePx = min(viewport.widthPx, viewport.heightPx)
        return (shortSidePx * 0.5 / tan(halfFovRad)) * DEGREES_TO_RADIANS
    }
}
