/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import kotlin.math.roundToInt

/** A source-space crop region: the largest centred window with the requested aspect. */
data class CropRegion(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** A pixel size. */
data class ShareSize(
    val width: Int,
    val height: Int,
)

/**
 * The size a `sourceWidth × sourceHeight` capture is scaled to before compositing, or null
 * when it already fits and should be used as-is (audit-2026-08 M5).
 *
 * Aspect is preserved and the long edge is capped at [maxEdge]. Split out from
 * `SkyShare.toShareSize` because this arithmetic — not the `Bitmap` plumbing around it — is
 * what decides the resolution of every image the app shares.
 */
fun shareScaledSize(
    sourceWidth: Int,
    sourceHeight: Int,
    maxEdge: Int,
): ShareSize? {
    require(sourceWidth > 0 && sourceHeight > 0 && maxEdge > 0)
    val longEdge = maxOf(sourceWidth, sourceHeight)
    if (longEdge <= maxEdge) return null
    val scale = maxEdge.toDouble() / longEdge
    return ShareSize(
        // Never round down to nothing: an extreme aspect ratio must still yield a drawable
        // bitmap rather than a zero-width one, which Bitmap.createScaledBitmap rejects.
        width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
        height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
    )
}

/**
 * The centre crop of a `sourceWidth × sourceHeight` image at `targetAspect`
 * (width / height) — the same window a FILL_CENTER preview shows, so the camera still
 * crops to exactly what the user saw behind the map (camera-ar-mode.md, slice 4).
 */
fun centerCrop(
    sourceWidth: Int,
    sourceHeight: Int,
    targetAspect: Double,
): CropRegion {
    require(sourceWidth > 0 && sourceHeight > 0 && targetAspect > 0.0)
    val sourceAspect = sourceWidth.toDouble() / sourceHeight
    return if (sourceAspect > targetAspect) {
        // Source too wide: full height, trim the sides.
        val width = (sourceHeight * targetAspect).roundToInt().coerceAtMost(sourceWidth)
        CropRegion((sourceWidth - width) / 2, 0, width, sourceHeight)
    } else {
        // Source too tall: full width, trim top and bottom.
        val height = (sourceWidth / targetAspect).roundToInt().coerceAtMost(sourceHeight)
        CropRegion(0, (sourceHeight - height) / 2, sourceWidth, height)
    }
}
