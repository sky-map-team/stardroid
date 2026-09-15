/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SizeFloorTest {
    private val shortSidePx = 1080
    private val density = 3.0f

    private fun drawn(
        trueDeg: Double,
        fovDeg: Double,
        minScreenFraction: Double = 0.0509,
        minSizeDp: Double = 3.0,
    ) = SizeFloor.drawnDiameterDeg(
        trueDeg,
        minScreenFraction,
        minSizeDp,
        fovDeg,
        shortSidePx,
        density,
    )

    /** Drawn size in screen pixels, which is what any of this is ultimately about. */
    private fun drawnPx(
        trueDeg: Double,
        fovDeg: Double,
        minScreenFraction: Double = 0.0509,
        minSizeDp: Double = 3.0,
    ) = drawn(trueDeg, fovDeg, minScreenFraction, minSizeDp) / fovDeg * shortSidePx

    @Test
    fun `at the default field of view a floored body keeps its old size`() {
        // minScreenFraction is calibrated as todaysSize / defaultFov, so the map is unchanged
        // where people already look: 0.0509 * 45 = 2.29 degrees, v1's Moon.
        assertThat(drawn(0.52, fovDeg = 45.0)).isWithin(1e-9).of(2.2905)
    }

    @Test
    fun `zooming in hands off to the true size without a jump`() {
        val trueDeg = 0.52
        val fraction = 0.0509
        // The crossover: where the fraction floor equals the true diameter.
        val crossover = trueDeg / fraction
        val justAbove = drawn(trueDeg, crossover * 1.001)
        val justBelow = drawn(trueDeg, crossover * 0.999)
        assertThat(justAbove).isWithin(1e-3).of(justBelow)
        // And it is the true size from there down.
        assertThat(drawn(trueDeg, crossover * 0.5)).isWithin(1e-9).of(trueDeg)
    }

    @Test
    fun `a body never shrinks on screen as you zoom in`() {
        // D55's objection to a hard zoom threshold, and the property that actually matters: what
        // the eye judges is *pixels*, not degrees. A floor decaying faster than the field of view
        // passes a degrees-based check while visibly shrinking the disc — that mistake shipped to
        // a test device and was spotted in seconds, which is why this measures pixels.
        for (body in listOf(0.52, 40 / 3600.0, 3.5 / 3600.0)) {
            var previous = 0.0
            var fov = 90.0
            while (fov >= 0.1) {
                val px = drawnPx(body, fov)
                assertThat(px).isAtLeast(previous - 1e-9)
                previous = px
                fov *= 0.97
            }
        }
    }

    @Test
    fun `a floored body holds a constant pixel size, which is what makes it legible`() {
        // While the fractional floor dominates, the disc occupies a fixed share of the screen —
        // v1's behaviour, and the reason zooming feels stable before true scale takes over.
        val jupiter = 40 / 3600.0
        for (fov in listOf(21.1, 10.0, 5.0, 1.0)) {
            assertThat(drawnPx(jupiter, fov)).isWithin(1e-6).of(0.0509 * shortSidePx)
        }
    }

    @Test
    fun `nothing ever falls below the absolute floor, over the whole zoom range`() {
        // Pluto: about a tenth of an arcsecond, the smallest disc the app draws.
        val pluto = 0.1 / 3600.0
        var fov = 90.0
        while (fov >= 0.1) {
            assertThat(drawnPx(pluto, fov)).isAtLeast(3.0 * density - 1e-9)
            fov *= 0.97
        }
    }

    @Test
    fun `a body with no floors is drawn at its true size`() {
        assertThat(drawn(0.52, fovDeg = 45.0, minScreenFraction = 0.0, minSizeDp = 0.0))
            .isWithin(1e-12)
            .of(0.52)
    }

    @Test
    fun `true scale mode still keeps a body visible`() {
        // The Layers-sheet "True scale" option drops minScreenFraction but keeps minSizeDp.
        val venusAtItsSmallest = 9.7 / 3600.0
        val px = drawnPx(venusAtItsSmallest, fovDeg = 45.0, minScreenFraction = 0.0)
        assertThat(px).isWithin(1e-6).of(3.0 * density)
    }

    @Test
    fun `the larger of the two floors wins`() {
        // At a wide field the fraction dominates; at a narrow one they cross and the dp floor
        // takes over only if the fraction is small enough.
        val wide = drawn(0.0, fovDeg = 90.0, minScreenFraction = 0.05, minSizeDp = 3.0)
        assertThat(wide).isWithin(1e-12).of(0.05 * 90.0)

        val dpDominant = drawn(0.0, fovDeg = 90.0, minScreenFraction = 0.0001, minSizeDp = 3.0)
        assertThat(dpDominant).isWithin(1e-12).of(3.0 * density * 90.0 / shortSidePx)
    }

    @Test
    fun `a degenerate viewport falls back to the true size`() {
        assertThat(SizeFloor.drawnDiameterDeg(0.52, 0.05, 3.0, 45.0, 0, 3.0f))
            .isWithin(1e-12)
            .of(0.52)
    }
}
