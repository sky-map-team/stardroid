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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Per-channel darkening at a point on the Moon's disc caused by an [EclipseShadow] — pure
 * geometry, no pixels or Android, for the same reason [PhaseGeometry] is: the GLES1 backend's
 * CPU compositor and a future GLES3 fragment shader both need to evaluate exactly this function
 * (D106).
 *
 * The umbra darkens *and* reddens — sunlight reaching it has been Rayleigh-scattered through
 * Earth's atmosphere, the same reason sunsets are red — while the penumbra only dims, since that
 * light is direct sunlight simply reduced by Earth partially blocking the Sun's disc, with no
 * filtering to shift its colour.
 */
object EclipseGeometry {
    /** Multipliers for each colour channel; [NONE] means "no eclipse effect at this point". */
    data class Tint(val red: Double, val green: Double, val blue: Double) {
        companion object {
            val NONE = Tint(1.0, 1.0, 1.0)
        }
    }

    /** Per-channel multiplier at the umbra's outer edge, where totality has only just begun. */
    private const val UMBRA_EDGE_RED = 0.55
    private const val UMBRA_EDGE_GREEN = 0.22
    private const val UMBRA_EDGE_BLUE = 0.16

    /** Per-channel multiplier at the umbra's centre — deepest, reddest point (Danjon L≈2). */
    private const val UMBRA_CORE_RED = 0.30
    private const val UMBRA_CORE_GREEN = 0.06
    private const val UMBRA_CORE_BLUE = 0.04

    /** How much the penumbra dims at its inner edge (the umbra boundary); no colour shift. */
    private const val PENUMBRA_MAX_DIMMING = 0.35

    /**
     * The tint at disc position ([x], [y]) — the same unit-disc frame [PhaseGeometry.litOffset]
     * works in — for a Moon shadowed by [shadow]. [Tint.NONE] outside both the umbra and the
     * penumbra.
     */
    fun tint(
        x: Double,
        y: Double,
        shadow: EclipseShadow,
    ): Tint {
        val chi = shadow.directionDeg * DEGREES_TO_RADIANS
        val shadowX = shadow.offset * -sin(chi)
        val shadowY = shadow.offset * cos(chi)
        val dist = hypot(x - shadowX, y - shadowY)
        return when {
            dist <= shadow.umbraRadius -> {
                // 0 at the umbra's edge, 1 at its centre.
                val depth =
                    if (shadow.umbraRadius > 0.0) {
                        (1.0 - dist / shadow.umbraRadius).coerceIn(0.0, 1.0)
                    } else {
                        1.0
                    }
                Tint(
                    lerp(UMBRA_EDGE_RED, UMBRA_CORE_RED, depth),
                    lerp(UMBRA_EDGE_GREEN, UMBRA_CORE_GREEN, depth),
                    lerp(UMBRA_EDGE_BLUE, UMBRA_CORE_BLUE, depth),
                )
            }
            dist < shadow.penumbraRadius -> {
                // 0 at the penumbra's outer edge, 1 at the umbra's edge.
                val span = shadow.penumbraRadius - shadow.umbraRadius
                val depth =
                    if (span > 0.0) {
                        ((shadow.penumbraRadius - dist) / span).coerceIn(0.0, 1.0)
                    } else {
                        1.0
                    }
                val dim = 1.0 - PENUMBRA_MAX_DIMMING * depth
                Tint(dim, dim, dim)
            }
            else -> Tint.NONE
        }
    }

    /** Linear interpolation from [a] at `t = 0` to [b] at `t = 1`. */
    private fun lerp(
        a: Double,
        b: Double,
        t: Double,
    ): Double = a + (b - a) * t
}
