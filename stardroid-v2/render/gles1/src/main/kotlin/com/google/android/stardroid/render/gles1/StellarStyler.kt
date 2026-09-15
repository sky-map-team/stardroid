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

/** The resolved screen-space appearance of a point, ready to feed a vertex/color buffer. */
data class StarAppearance(val color: Rgba, val sizePx: Float)

/**
 * Backend-owned mapping from domain point appearance to screen pixels (D12):
 * magnitude is the lossless form, pixels are this backend's lossy projection of it — GLES1 can
 * only bake brightness into vertex color and a couple of discrete sizes, not anti-aliased PSFs.
 * Point size is near-constant in screen space (zooming must never turn stars into blobs); only the
 * brightest few stars get a modest size boost, brightness is otherwise conveyed by color/alpha.
 */
object StellarStyler {
    private const val BASE_SIZE_DP = 3f
    private const val BRIGHT_SIZE_DP = 5f

    /** Stars at or brighter than this magnitude get [BRIGHT_SIZE_DP] instead of [BASE_SIZE_DP]. */
    private const val BRIGHT_MAGNITUDE_THRESHOLD = 1.0

    /** Magnitudes past [RenderState.magnitudeLimit] over which a star fades out, not pops. */
    private const val FADE_RANGE_MAGNITUDES = 0.5

    /** Alpha floor so a fading star near the magnitude limit never fully disappears mid-fade. */
    private const val FAINT_ALPHA_FLOOR = 0.15f

    /** v1 `StarAttributeCalculator.MAX_MAGNITUDE`: the shade ramp's faint end. */
    private const val MAX_SHADED_MAGNITUDE = 5.6

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

    /** Shared night-mode red transform (D12); also reused for line colors. */
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
        val isBright = stellar.magnitude <= BRIGHT_MAGNITUDE_THRESHOLD
        val sizeDp = if (isBright) BRIGHT_SIZE_DP else BASE_SIZE_DP
        val alpha = magnitudeAlpha(stellar.magnitude, state.magnitudeLimit)
        val shade = magnitudeShade(stellar.magnitude)
        val tint = colorForIndex(stellar.colorIndex)
        val shaded = Rgba(tint.r * shade, tint.g * shade, tint.b * shade, alpha)
        val color = applyNightMode(shaded, state.nightMode)
        return StarAppearance(color, sizeDp * density)
    }

    /**
     * v1 `StarAttributeCalculator.getColor`'s brightness ramp: full at magnitude ≤ 0, falling
     * linearly by `mag / (5.6 + 3)`. v1's data cap made anything fainter than 5.6 vanish; v2
     * catalogs may carry fainter stars, so the shade clamps at the cap's value (~0.35) instead.
     */
    private fun magnitudeShade(magnitude: Double): Float {
        val m = magnitude.coerceIn(0.0, MAX_SHADED_MAGNITUDE)
        return (1.0 - m / (MAX_SHADED_MAGNITUDE + 3.0)).toFloat()
    }

    /** 1.0 within the limit, fading to [FAINT_ALPHA_FLOOR]..0 over [FADE_RANGE_MAGNITUDES]. */
    private fun magnitudeAlpha(
        magnitude: Double,
        magnitudeLimit: Double?,
    ): Float {
        if (magnitudeLimit == null) return 1f
        val overshoot = magnitude - magnitudeLimit
        if (overshoot <= 0.0) return 1f
        if (overshoot >= FADE_RANGE_MAGNITUDES) return 0f
        val t = (1.0 - overshoot / FADE_RANGE_MAGNITUDES).toFloat()
        return FAINT_ALPHA_FLOOR + (1f - FAINT_ALPHA_FLOOR) * t
    }

    /**
     * Maps a B-V color index (negative = blue-hot, positive = red-cool) to an approximate tint via
     * a blue -> white -> orange-red ramp. B-V outside roughly [-0.4, 2.0] is astrophysically rare,
     * so the ramp clamps there; a missing index renders white.
     */
    private fun colorForIndex(colorIndex: Double?): Rgba {
        if (colorIndex == null) return Rgba.WHITE
        val t = ((colorIndex + 0.4) / 2.4).coerceIn(0.0, 1.0)
        return if (t < 0.5) {
            lerp(Rgba(0.65f, 0.75f, 1f), Rgba.WHITE, (t / 0.5).toFloat())
        } else {
            lerp(Rgba.WHITE, Rgba(1f, 0.7f, 0.4f), ((t - 0.5) / 0.5).toFloat())
        }
    }

    private fun lerp(
        a: Rgba,
        b: Rgba,
        t: Float,
    ): Rgba =
        Rgba(
            r = a.r + (b.r - a.r) * t,
            g = a.g + (b.g - a.g) * t,
            b = a.b + (b.b - a.b) * t,
            a = a.a + (b.a - a.a) * t,
        )
}
