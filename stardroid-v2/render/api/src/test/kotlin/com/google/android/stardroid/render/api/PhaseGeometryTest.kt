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

class PhaseGeometryTest {
    /** Fraction of the unit disc's area that [PhaseGeometry] calls lit, by fine quadrature. */
    private fun litAreaFraction(fraction: Double): Double {
        val n = 800
        var lit = 0
        var inside = 0
        for (i in 0 until n) {
            val y = (i + 0.5) / n * 2.0 - 1.0
            for (j in 0 until n) {
                val x = (j + 0.5) / n * 2.0 - 1.0
                if (x * x + y * y > 1.0) continue
                inside++
                if (PhaseGeometry.litOffset(x, y, fraction) >= 0.0) lit++
            }
        }
        return lit.toDouble() / inside
    }

    @Test
    fun `lit area equals the illuminated fraction`() {
        // The whole point of the construction: the lune it describes has area pi*r^2*f exactly.
        for (f in listOf(0.0, 0.05, 0.25, 0.5, 0.75, 0.95, 1.0)) {
            assertThat(litAreaFraction(f)).isWithin(0.005).of(f)
        }
    }

    @Test
    fun `full phase lights the entire disc`() {
        for (y in listOf(-0.99, -0.5, 0.0, 0.5, 0.99)) {
            for (x in listOf(-0.99, -0.5, 0.0, 0.5, 0.99)) {
                if (x * x + y * y > 1.0) continue
                assertThat(PhaseGeometry.litOffset(x, y, 1.0)).isAtLeast(0.0)
            }
        }
    }

    @Test
    fun `new phase leaves the entire disc dark`() {
        for (y in listOf(-0.99, -0.5, 0.0, 0.5, 0.99)) {
            for (x in listOf(-0.99, -0.5, 0.0, 0.5, 0.99)) {
                if (x * x + y * y > 1.0) continue
                assertThat(PhaseGeometry.litOffset(x, y, 0.0)).isAtMost(0.0)
            }
        }
    }

    @Test
    fun `at quarter phase the terminator is the vertical diameter`() {
        for (y in listOf(-0.9, -0.3, 0.0, 0.3, 0.9)) {
            assertThat(PhaseGeometry.litOffset(0.0, y, 0.5)).isWithin(1e-12).of(0.0)
            assertThat(PhaseGeometry.litOffset(0.2, y, 0.5)).isGreaterThan(0.0)
            assertThat(PhaseGeometry.litOffset(-0.2, y, 0.5)).isLessThan(0.0)
        }
    }

    @Test
    fun `the lit limb always faces positive x`() {
        // Whatever the phase, the point on the limb at +x is lit and the one at -x is not,
        // except at the degenerate full and new phases.
        for (f in listOf(0.05, 0.25, 0.5, 0.75, 0.95)) {
            assertThat(PhaseGeometry.litOffset(0.999, 0.0, f)).isGreaterThan(0.0)
            assertThat(PhaseGeometry.litOffset(-0.999, 0.0, f)).isLessThan(0.0)
        }
    }

    @Test
    fun `a crescent is thinner than a gibbous at the same offset from half`() {
        // f = 0.3 and f = 0.7 are mirror images: the crescent's lit width at the equator plus
        // the gibbous's dark width should both be |2f-1| of the radius.
        assertThat(PhaseGeometry.litOffset(0.0, 0.0, 0.3)).isWithin(1e-12).of(-0.4)
        assertThat(PhaseGeometry.litOffset(0.0, 0.0, 0.7)).isWithin(1e-12).of(0.4)
    }
}
