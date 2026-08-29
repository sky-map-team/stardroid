/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.timetravel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.astronomy.Ephemeris
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/** A one-shot cue the map screen renders as v1's travel flash + toast. */
sealed interface TravelEffect {
    /** Entering travel toward [destination] ("Traveling through time to %s ..."). */
    data class Travel(val destination: Instant) : TravelEffect

    /** Coming home ("Back to the present ..."). */
    data object Return : TravelEffect
}

/**
 * The time state machine (layers-and-app.md): normal ↔ transitioning ↔ travelling, rate
 * stepping, and preset-event resolution. All clock authority lives in the app-scoped
 * [TimeController] — this ViewModel is the map screen's handle on it, so the state survives
 * activity recreation with the clock itself.
 */
class TimeTravelViewModel(
    private val timeController: TimeController,
    private val ephemeris: Ephemeris,
    private val analytics: Analytics = NoOpAnalytics,
    private val location: () -> LatLong = { LatLong(0.0, 0.0) },
) : ViewModel() {
    val state: StateFlow<TimeTravelState> = timeController.state

    /** Playback rate in simulated seconds per wall second; 0 while frozen. */
    val rateSecondsPerSecond: StateFlow<Double> = timeController.rateSecondsPerSecond

    /** The travel-aware clock readout for the time-player bar (fast ticks while travelling). */
    val currentTime: StateFlow<Instant> =
        timeController.times.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            timeController.now(),
        )

    private val _effects = MutableSharedFlow<TravelEffect>(extraBufferCapacity = 4)

    /** Flash-and-toast cues; v1 fired them on travel and return, never on "start from now". */
    val effects: SharedFlow<TravelEffect> = _effects.asSharedFlow()

    private val _searchTargets = MutableSharedFlow<CelestialObjectId>(extraBufferCapacity = 1)

    /**
     * The travelled-to event's object, emitted once the clock has *arrived* — v1 posted its
     * search 3 s out so the solar system had moved to the destination time before the target
     * position was read.
     */
    val searchTargets: SharedFlow<CelestialObjectId> = _searchTargets.asSharedFlow()

    /** The wall-clock now (greys out past fixed events in the picker). */
    fun wallTime(): Instant = timeController.wallNow()

    /**
     * Seeds the picker at the live app clock: the simulated instant while travelling (so
     * re-opening the dialog adjusts from where you are, not the present), else the wall now.
     */
    fun pickerSeed(): Instant = timeController.now()

    /**
     * Resolves a picker event to its target instant. Relative events ("next full moon") resolve
     * from the app clock's now, so while travelling they look forward from the visited time
     * rather than snapping back to the present. Null only for the sunrise/sunset presets when
     * the sun doesn't rise or set within a day at the observer's [location] (v1's "sun will
     * not set" toast).
     */
    fun resolve(event: TimeTravelEvent): Instant? =
        when (event.type) {
            TimeTravelEvent.Type.NOW -> timeController.wallNow()
            TimeTravelEvent.Type.NEXT_SUNRISE -> nextSunEvent(RiseSetIndicator.RISE)
            TimeTravelEvent.Type.NEXT_SUNSET -> nextSunEvent(RiseSetIndicator.SET)
            TimeTravelEvent.Type.NEXT_FULL_MOON ->
                nextLunarPhaseEvent(LunarPhase.FULL, timeController.now(), ephemeris)
            TimeTravelEvent.Type.NEXT_NEW_MOON ->
                nextLunarPhaseEvent(LunarPhase.NEW, timeController.now(), ephemeris)
            TimeTravelEvent.Type.FIXED -> event.timestamp!!
        }

    private fun nextSunEvent(indicator: RiseSetIndicator): Instant? =
        nextRiseSetTime(
            SolarSystemBody.SUN,
            timeController.now(),
            location(),
            indicator,
            ephemeris,
        )

    /**
     * [eventKey] names the picker preset for the analytics funnel (a resource entry name,
     * locale-independent); null means a hand-edited date/time — v1 logged the same event
     * either way. [searchTarget] aims the map at the event's object after arrival.
     */
    fun goTo(
        target: Instant,
        eventKey: String? = null,
        searchTarget: CelestialObjectId? = null,
    ) {
        trackTravel(eventKey)
        timeController.goTimeTravel(target)
        _effects.tryEmit(TravelEffect.Travel(target))
        if (searchTarget != null) {
            viewModelScope.launch {
                // Wait out the transition; if the user bails home first, drop the search.
                val settled = timeController.state.first { it != TimeTravelState.TRANSITIONING }
                if (settled == TimeTravelState.TRAVELLING) {
                    _searchTargets.emit(searchTarget)
                }
            }
        }
    }

    /** v1's "Start from now": travel to the present instant, frozen — no effects, as in v1. */
    fun goToNow() {
        trackTravel("now")
        timeController.goTimeTravel(timeController.wallNow())
    }

    fun returnToRealTime() {
        timeController.returnToRealTime()
        _effects.tryEmit(TravelEffect.Return)
    }

    fun accelerate() = timeController.accelerate()

    fun decelerate() = timeController.decelerate()

    fun pauseTime() = timeController.pauseTime()

    private fun trackTravel(eventKey: String?) {
        analytics.trackEvent(
            AnalyticsEvents.TIME_TRAVEL_USED_EVENT,
            mapOf(AnalyticsEvents.TIME_TRAVEL_EVENT_KEY to (eventKey ?: "custom")),
        )
    }
}
