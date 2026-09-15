/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

    @Test
    fun `shutter readout uses fractions below a quarter second and decimals above`() {
        assertThat(ArExposureMath.formatExposureTime(33_333_333L)).isEqualTo("1/30s")
        assertThat(ArExposureMath.formatExposureTime(1_000_000L)).isEqualTo("1/1000s")
        assertThat(ArExposureMath.formatExposureTime(500_000_000L)).isEqualTo("0.5s")
        assertThat(ArExposureMath.formatExposureTime(2_000_000_000L)).isEqualTo("2.0s")
    }
}
