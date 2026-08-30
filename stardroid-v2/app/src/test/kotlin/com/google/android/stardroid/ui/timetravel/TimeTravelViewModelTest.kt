/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.timetravel

import com.google.android.stardroid.astronomy.KeplerianEphemeris
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.RiseSetIndicator
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.nextLunarPhaseEvent
import com.google.android.stardroid.astronomy.nextRiseSetTime
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.time.TimeController
import com.google.android.stardroid.time.TimeTravelEvent
import com.google.android.stardroid.time.TimeTravelState
import com.google.android.stardroid.time.TransitioningClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class TimeTravelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        location: LatLong = LatLong(0.0, 0.0),
        epochMillis: Long = EPOCH_MILLIS,
    ): TimeTravelViewModel =
        TimeTravelViewModel(
            TimeController(
                wallTimeMillis = { epochMillis + testScheduler.currentTime },
                scope = backgroundScope,
            ),
            KeplerianEphemeris,
            location = { location },
        )

    @Test
    fun `walks normal, transitioning, travelling, and home again`() =
        testScope.runTest {
            val vm = viewModel()
            assertThat(vm.state.value).isEqualTo(TimeTravelState.REAL_TIME)

            vm.goTo(TARGET)
            assertThat(vm.state.value).isEqualTo(TimeTravelState.TRANSITIONING)

            // The readout StateFlow's collector is also the pacer that promotes arrival.
            val readout = launchReadout(vm)
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS * 2)
            runCurrent()
            assertThat(vm.state.value).isEqualTo(TimeTravelState.TRAVELLING)
            assertThat(vm.currentTime.value).isEqualTo(TARGET)

            vm.returnToRealTime()
            assertThat(vm.state.value).isEqualTo(TimeTravelState.REAL_TIME)
            readout.cancel()
        }

    @Test
    fun `rate steps ride the v1 ladder and pause freezes`() =
        testScope.runTest {
            val vm = viewModel()
            vm.goTo(TARGET)
            assertThat(vm.rateSecondsPerSecond.value).isEqualTo(0.0)
            vm.accelerate()
            assertThat(vm.rateSecondsPerSecond.value).isEqualTo(1.0)
            vm.accelerate()
            assertThat(vm.rateSecondsPerSecond.value).isEqualTo(60.0)
            vm.decelerate()
            vm.decelerate()
            assertThat(vm.rateSecondsPerSecond.value).isEqualTo(0.0)
            vm.accelerate()
            vm.pauseTime()
            assertThat(vm.rateSecondsPerSecond.value).isEqualTo(0.0)
        }

    @Test
    fun `fixed events resolve to their timestamps`() =
        testScope.runTest {
            val vm = viewModel()
            val apollo =
                TimeTravelEvent(
                    displayNameRes = 0,
                    type = TimeTravelEvent.Type.FIXED,
                    timestamp = Instant.fromEpochMilliseconds(-14182953622L),
                )
            assertThat(vm.resolve(apollo)).isEqualTo(apollo.timestamp)
        }

    @Test
    fun `now resolves to the wall clock even mid-travel`() =
        testScope.runTest {
            val vm = viewModel()
            vm.goTo(TARGET)
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS * 2)
            val now = TimeTravelEvent(displayNameRes = 0, type = TimeTravelEvent.Type.NOW)
            assertThat(vm.resolve(now))
                .isEqualTo(Instant.fromEpochMilliseconds(EPOCH_MILLIS + testScheduler.currentTime))
        }

    @Test
    fun `lunar events resolve through the shared solver`() =
        testScope.runTest {
            val vm = viewModel()
            val wallNow = Instant.fromEpochMilliseconds(EPOCH_MILLIS)
            val fullMoon =
                TimeTravelEvent(
                    displayNameRes = 0,
                    type = TimeTravelEvent.Type.NEXT_FULL_MOON,
                )
            val newMoon =
                TimeTravelEvent(displayNameRes = 0, type = TimeTravelEvent.Type.NEXT_NEW_MOON)
            assertThat(vm.resolve(fullMoon))
                .isEqualTo(nextLunarPhaseEvent(LunarPhase.FULL, wallNow, KeplerianEphemeris))
            assertThat(vm.resolve(newMoon))
                .isEqualTo(nextLunarPhaseEvent(LunarPhase.NEW, wallNow, KeplerianEphemeris))
            assertThat(vm.resolve(fullMoon)).isGreaterThan(wallNow)
        }

    @Test
    fun `past fixed events know they are past`() =
        testScope.runTest {
            val vm = viewModel()
            val now = vm.wallTime()
            val past =
                TimeTravelEvent(0, TimeTravelEvent.Type.FIXED, Instant.fromEpochMilliseconds(0))
            val future = TimeTravelEvent(0, TimeTravelEvent.Type.FIXED, now + FAR_FUTURE)
            assertThat(past.isPastAt(now)).isTrue()
            assertThat(future.isPastAt(now)).isFalse()
        }

    @Test
    fun `sunrise and sunset resolve through the rise-set solver at the observer's location`() =
        testScope.runTest {
            val vm = viewModel(location = LONDON)
            val wallNow = Instant.fromEpochMilliseconds(EPOCH_MILLIS)
            val sunrise = TimeTravelEvent(0, TimeTravelEvent.Type.NEXT_SUNRISE)
            val sunset = TimeTravelEvent(0, TimeTravelEvent.Type.NEXT_SUNSET)
            assertThat(vm.resolve(sunrise))
                .isEqualTo(
                    nextRiseSetTime(
                        SolarSystemBody.SUN,
                        wallNow,
                        LONDON,
                        RiseSetIndicator.RISE,
                        KeplerianEphemeris,
                    ),
                )
            assertThat(vm.resolve(sunset))
                .isEqualTo(
                    nextRiseSetTime(
                        SolarSystemBody.SUN,
                        wallNow,
                        LONDON,
                        RiseSetIndicator.SET,
                        KeplerianEphemeris,
                    ),
                )
            assertThat(vm.resolve(sunrise)).isGreaterThan(wallNow)
        }

    @Test
    fun `sunset resolves to null under the midnight sun`() =
        testScope.runTest {
            // Tromsø at the June solstice: no sunset within a day → the v1 toast case.
            val vm = viewModel(location = TROMSO, epochMillis = MIDSUMMER_MILLIS)
            val sunset = TimeTravelEvent(0, TimeTravelEvent.Type.NEXT_SUNSET)
            assertThat(vm.resolve(sunset)).isNull()
        }

    @Test
    fun `travel and return emit their effects, start-from-now neither`() =
        testScope.runTest {
            val vm = viewModel()
            val effects = mutableListOf<TravelEffect>()
            backgroundScope.launch { vm.effects.collect { effects += it } }
            runCurrent()

            vm.goTo(TARGET)
            runCurrent()
            assertThat(effects).containsExactly(TravelEffect.Travel(TARGET))

            vm.returnToRealTime()
            runCurrent()
            assertThat(effects)
                .containsExactly(TravelEffect.Travel(TARGET), TravelEffect.Return)
                .inOrder()

            vm.goToNow()
            runCurrent()
            assertThat(effects).hasSize(2)
        }

    @Test
    fun `the search target waits for arrival`() =
        testScope.runTest {
            val vm = viewModel()
            val targets = mutableListOf<CelestialObjectId>()
            backgroundScope.launch { vm.searchTargets.collect { targets += it } }
            val readout = launchReadout(vm)

            vm.goTo(TARGET, searchTarget = SUN_ID)
            runCurrent()
            // Still transitioning: v1 delayed its search past the sweep for the same reason.
            assertThat(targets).isEmpty()

            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS * 2)
            runCurrent()
            assertThat(targets).containsExactly(SUN_ID)
            readout.cancel()
        }

    @Test
    fun `bailing home mid-transition drops the search target`() =
        testScope.runTest {
            val vm = viewModel()
            val targets = mutableListOf<CelestialObjectId>()
            backgroundScope.launch { vm.searchTargets.collect { targets += it } }
            val readout = launchReadout(vm)

            vm.goTo(TARGET, searchTarget = SUN_ID)
            runCurrent()
            vm.returnToRealTime()
            advanceTimeBy(TransitioningClock.TRANSITION_TIME_MILLIS * 2)
            runCurrent()
            assertThat(targets).isEmpty()
            readout.cancel()
        }

    /** Collects [TimeTravelViewModel.currentTime] so its `WhileSubscribed` pacer runs. */
    private fun TestScope.launchReadout(vm: TimeTravelViewModel) =
        backgroundScope.launch {
            vm.currentTime.collect {}
        }

    companion object {
        // 2026-02-15T00:26:40Z: a quiet wall-clock epoch for the fake scheduler clock.
        private const val EPOCH_MILLIS = 1_771_115_200_000L

        // 2026-06-20T12:00:00Z, deep in Tromsø's midnight sun.
        private const val MIDSUMMER_MILLIS = 1_781_956_800_000L
        private val TARGET = Instant.fromEpochMilliseconds(1_608_574_800_000L)
        private val FAR_FUTURE = 1000.days
        private val LONDON = LatLong(51.51, -0.13)
        private val TROMSO = LatLong(69.65, 18.96)
        private val SUN_ID = CelestialObjectId("planet/sun")
    }
}
