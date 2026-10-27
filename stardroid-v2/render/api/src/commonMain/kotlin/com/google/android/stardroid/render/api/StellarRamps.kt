/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

/**
 * How a star's magnitude and colour index become a drawn size, brightness and tint (D12).
 *
 * These four functions are the whole of the stellar appearance model, and they live here —
 * pure, backend-agnostic, unit-tested — because *both* backends need them and they mean
 * different things in each. `:render:gles1` evaluates them on the CPU and bakes the answer into
 * a vertex colour, which is why changing `RenderState.magnitudeLimit` invalidates its entire
 * point buffer. `:render:gles3` transcribes them into `point.vert`/`point.frag` and evaluates
 * them per vertex from a `(magnitude, colorIndex)` attribute pair, so the same two fields become
 * uniforms and invalidate nothing.
 *
 * That transcription is the risk: GLSL copies of these functions can silently drift from the
 * Kotlin. This object is therefore the **golden reference** — the GLES3 conformance test renders
 * each function through its shader as a 1-D lookup and asserts it matches what is here. Change
 * a constant below and the shader must change with it, or CI says so.
 */
object StellarRamps {
    /** Screen size of an ordinary star, in dp. Near-constant so zoom never makes blobs. */
    const val BASE_SIZE_DP = 3f

    /** Screen size of a star at or brighter than [BRIGHT_MAGNITUDE_THRESHOLD], in dp. */
    const val BRIGHT_SIZE_DP = 5f

    /** Stars at or brighter than this magnitude get [BRIGHT_SIZE_DP]. */
    const val BRIGHT_MAGNITUDE_THRESHOLD = 1.0

    /** Magnitudes past `RenderState.magnitudeLimit` over which a star fades out, not pops. */
    const val FADE_RANGE_MAGNITUDES = 0.5

    /** Alpha floor so a fading star near the magnitude limit never fully disappears mid-fade. */
    const val FAINT_ALPHA_FLOOR = 0.15f

    /** v1 `StarAttributeCalculator.MAX_MAGNITUDE`: the shade ramp's faint end. */
    const val MAX_SHADED_MAGNITUDE = 5.6

    /** The drawn diameter in dp for a star of [magnitude]. */
    fun sizeDp(magnitude: Double): Float =
        if (magnitude <= BRIGHT_MAGNITUDE_THRESHOLD) BRIGHT_SIZE_DP else BASE_SIZE_DP

    /**
     * v1 `StarAttributeCalculator.getColor`'s brightness ramp: full at magnitude ≤ 0, falling
     * linearly by `mag / (5.6 + 3)`. v1's data cap made anything fainter than 5.6 vanish; v2
     * catalogs may carry fainter stars, so the shade clamps at the cap's value (~0.35) instead.
     */
    fun magnitudeShade(magnitude: Double): Float {
        val m = magnitude.coerceIn(0.0, MAX_SHADED_MAGNITUDE)
        return (1.0 - m / (MAX_SHADED_MAGNITUDE + 3.0)).toFloat()
    }

    /** 1.0 within the limit, fading to [FAINT_ALPHA_FLOOR]..0 over [FADE_RANGE_MAGNITUDES]. */
    fun magnitudeAlpha(
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
    fun colorForIndex(colorIndex: Double?): Rgba {
        if (colorIndex == null) return Rgba.WHITE
        val t = ((colorIndex + 0.4) / 2.4).coerceIn(0.0, 1.0)
        return if (t < 0.5) {
            lerp(BLUE_END, Rgba.WHITE, (t / 0.5).toFloat())
        } else {
            lerp(Rgba.WHITE, RED_END, ((t - 0.5) / 0.5).toFloat())
        }
    }

    /** The blue-hot end of the B−V ramp. */
    val BLUE_END = Rgba(0.65f, 0.75f, 1f)

    /** The red-cool end of the B−V ramp. */
    val RED_END = Rgba(1f, 0.7f, 0.4f)

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
