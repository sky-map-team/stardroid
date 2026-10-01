/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.StellarRamps

/** The resolved screen-space appearance of a point, ready to feed a vertex/color buffer. */
data class StarAppearance(val color: Rgba, val sizePx: Float)

/**
 * Backend-owned mapping from domain point appearance to screen pixels (D12):
 * magnitude is the lossless form, pixels are this backend's lossy projection of it — GLES1 can
 * only bake brightness into vertex color and a couple of discrete sizes, not anti-aliased PSFs.
 *
 * The ramps themselves live in [StellarRamps] in `:render:api`, shared with the GLES3 backend
 * (which evaluates them in a shader instead). What stays here is the *baking*: resolving them to
 * one colour and one size per vertex, which is the part that makes `magnitudeLimit` and night
 * mode invalidate this backend's point buffers.
 */
object StellarStyler {
    fun style(
        appearance: PointAppearance,
        state: RenderState,
        density: Float,
    ): StarAppearance =
        when (appearance) {
            is PointAppearance.Stellar -> styleStellar(appearance, state, density)
            is PointAppearance.Fixed ->
                StarAppearance(
                    applyNightMode(appearance.color, state.nightMode),
                    (appearance.sizeDp * density).toFloat(),
                )
            is PointAppearance.Icon ->
                error("Icon points are textured quads drawn by IconDrawer, not styled dots")
        }

    /**
     * Shared night-mode red transform (D12); also reused for line colors.
     *
     * Stays GLES1-only: the GLES3 backend applies the same transform as a colour-transform
     * uniform in every fragment shader, which is what lets it hold one texture per image instead
     * of a second red-shifted copy.
     */
    fun applyNightMode(
        color: Rgba,
        nightMode: Boolean,
    ): Rgba {
        if (!nightMode) return color
        val luminance = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b
        return color.copy(r = luminance, g = 0f, b = 0f)
    }

    private fun styleStellar(
        stellar: PointAppearance.Stellar,
        state: RenderState,
        density: Float,
    ): StarAppearance {
        val sizeDp = StellarRamps.sizeDp(stellar.magnitude)
        val alpha = StellarRamps.magnitudeAlpha(stellar.magnitude, state.magnitudeLimit)
        val shade = StellarRamps.magnitudeShade(stellar.magnitude)
        val tint = StellarRamps.colorForIndex(stellar.colorIndex)
        val shaded = Rgba(tint.r * shade, tint.g * shade, tint.b * shade, alpha)
        val color = applyNightMode(shaded, state.nightMode)
        return StarAppearance(color, sizeDp * density)
    }
}
