/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.Instant

/** Where the app's clock is, from the UI's point of view. */
enum class TimeTravelState {
    /** The wall clock rules; the map shows the sky of right now. */
    REAL_TIME,

    /** Gliding toward the travel target (v1's 2.5 s sweep). */
    TRANSITIONING,

    /** Arrived; the [TimeTravelClock] speed ladder is in charge. */
    TRAVELLING,
}

/**
 * The app's single time source — the D13 "shared clock bus" that D41 flagged as debt. Every
 * time consumer (layers via the registry, `MapViewModel`'s local frame and sky gradient,
 * `TimeTravelViewModel`'s readout) sees the same instant stream, so the dome, horizon, and
 * solar system can never disagree about what time it is.
 *
 * [times] paces itself by state: a lazy real-time tick (the horizon drifts ~0.25°/min of sky),
 * or a fast tick while transitioning/travelling, when a second of wall time can mean a week
 * of sky (D13 lets dynamic layers re-submit at up to per-frame rates; scenes are small).
 * Emissions are distinct, so frozen travel time and the ≥ day/sec whole-day steps of
 * [TimeTravelClock] cost consumers nothing between changes.
 *
 * The stream is shared ([shareIn]): a single pacer feeds every consumer the *same* [Instant]
 * emissions. This matters under fast travel — at a week/sec even a few ms of phase skew
 * between independent pacers would sample the clock hours of sky-time apart, visibly
 * desynchronising the dome, horizon, stars, and planets within one frame.
 */
class TimeController(
    private val wallTimeMillis: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    private val travelClock = TimeTravelClock(wallTimeMillis)
    private val clock = TransitioningClock(travelClock, wallTimeMillis)

    private val _state = MutableStateFlow(TimeTravelState.REAL_TIME)
    val state: StateFlow<TimeTravelState> = _state.asStateFlow()

    private val _rateSecondsPerSecond = MutableStateFlow(0.0)

    /** The travel playback rate in simulated seconds per wall second; 0 while frozen. */
    val rateSecondsPerSecond: StateFlow<Double> = _rateSecondsPerSecond.asStateFlow()

    /** The current instant on the app clock (travel-aware). */
    fun now(): Instant = Instant.fromEpochMilliseconds(clock.timeInMillisSinceEpoch())

    /** The real wall-clock now regardless of travel state (picker seeding, preset math). */
    fun wallNow(): Instant = Instant.fromEpochMilliseconds(wallTimeMillis())

    /**
     * The clock bus. One shared pacer reads the one clock; every collector sees identical
     * emissions. The state change on [goTimeTravel]/[returnToRealTime] restarts the pacer, so
     * a real-time collector parked in its lazy tick picks up the transition immediately.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val times: Flow<Instant> =
        _state
            .flatMapLatest { state ->
                flow {
                    while (true) {
                        emit(now())
                        // May flip state → flatMapLatest restarts this pacer under the new
                        // cadence rules; the loop below never sees a stale state.
                        promoteArrival()
                        delay(
                            if (state == TimeTravelState.REAL_TIME &&
                                clock.mode == TransitioningClock.Mode.REAL_TIME
                            ) {
                                REAL_TIME_TICK_MS
                            } else {
                                TRAVEL_TICK_MS
                            },
                        )
                    }
                }
            }.distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    /** Glides the clock to [target], arriving frozen (v1: entering travel pauses playback). */
    @Synchronized
    fun goTimeTravel(target: Instant) {
        clock.goTimeTravel(target.toEpochMilliseconds())
        _rateSecondsPerSecond.value = travelClock.rateSecondsPerSecond
        _state.value = TimeTravelState.TRANSITIONING
    }

    /** Glides the clock back to the wall; the UI leaves travel mode immediately (v1). */
    @Synchronized
    fun returnToRealTime() {
        clock.returnToRealTime()
        _state.value = TimeTravelState.REAL_TIME
    }

    @Synchronized
    fun accelerate() {
        travelClock.accelerate()
        _rateSecondsPerSecond.value = travelClock.rateSecondsPerSecond
    }

    @Synchronized
    fun decelerate() {
        travelClock.decelerate()
        _rateSecondsPerSecond.value = travelClock.rateSecondsPerSecond
    }

    @Synchronized
    fun pauseTime() {
        travelClock.pause()
        _rateSecondsPerSecond.value = travelClock.rateSecondsPerSecond
    }

    /** TRANSITIONING resolves to TRAVELLING inside the pacer — no scope of its own needed. */
    @Synchronized
    private fun promoteArrival() {
        if (_state.value == TimeTravelState.TRANSITIONING &&
            clock.mode == TransitioningClock.Mode.TIME_TRAVEL
        ) {
            _state.value = TimeTravelState.TRAVELLING
        }
    }

    companion object {
        /** The old `AppGraph` wall-clock cadence: fine while the sky drifts at 1×. */
        const val REAL_TIME_TICK_MS = 60_000L

        /** ~30 Hz while the clock is in flight — v1 queried its clock every rendered frame. */
        const val TRAVEL_TICK_MS = 32L
    }
}
