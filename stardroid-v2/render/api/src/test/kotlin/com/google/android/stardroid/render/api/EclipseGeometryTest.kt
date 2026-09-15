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

class EclipseGeometryTest {
    private val centeredTotal =
        EclipseShadow(umbraRadius = 0.6, penumbraRadius = 1.4, offset = 0.0, directionDeg = 0.0)

    @Test
    fun `outside the penumbra there is no tint`() {
        val tint = EclipseGeometry.tint(x = 0.0, y = 1.39, centeredTotal)
        // Just inside the penumbra edge still tints; just past 1.4 in y should not.
        val outside = EclipseGeometry.tint(x = 0.0, y = 1.41, centeredTotal)
        assertThat(tint).isNotEqualTo(EclipseGeometry.Tint.NONE)
        assertThat(outside).isEqualTo(EclipseGeometry.Tint.NONE)
    }

    @Test
    fun `the umbra darkens and reddens more toward its centre`() {
        val edge = EclipseGeometry.tint(x = 0.0, y = 0.6, centeredTotal)
        val core = EclipseGeometry.tint(x = 0.0, y = 0.0, centeredTotal)
        // Darker toward the centre in every channel...
        assertThat(core.red).isLessThan(edge.red)
        assertThat(core.green).isLessThan(edge.green)
        assertThat(core.blue).isLessThan(edge.blue)
        // ...and redder: red is always the least-suppressed channel, and increasingly so deeper
        // in, since that is the whole visual signature of a lunar eclipse.
        assertThat(core.red).isGreaterThan(core.green)
        assertThat(core.red).isGreaterThan(core.blue)
        assertThat(core.red / core.green).isGreaterThan(edge.red / edge.green)
    }

    @Test
    fun `the penumbra only dims, with no colour shift`() {
        // Between the umbra (0.6) and penumbra (1.4) edges, uniformly.
        val tint = EclipseGeometry.tint(x = 0.0, y = 1.0, centeredTotal)
        assertThat(tint.red).isEqualTo(tint.green)
        assertThat(tint.green).isEqualTo(tint.blue)
        assertThat(tint.red).isLessThan(1.0)
    }

    @Test
    fun `penumbral dimming fades to nothing at the outer edge`() {
        // Right at the penumbra's outer boundary the dimming factor must reach exactly 1.0 (no
        // effect) by construction, whatever PENUMBRA_MAX_DIMMING is tuned to — otherwise there
        // would be a visible seam where the disc's untouched region meets the shadow.
        val atPenumbraEdge = EclipseGeometry.tint(x = 0.0, y = 1.4 - 1e-9, centeredTotal)
        assertThat(atPenumbraEdge.red).isWithin(1e-6).of(1.0)
        assertThat(atPenumbraEdge.green).isWithin(1e-6).of(1.0)
        assertThat(atPenumbraEdge.blue).isWithin(1e-6).of(1.0)
    }

    @Test
    fun `the shadow follows its offset and direction`() {
        // directionDeg = 0 (north, the same east-of-north convention brightLimbAngleDeg uses)
        // should put the shadow at (0, +offset).
        val offsetNorth =
            EclipseShadow(umbraRadius = 0.3, penumbraRadius = 0.5, offset = 2.0, directionDeg = 0.0)
        assertThat(
            EclipseGeometry.tint(0.0, 2.0, offsetNorth),
        ).isNotEqualTo(EclipseGeometry.Tint.NONE)
        assertThat(EclipseGeometry.tint(0.0, 0.0, offsetNorth)).isEqualTo(EclipseGeometry.Tint.NONE)

        // directionDeg = 180 (south) should put it at (0, -offset) instead.
        val offsetSouth =
            EclipseShadow(
                umbraRadius = 0.3,
                penumbraRadius = 0.5,
                offset = 2.0,
                directionDeg = 180.0,
            )
        assertThat(
            EclipseGeometry.tint(0.0, -2.0, offsetSouth),
        ).isNotEqualTo(EclipseGeometry.Tint.NONE)
        assertThat(EclipseGeometry.tint(0.0, 2.0, offsetSouth)).isEqualTo(EclipseGeometry.Tint.NONE)
    }
}
