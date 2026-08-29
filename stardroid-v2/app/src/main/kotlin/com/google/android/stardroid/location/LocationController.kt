/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The app-scoped location bus, v1's `LocationController` state machine over the
 * [LocationProvider] edge. Like `TimeController`, it lives in the app's singleton graph so every consumer —
 * `MapViewModel`'s local frame, the horizon layer, `LocationViewModel`'s UI — shares one
 * [locations] flow and one [state].
 *
 * v1's SharedPreferences become [Settings]: `no_auto_locate` selects manual mode, and the
 * last confirmed position persists (v1 wrote `latitude`/`longitude` on every fix *and* every
 * manual entry, and read them back in manual mode — same keys, same behavior). One v2
 * addition: [start] seeds [locations] from the persisted position in *any* mode, so a
 * returning user's sky is right immediately instead of Greenwich-until-first-fix; the
 * *state* still reflects that no fresh fix has arrived.
 *
 * Main-thread confined: UI calls, the provider's main-looper callbacks, and [scope]'s jobs
 * all run on the main dispatcher, so the state machine needs no locks.
 */
class LocationController(
    private val provider: LocationProvider,
    private val settings: Settings,
    private val hasLocationPermission: () -> Boolean,
    private val wallTimeMillis: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    private val _state = MutableStateFlow<LocationState>(LocationState.Unset)

    /** The state machine's position, for the location UI. */
    val state: StateFlow<LocationState> = _state.asStateFlow()

    private val _locations = MutableStateFlow(DEFAULT_LOCATION)

    /** The observer position every sky consumer uses; Greenwich until anything better. */
    val locations: StateFlow<LatLong> = _locations.asStateFlow()

    private val _fixes =
        MutableSharedFlow<LatLong>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Fresh confirmations only — each manual entry and each provider fix that actually
     * *moved*, but *not* the persisted location re-loaded at startup nor the unchanged
     * position a provider re-delivers when updates restart (rotation recreates the activity,
     * which stop/starts the controller). Drives v1's "Location set to X" toast, which should
     * only fire when the location changes.
     */
    val fixes: SharedFlow<LatLong> = _fixes.asSharedFlow()

    /** The last position [fixes] emitted, for suppressing re-delivered identical fixes. */
    private var lastFixEvent: LatLong? = null

    /** Manual mode selected (`no_auto_locate`), surfaced for the location UI's mode toggle. */
    val noAutoLocate: Flow<Boolean> = settings.noAutoLocate

    private var timeoutJob: Job? = null

    private var startJob: Job? = null

    /**
     * v1 `start()`, run on every activity start: manual mode loads the persisted location,
     * auto mode starts provider updates if permitted, and otherwise the state rests at
     * [LocationState.Unset] so the UI offers the permission rationale.
     */
    fun start() {
        startJob?.cancel()
        startJob =
            scope.launch {
                val saved = settings.savedLocation.first()
                if (saved != null && _state.value !is LocationState.Confirmed) {
                    _locations.value = saved
                }
                when {
                    settings.noAutoLocate.first() -> loadSavedLocation(saved)
                    hasLocationPermission() -> startAuto()
                    // Keep an existing denied state: resetting it to Unset on every resume
                    // would re-offer the rationale after the user already answered.
                    else -> if (!stateIsDenied()) _state.value = LocationState.Unset
                }
            }
    }

    /** v1 `stop()`: halt updates while the app is backgrounded; the state stays put. */
    fun stop() {
        cancelPendingStart()
        provider.stopUpdates()
        cancelAcquiringTimeout()
    }

    /** Begins (or restarts) automatic acquisition; assumes the permission is held. */
    fun startAuto() {
        if (!provider.isAvailable()) {
            _state.value = LocationState.HardwareUnavailable
            return
        }
        // Already tracking? Just restart updates — don't regress a good fix to Acquiring.
        val alreadyAuto =
            (_state.value as? LocationState.Confirmed)?.source == LocationSource.AUTO
        if (!alreadyAuto) {
            _state.value = LocationState.Acquiring
            scheduleAcquiringTimeout()
        }
        provider.startUpdates(LOCATION_UPDATE_MIN_DISTANCE_METRES, ::onLocationUpdate)
    }

    /** The permission request came back negative; [canAsk] is `shouldShowRationale`. */
    fun onPermissionDenied(canAsk: Boolean) {
        cancelPendingStart()
        provider.stopUpdates()
        cancelAcquiringTimeout()
        _state.value =
            if (canAsk) {
                LocationState.PermissionDenied
            } else {
                LocationState.PermissionPermanentlyDenied
            }
    }

    /** The permission disappeared while we were tracking (revoked in system settings). */
    fun onPermissionRevoked() {
        cancelPendingStart()
        provider.stopUpdates()
        cancelAcquiringTimeout()
        _state.value = LocationState.PermissionDenied
    }

    /** Applies a user-entered position and switches to manual mode (v1 semantics). */
    fun setManualLocation(location: LatLong) {
        cancelPendingStart()
        cancelAcquiringTimeout()
        provider.stopUpdates()
        scope.launch {
            settings.setSavedLocation(location)
            settings.setNoAutoLocate(true)
        }
        _locations.value = location
        _state.value =
            LocationState.Confirmed(
                location = location,
                source = LocationSource.MANUAL,
                accuracyM = null,
                timestampMillis = wallTimeMillis(),
            )
        // A manual entry is a deliberate user action, so it always gets its confirmation.
        lastFixEvent = location
        _fixes.tryEmit(location)
    }

    /** Leaves manual mode and starts acquiring (the caller has checked the permission). */
    fun switchToAuto() {
        cancelPendingStart()
        scope.launch { settings.setNoAutoLocate(false) }
        startAuto()
    }

    /** Enters manual mode without a position yet; the entry dialog follows (v1). */
    fun switchToManual() {
        cancelPendingStart()
        provider.stopUpdates()
        cancelAcquiringTimeout()
        scope.launch { settings.setNoAutoLocate(true) }
    }

    /** The user chose to wait past the acquiring timeout; rearm it. */
    fun keepWaiting() {
        cancelPendingStart()
        cancelAcquiringTimeout()
        _state.value = LocationState.Acquiring
        scheduleAcquiringTimeout()
    }

    private fun onLocationUpdate(
        location: LatLong,
        accuracyM: Float?,
    ) {
        cancelAcquiringTimeout()
        scope.launch { settings.setSavedLocation(location) }
        _locations.value = location
        _state.value =
            LocationState.Confirmed(
                location = location,
                source = LocationSource.AUTO,
                accuracyM = accuracyM,
                timestampMillis = wallTimeMillis(),
            )
        // The provider's min-distance filter only holds within one registration; every
        // restart (rotation, foregrounding) re-delivers the current position. Only a
        // position that moved past the same filter distance is a fresh-fix *event*.
        val last = lastFixEvent
        if (last == null || last.distanceTo(location) >= FIX_EVENT_MIN_DISTANCE_DEG) {
            lastFixEvent = location
            _fixes.tryEmit(location)
        }
    }

    private fun loadSavedLocation(saved: LatLong?) {
        if (saved != null) {
            _state.value =
                LocationState.Confirmed(
                    location = saved,
                    source = LocationSource.MANUAL,
                    accuracyM = null,
                    timestampMillis = wallTimeMillis(),
                )
        } else {
            _state.value = LocationState.Unset
        }
    }

    private fun scheduleAcquiringTimeout() {
        cancelAcquiringTimeout()
        timeoutJob =
            scope.launch {
                delay(LOCATION_ACQUIRING_TIMEOUT_MS)
                if (_state.value is LocationState.Acquiring) {
                    _state.value = LocationState.AcquiringTimeout
                }
            }
    }

    private fun cancelAcquiringTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /**
     * Every state mutator cancels a still-suspended [start] first, so a stale startup load
     * can't resume later and overwrite the state the user just chose.
     */
    private fun cancelPendingStart() {
        startJob?.cancel()
        startJob = null
    }

    private fun stateIsDenied() =
        _state.value is LocationState.PermissionDenied ||
            _state.value is LocationState.PermissionPermanentlyDenied

    companion object {
        /** Until a real fix arrives — Greenwich, like v1's default. */
        val DEFAULT_LOCATION = LatLong(51.48, 0.0)

        /** v1 `ApplicationConstants.LOCATION_UPDATE_MIN_DISTANCE_METRES`. */
        const val LOCATION_UPDATE_MIN_DISTANCE_METRES = 2000f

        /** v1 `ApplicationConstants.LOCATION_ACQUIRING_TIMEOUT_MS`. */
        const val LOCATION_ACQUIRING_TIMEOUT_MS = 30_000L

        /**
         * [LOCATION_UPDATE_MIN_DISTANCE_METRES] as great-circle degrees (~111.2 km/degree):
         * an auto fix closer than this to the last emitted one is a re-delivery, not news.
         */
        const val FIX_EVENT_MIN_DISTANCE_DEG =
            LOCATION_UPDATE_MIN_DISTANCE_METRES / 111_195.0
    }
}
