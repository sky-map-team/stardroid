/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class ArExposureMathTest {
    @Test
    fun `iso interpolates logarithmically across the sensor range`() {
        val range = 100..6400
        assertThat(ArExposureMath.isoForFraction(range, 0.0)).isEqualTo(100)
        assertThat(ArExposureMath.isoForFraction(range, 1.0)).isEqualTo(6400)
        // Halfway in stops: 100 → 6400 is six stops, so the midpoint is 800.
        assertThat(ArExposureMath.isoForFraction(range, 0.5)).isEqualTo(800)
        // Out-of-range fractions clamp instead of extrapolating.
        assertThat(ArExposureMath.isoForFraction(range, 2.0)).isEqualTo(6400)
        assertThat(ArExposureMath.isoForFraction(range, -1.0)).isEqualTo(100)
    }

    @Test
    fun `exposure time interpolates logarithmically`() {
        val range = 1_000_000L..1_000_000_000L
        assertThat(ArExposureMath.exposureTimeForFraction(range, 0.0)).isEqualTo(1_000_000L)
        assertThat(ArExposureMath.exposureTimeForFraction(range, 1.0))
            .isEqualTo(1_000_000_000L)
        // 1 ms → 1 s spans three decades; halfway is √1000 ≈ 31.6 ms.
        assertThat(ArExposureMath.exposureTimeForFraction(range, 0.5).toDouble())
            .isWithin(1e5)
            .of(31_622_776.6)
    }
}
