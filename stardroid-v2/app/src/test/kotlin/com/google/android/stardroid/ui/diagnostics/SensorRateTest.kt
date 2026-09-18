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

class SensorRateTest {
    @Test
    fun `no events yet reports zero hz and no staleness`() {
        val accumulator = SensorRateAccumulator(windowMillis = 3_000)
        val info = accumulator.snapshot(nowMillis = 1_000)
        assertThat(info.hz).isEqualTo(0.0)
        assertThat(info.staleForMillis).isNull()
    }

    @Test
    fun `a single event has no rate but is fresh`() {
        val accumulator = SensorRateAccumulator(windowMillis = 3_000)
        accumulator.recordEvent(1_000)
        val info = accumulator.snapshot(nowMillis = 1_200)
        assertThat(info.hz).isEqualTo(0.0)
        assertThat(info.staleForMillis).isEqualTo(200)
    }

    @Test
    fun `steady 50 Hz events report about 50 Hz`() {
        val accumulator = SensorRateAccumulator(windowMillis = 3_000)
        var timeMillis = 0L
        repeat(100) {
            accumulator.recordEvent(timeMillis)
            timeMillis += 20 // 50 Hz
        }
        val info = accumulator.snapshot(nowMillis = timeMillis)
        assertThat(info.hz).isWithin(0.5).of(50.0)
        assertThat(info.staleForMillis).isEqualTo(20)
    }

    @Test
    fun `events older than the window stop contributing to the rate`() {
        val accumulator = SensorRateAccumulator(windowMillis = 1_000)
        accumulator.recordEvent(0)
        accumulator.recordEvent(100)
        // Long gap: only the trailing event should remain in the window.
        accumulator.recordEvent(5_000)
        val info = accumulator.snapshot(nowMillis = 5_000)
        assertThat(info.hz).isEqualTo(0.0)
        assertThat(info.staleForMillis).isEqualTo(0)
    }

    @Test
    fun `a sensor that has gone silent shows growing staleness, not a frozen rate`() {
        val accumulator = SensorRateAccumulator(windowMillis = 3_000)
        accumulator.recordEvent(0)
        accumulator.recordEvent(20)
        val info = accumulator.snapshot(nowMillis = 10_000)
        assertThat(info.staleForMillis).isEqualTo(9_980)
        // The window is long past by 10s; the last-known rate must decay to zero rather than
        // recomputing forever from the two samples that arrived before the sensor went quiet.
        assertThat(info.hz).isEqualTo(0.0)
    }

    @Test
    fun `hz decays across repeated polls once events stop arriving`() {
        val accumulator = SensorRateAccumulator(windowMillis = 3_000)
        var timeMillis = 0L
        repeat(20) {
            accumulator.recordEvent(timeMillis)
            timeMillis += 20 // 50 Hz
        }
        assertThat(accumulator.snapshot(nowMillis = timeMillis).hz).isGreaterThan(0.0)

        // No further events; poll well past the window on its own clock.
        val info = accumulator.snapshot(nowMillis = timeMillis + 5_000)
        assertThat(info.hz).isEqualTo(0.0)
    }
}
