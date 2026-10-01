/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.render.api.GroundRamp
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Ties the horizon ramp's floor to the tightest zoom the app allows.
 *
 * These two constants live in different modules and nothing connected them, which is how a floor
 * of 1e-3° shipped: it is below a pixel at ordinary fields of view, so it looks harmless, and it
 * only misbehaves past about 1° where it — rather than `fwidth` — starts deciding the band width.
 * At full zoom that produced a ~70px blend, the exact artifact deriving the ramp from `fwidth`
 * exists to prevent.
 *
 * `:render:api` cannot depend on the app, so the relationship is asserted from this side.
 */
class HorizonEdgeSharpnessTest {
    /**
     * The largest short side worth planning for — a big tablet.
     *
     * Deliberately the *strictest* choice rather than a typical phone: a wider screen means a
     * smaller per-pixel angle, so it leaves the floor the least headroom. Testing against 1080
     * asserts the loosest case while the comment claims to cover every device.
     */
    private val widestShortSidePx = 1600

    /**
     * Conservative by construction: the shader uses `fwidth`, which is `|dFdx| + |dFdy|` and so up
     * to about 1.4x the per-pixel angle on a diagonal horizon. Using the per-pixel angle
     * understates the real derivative, erring toward the floor binding sooner than it will.
     */
    private fun bandWidthPx(fovDeg: Double): Double {
        val perPixelDeg = fovDeg / widestShortSidePx
        val halfWidthDeg =
            perPixelDeg.coerceIn(
                GroundRamp.EDGE_RAMP_MIN_DEG,
                GroundRamp.EDGE_RAMP_DEG,
            )
        return 2.0 * halfWidthDeg / perPixelDeg
    }

    @Test
    fun `the floor never binds at any zoom the app allows, so the derivative always decides`() {
        // This is the property that matters, and the one the 1e-3 floor broke. The floor exists
        // only to keep smoothstep defined when fwidth returns zero; the moment it exceeds the
        // per-pixel angle it starts setting the band width itself, and at full zoom that was a
        // ~70px blend. Below the per-pixel angle at maximum zoom it can never do that.
        val perPixelAtMaxZoom = MapViewModel.MIN_FOV_DEG / widestShortSidePx
        assertThat(GroundRamp.EDGE_RAMP_MIN_DEG).isLessThan(perPixelAtMaxZoom)
    }

    @Test
    fun `the floor cannot collapse toward zero, where smoothstep is undefined again`() {
        // The other side of the window, and the one a bare isGreaterThan(0.0) leaves open: a floor
        // of 1e-30 satisfies every upper bound while sitting below fp32 resolution and inside
        // denormal range, where GPUs flush to zero. That restores edge0 == edge1 -- the undefined
        // smoothstep this constant exists to prevent.
        //
        // Deliberately looser than the ~3e-6 degrees of fp32 noise `asin(dot)` carries near the
        // horizon. Asserting that figure directly would put the bound at ~1e-5 -- the constant's
        // current value -- leaving a window of [1e-5, 1.9e-5] with the constant sitting on its own
        // lower edge, which fails the moment anyone nudges it in the safe direction. The noise
        // figure is a design note on the constant; this is the assertion that catches the bug.
        assertThat(GroundRamp.EDGE_RAMP_MIN_DEG).isAtLeast(1e-6)
    }

    @Test
    fun `the horizon edge stays a couple of pixels wide at every zoom the app allows`() {
        for (fovDeg in listOf(MapViewModel.MIN_FOV_DEG, 0.1, 1.0, 5.0, 45.0, 90.0)) {
            assertThat(bandWidthPx(fovDeg)).isAtMost(4.0)
        }
    }
}
