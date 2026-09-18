/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PointingJitterTest {
    @Test
    fun `a steady pointing has zero jitter`() {
        val accumulator = PointingJitterAccumulator(windowMillis = 5_000)
        var jitter = accumulator.add(0, azimuthDeg = 90.0, altitudeDeg = 30.0)
        repeat(10) {
            jitter = accumulator.add(it * 100L, azimuthDeg = 90.0, altitudeDeg = 30.0)
        }
        assertThat(jitter.azimuthStdDevDeg).isWithin(1e-9).of(0.0)
        assertThat(jitter.altitudeStdDevDeg).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `altitude jitter matches the population standard deviation`() {
        val accumulator = PointingJitterAccumulator(windowMillis = 5_000)
        val altitudes = listOf(10.0, 12.0, 8.0, 11.0, 9.0)
        var jitter = accumulator.add(0, 0.0, altitudes[0])
        for (i in 1 until altitudes.size) {
            jitter = accumulator.add(i * 10L, 0.0, altitudes[i])
        }
        val mean = altitudes.average()
        val expected = kotlin.math.sqrt(altitudes.sumOf { (it - mean) * (it - mean) } / altitudes.size)
        assertThat(jitter.altitudeStdDevDeg).isWithin(1e-9).of(expected)
    }

    @Test
    fun `azimuth jitter across the 0-360 seam stays small, not near-maximal`() {
        val accumulator = PointingJitterAccumulator(windowMillis = 5_000)
        // A phone jittering by a couple of degrees around due north, straddling the wrap.
        val headings = listOf(359.0, 0.5, 1.0, 359.5, 0.0)
        var jitter = accumulator.add(0, headings[0], 0.0)
        for (i in 1 until headings.size) {
            jitter = accumulator.add(i * 10L, headings[i], 0.0)
        }
        assertThat(jitter.azimuthStdDevDeg).isLessThan(2.0)
    }

    @Test
    fun `samples older than the window fall off`() {
        val accumulator = PointingJitterAccumulator(windowMillis = 1_000)
        accumulator.add(0, azimuthDeg = 0.0, altitudeDeg = 0.0)
        accumulator.add(0, azimuthDeg = 20.0, altitudeDeg = 20.0)
        // Far enough later that both earlier samples have aged out except the trailing one.
        val jitter = accumulator.add(5_000, azimuthDeg = 20.0, altitudeDeg = 20.0)
        assertThat(jitter.azimuthStdDevDeg).isWithin(1e-9).of(0.0)
        assertThat(jitter.altitudeStdDevDeg).isWithin(1e-9).of(0.0)
    }
}
