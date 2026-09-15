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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * The wall clock is the test scheduler's virtual time (offset to a plausible epoch), so
 * `advanceTimeBy` moves the wall and the pacer's `delay`s together — exactly the coupling the
 * controller sees in production.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeControllerTest {
    private fun TestScope.controller() =
        TimeController(
            wallTimeMillis = { EPOCH_MILLIS + testScheduler.currentTime },
            scope = backgroundScope,
        )

    private fun TestScope.collectTimes(
        controller: TimeController,
        into: MutableList<Instant>,
    ): Job = backgroundScope.launch { controller.times.collect { into += it } }

    @Test
    fun `real time ticks lazily on the wall clock`() =
        runTest {
            val controller = controller()
            val times = mutableListOf<Instant>()
            collectTimes(controller, times)
            runCurrent()
            assertThat(times).containsExactly(instantAt(0))

            // Nothing more until the lazy tick elapses.
            advanceTimeBy(TimeController.REAL_TIME_TICK_MS - 1)
            assertThat(times).hasSize(1)
            advanceTimeBy(1)
            runCurrent()
            assertThat(times).hasSize(2)
            assertThat(times[1]).isEqualTo(instantAt(TimeController.REAL_TIME_TICK_MS))
        }

    @Test
    fun `goTimeTravel sweeps the bus to the target and promotes to travelling`() =
        runTest {
            val controller = controller()
            val times = mutableListOf<Instant>()
            collectTimes(controller, times)
            runCurrent()

            val target = instantAt(-1_000_000_000L)
            controller.goTimeTravel(target)
            assertThat(controller.state.value).isEqualTo(TimeTravelState.TRANSITIONING)

            // A real-time collector parked in its lazy 60 s tick must notice immediately:
            // the state change restarts the pacer at the fast cadence.
            advanceTimeBy(TimeController.TRAVEL_TICK_MS)
            runCurrent()
            assertThat(times.size).isAtLeast(2)

            // Mid-transition the bus is strictly between departure and destination.
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS / 2)
            runCurrent()
            val mid = times.last()
            assertThat(mid).isGreaterThan(target)
            assertThat(mid).isLessThan(instantAt(testScheduler.currentTime))

            // Arrival: frozen at the target, state promoted by the pacer.
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS)
            runCurrent()
            assertThat(controller.now()).isEqualTo(target)
            assertThat(controller.state.value).isEqualTo(TimeTravelState.TRAVELLING)
            assertThat(times.last()).isEqualTo(target)
        }

    @Test
    fun `frozen travel emits nothing but accelerating plays the bus forward`() =
        runTest {
            val controller = controller()
            val times = mutableListOf<Instant>()
            collectTimes(controller, times)
            val target = instantAt(5_000_000L)
            controller.goTimeTravel(target)
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS * 2)
            runCurrent()

            // Frozen: fast ticks, but distinct emissions only.
            times.clear()
            advanceTimeBy(TimeController.TRAVEL_TICK_MS * 10)
            runCurrent()
            assertThat(times).isEmpty()

            controller.accelerate()
            assertThat(controller.rateSecondsPerSecond.value).isEqualTo(1.0)
            advanceTimeBy(1_000)
            runCurrent()
            assertThat(times).isNotEmpty()
            assertThat(times.last()).isGreaterThan(target)
        }

    @Test
    fun `returnToRealTime leaves travel immediately and glides home`() =
        runTest {
            val controller = controller()
            val times = mutableListOf<Instant>()
            collectTimes(controller, times)
            controller.goTimeTravel(instantAt(5_000_000L))
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS * 2)
            runCurrent()

            controller.returnToRealTime()
            assertThat(controller.state.value).isEqualTo(TimeTravelState.REAL_TIME)
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS + TimeController.TRAVEL_TICK_MS)
            runCurrent()
            assertThat(controller.now()).isEqualTo(instantAt(testScheduler.currentTime))
        }

    @Test
    fun `wallNow ignores travel`() =
        runTest {
            val controller = controller()
            controller.goTimeTravel(instantAt(5_000_000L))
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS * 2)
            assertThat(controller.wallNow()).isEqualTo(instantAt(testScheduler.currentTime))
        }

    companion object {
        private const val EPOCH_MILLIS = 1_770_000_000_000L

        private fun instantAt(schedulerMillis: Long) =
            Instant.fromEpochMilliseconds(EPOCH_MILLIS + schedulerMillis)
    }
}
