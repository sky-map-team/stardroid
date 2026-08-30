/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TimeTravelClockTest {
    private var wallMillis = 1_000_000L
    private val clock = TimeTravelClock { wallMillis }

    @Test
    fun `setting the date freezes simulated time there`() {
        clock.setTimeTravelDate(5_000L)
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(5_000L)
        wallMillis += 10_000L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(5_000L)
        assertThat(clock.rateSecondsPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `one accelerate plays forward at one second per second`() {
        clock.setTimeTravelDate(5_000L)
        clock.accelerate()
        assertThat(clock.rateSecondsPerSecond).isEqualTo(1.0)
        wallMillis += 1_000L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(6_000L)
    }

    @Test
    fun `one decelerate plays backward at one second per second`() {
        clock.setTimeTravelDate(5_000L)
        clock.decelerate()
        assertThat(clock.rateSecondsPerSecond).isEqualTo(-1.0)
        wallMillis += 1_000L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(4_000L)
    }

    @Test
    fun `the ladder clamps at a week per second in both directions`() {
        repeat(20) { clock.accelerate() }
        assertThat(clock.rateSecondsPerSecond).isEqualTo(TimeTravelClock.SECONDS_PER_WEEK)
        repeat(40) { clock.decelerate() }
        assertThat(clock.rateSecondsPerSecond).isEqualTo(-TimeTravelClock.SECONDS_PER_WEEK)
    }

    @Test
    fun `pause returns to frozen from anywhere on the ladder`() {
        repeat(3) { clock.accelerate() }
        clock.pause()
        assertThat(clock.rateSecondsPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `setting a new date pauses playback`() {
        clock.accelerate()
        clock.setTimeTravelDate(5_000L)
        assertThat(clock.rateSecondsPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `day-per-second rates step in whole days`() {
        val dayMillis = 24L * 3600L * 1000L
        clock.setTimeTravelDate(0L)
        while (clock.rateSecondsPerSecond < TimeTravelClock.SECONDS_PER_DAY) clock.accelerate()
        // Half a wall second is half a simulated day: not yet a whole day, so time holds.
        wallMillis += 500L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(0L)
        // The next half second completes the day, which lands in one whole-day jump.
        wallMillis += 500L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(dayMillis)
    }

    @Test
    fun `whole-day stepping keeps the fractional remainder so the rate holds`() {
        val dayMillis = 24L * 3600L * 1000L
        clock.setTimeTravelDate(0L)
        while (clock.rateSecondsPerSecond < TimeTravelClock.SECONDS_PER_DAY) clock.accelerate()
        // At a day/sec, one simulated day is exactly one wall second. Poll every 600 ms so each
        // day is crossed mid-poll with a 0.2-day remainder that must carry over, not be
        // discarded. After k polls the simulated time must be the whole-day floor of 0.6·k days.
        var lastSim = 0L
        for (k in 1..10) {
            wallMillis += 600L
            lastSim = clock.timeInMillisSinceEpoch()
            val expectedDays = 600L * k / 1000L
            assertThat(lastSim).isEqualTo(expectedDays * dayMillis)
        }
        // 6 wall seconds elapsed → 6 whole simulated days, with no accumulated drift.
        assertThat(lastSim).isEqualTo(6L * dayMillis)
    }
}
