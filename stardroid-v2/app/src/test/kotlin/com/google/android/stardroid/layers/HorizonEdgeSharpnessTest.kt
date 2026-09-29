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
    /** Comfortably below any real device; a wider screen only makes the per-pixel angle smaller. */
    private val narrowScreenPx = 1080

    private fun bandWidthPx(fovDeg: Double): Double {
        val perPixelDeg = fovDeg / narrowScreenPx
        val halfWidthDeg = perPixelDeg.coerceIn(GroundRamp.EDGE_RAMP_MIN_DEG, GroundRamp.EDGE_RAMP_DEG)
        return 2.0 * halfWidthDeg / perPixelDeg
    }

    @Test
    fun `the floor never binds at any zoom the app allows, so the derivative always decides`() {
        // This is the property that matters, and the one the 1e-3 floor broke. The floor exists
        // only to keep smoothstep defined when fwidth returns zero; the moment it exceeds the
        // per-pixel angle it starts setting the band width itself, and at full zoom that was a
        // ~70px blend. Below the per-pixel angle at maximum zoom it can never do that.
        val perPixelAtMaxZoom = MapViewModel.MIN_FOV_DEG / narrowScreenPx
        assertThat(GroundRamp.EDGE_RAMP_MIN_DEG).isLessThan(perPixelAtMaxZoom)
        assertThat(GroundRamp.EDGE_RAMP_MIN_DEG).isGreaterThan(0.0)
    }

    @Test
    fun `the horizon edge stays a couple of pixels wide at every zoom the app allows`() {
        for (fovDeg in listOf(MapViewModel.MIN_FOV_DEG, 0.1, 1.0, 5.0, 45.0, 90.0)) {
            assertThat(bandWidthPx(fovDeg)).isAtMost(4.0)
        }
    }

    @Test
    fun `the cap only binds at absurdly wide fields, never in normal use`() {
        // The cap is a safety net, not a working value: at any field of view the app offers, the
        // derivative should be what decides the width.
        val perPixelAtWidest = 90.0 / narrowScreenPx
        assertThat(perPixelAtWidest).isLessThan(GroundRamp.EDGE_RAMP_DEG)
    }
}
