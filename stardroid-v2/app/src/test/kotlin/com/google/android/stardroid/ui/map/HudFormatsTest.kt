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

class HudFormatsTest {
    @Test
    fun `right ascension converts to hours and minutes`() {
        // Vega-ish: 279.4° = 18.6267 h.
        assertThat(HudFormats.raHoursMinutes(279.4)).isEqualTo(18 to 38)
        assertThat(HudFormats.raHoursMinutes(0.0)).isEqualTo(0 to 0)
        // 15° = exactly 1 h.
        assertThat(HudFormats.raHoursMinutes(15.0)).isEqualTo(1 to 0)
    }

    @Test
    fun `right ascension minutes carry into the hour instead of printing 60`() {
        // 44.9° = 2.9933 h = 2 h 59.6 m → rounds to 3 h 00 m, not 2 h 60 m.
        assertThat(HudFormats.raHoursMinutes(44.9)).isEqualTo(3 to 0)
    }

    @Test
    fun `right ascension wraps at 24 hours and normalizes negatives`() {
        // 359.96° = 23.9973 h → minutes round to 60 → carries to 24 h → wraps to 0 h.
        assertThat(HudFormats.raHoursMinutes(359.96)).isEqualTo(0 to 0)
        // fromGeocentricVector already normalizes, but the helper shouldn't rely on it.
        assertThat(HudFormats.raHoursMinutes(-15.0)).isEqualTo(23 to 0)
    }

    @Test
    fun `azimuth rounds to whole degrees and wraps 360 to 0`() {
        assertThat(HudFormats.azimuthWholeDegrees(312.4)).isEqualTo(312)
        assertThat(HudFormats.azimuthWholeDegrees(359.7)).isEqualTo(0)
        assertThat(HudFormats.azimuthWholeDegrees(0.2)).isEqualTo(0)
    }

    @Test
    fun `cardinal buckets are 22 point 5 degrees wide, centred on the points`() {
        assertThat(HudFormats.cardinalIndex(0.0)).isEqualTo(0) // N
        assertThat(HudFormats.cardinalIndex(11.2)).isEqualTo(0) // still N
        assertThat(HudFormats.cardinalIndex(11.3)).isEqualTo(1) // NNE
        assertThat(HudFormats.cardinalIndex(90.0)).isEqualTo(4) // E
        assertThat(HudFormats.cardinalIndex(312.4)).isEqualTo(14) // NW
        assertThat(HudFormats.cardinalIndex(354.0)).isEqualTo(0) // wraps back to N
        // Every azimuth maps inside the 16-entry table.
        for (az in 0 until 360) {
            val index = HudFormats.cardinalIndex(az.toDouble())
            assertThat(index).isAtLeast(0)
            assertThat(index).isLessThan(HudFormats.CARDINAL_COUNT)
        }
    }

    @Test
    fun `correction row is strictly hidden at zero`() {
        assertThat(HudFormats.correctionActive(0.0, 0.0)).isFalse()
        // Float dust stays hidden; anything a drag could produce shows.
        assertThat(HudFormats.correctionActive(0.01, -0.02)).isFalse()
        assertThat(HudFormats.correctionActive(0.1, 0.0)).isTrue()
        assertThat(HudFormats.correctionActive(0.0, -4.5)).isTrue()
    }

    @Test
    fun `fov switches to one decimal below ten degrees`() {
        assertThat(HudFormats.fovDecimals(45.0)).isEqualTo(0)
        assertThat(HudFormats.fovDecimals(10.0)).isEqualTo(0)
        assertThat(HudFormats.fovDecimals(9.9)).isEqualTo(1)
        assertThat(HudFormats.fovDecimals(1.0)).isEqualTo(1)
    }

    @Test
    fun `fov switches to two decimals below one degree`() {
        assertThat(HudFormats.fovDecimals(0.5)).isEqualTo(2)
        assertThat(HudFormats.fovDecimals(0.1)).isEqualTo(2)
        // The deepest zoom the map allows still moves the readout rather than printing 0.0°.
        assertThat(HudFormats.fovDecimals(0.01)).isEqualTo(2)
    }
}
