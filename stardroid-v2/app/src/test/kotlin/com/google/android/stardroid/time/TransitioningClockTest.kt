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

/** Port of v1's `TransitioningCompositeClockTest`. */
class TransitioningClockTest {
    private var wallMillis = 0L
    private val travelClock = TimeTravelClock { wallMillis }
    private val clock = TransitioningClock(travelClock) { wallMillis }

    @Test
    fun `interpolant hits its endpoints and has zero-velocity ends`() {
        val tol = 1e-3
        assertThat(TransitioningClock.interpolate(0.0, 10.0, 0.0)).isWithin(tol).of(0.0)
        assertThat(TransitioningClock.interpolate(1.0, 10.0, 0.0)).isWithin(tol).of(1.0)
        assertThat(TransitioningClock.interpolate(0.0, 10.0, 1.0)).isWithin(tol).of(10.0)
        assertThat(TransitioningClock.interpolate(0.0, 10.0, 0.5)).isWithin(tol).of(5.0)
        val epsilon = 1e-4
        val dydx0 =
            (
                TransitioningClock.interpolate(0.0, 1.0, epsilon) -
                    TransitioningClock.interpolate(0.0, 1.0, 0.0)
            ) / epsilon
        assertThat(dydx0).isWithin(tol).of(0.0)
        val dydx1 =
            (
                TransitioningClock.interpolate(0.0, 1.0, 1.0) -
                    TransitioningClock.interpolate(0.0, 1.0, 1.0 - epsilon)
            ) / epsilon
        assertThat(dydx1).isWithin(tol).of(0.0)
    }

    @Test
    fun `transitions sweep to travel time and back, exactly as v1`() {
        val transition = TransitioningClock.TRANSITION_TIME_MILLIS
        wallMillis = 1000L
        // Starts in real time, following the wall clock.
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(1000L)
        assertThat(clock.mode).isEqualTo(TransitioningClock.Mode.REAL_TIME)
        wallMillis = 2000L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(2000L)

        clock.goTimeTravel(5000L)
        assertThat(clock.mode).isEqualTo(TransitioningClock.Mode.TRANSITION)
        // We shouldn't have budged yet.
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(2000L)
        wallMillis += transition / 2
        // Half way there.
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(3500L)
        wallMillis += transition / 2
        // All the way there — and we stay.
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(5000L)
        wallMillis += 1000L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(5000L)
        assertThat(clock.mode).isEqualTo(TransitioningClock.Mode.TIME_TRAVEL)

        clock.returnToRealTime()
        val destination = wallMillis + transition
        // Shouldn't have moved yet.
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(5000L)
        wallMillis += transition / 2
        // Half way home.
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo((5000L + destination) / 2)
        wallMillis += transition / 2
        // All the way home, then advancing with the wall clock.
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(destination)
        wallMillis += 1000L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(wallMillis)
        assertThat(clock.mode).isEqualTo(TransitioningClock.Mode.REAL_TIME)
    }

    @Test
    fun `accelerating mid-transition does not jump the sky on arrival`() {
        val transition = TransitioningClock.TRANSITION_TIME_MILLIS
        wallMillis = 0L
        clock.goTimeTravel(1_000_000L)
        // The user speeds the ladder up while the glide is still running: 0 → 1 s/s → 1 min/s.
        repeat(2) { travelClock.accelerate() }
        assertThat(travelClock.rateSecondsPerSecond).isEqualTo(TimeTravelClock.SECONDS_PER_MINUTE)
        // Cross the arrival boundary. Without re-anchoring, the whole transition's wall time
        // would be billed at the new rate and snap the clock minutes ahead of the target.
        wallMillis += transition + 1L
        assertThat(clock.timeInMillisSinceEpoch()).isEqualTo(1_000_000L)
        // From arrival it advances at the ladder rate: one wall second → one simulated minute.
        wallMillis += 1000L
        assertThat(clock.timeInMillisSinceEpoch())
            .isEqualTo(1_000_000L + TimeTravelClock.SECONDS_PER_MINUTE.toLong() * 1000L)
    }
}
