/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.location.Geocoding
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.math.LatLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The location feature's UI adapter (layers-and-app.md): the shared [LocationController]'s
 * state for the sheet and dialogs, v1's manual-entry validation
 * (`ManualLocationEntryDialogFragment`), and the "Location set to X" toast feed. Permission
 * *results* stay in the activity (the launcher is an Android edge); everything here is
 * JVM-testable.
 */
class LocationViewModel(
    private val controller: LocationController,
    private val geocoding: Geocoding,
) : ViewModel() {
    val state: StateFlow<LocationState> = controller.state

    /** Manual mode selected (v1's `no_auto_locate`) — drives the sheet's mode toggle. */
    val manualMode: StateFlow<Boolean> =
        controller.noAutoLocate.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** One per fresh fix or manual entry: the position plus its reverse-geocoded name. */
    data class LocationToast(
        val location: LatLong,
        val placeName: String?,
    )

    private val _toasts =
        MutableSharedFlow<LocationToast>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** v1's `showLocationToast`, minus the formatting: the UI owns the strings. */
    val toasts: SharedFlow<LocationToast> = _toasts.asSharedFlow()

    init {
        viewModelScope.launch {
            // collectLatest: a newer fix cancels the previous fix's still-running geocode,
            // so only the latest position ever becomes a toast.
            controller.fixes.collectLatest { location ->
                _toasts.emit(LocationToast(location, geocoding.reverseGeocode(location)))
            }
        }
    }

    // ---- Manual entry (v1 ManualLocationEntryDialogFragment) -----------------------------

    /** Why a typed place name didn't resolve. */
    enum class PlaceError {
        /** The geocoder answered but found nothing. */
        NOT_FOUND,

        /** No geocoder or no network. */
        UNAVAILABLE,
    }

    data class ManualEntryUi(
        val resolving: Boolean = false,
        val placeError: PlaceError? = null,
        /** Geocoded coordinates for the typed place; the dialog copies them into its fields. */
        val resolved: LatLong? = null,
        /** The place-name query that produced [resolved]; lets confirm skip a redundant lookup. */
        val resolvedQuery: String? = null,
        val latitudeInvalid: Boolean = false,
        val longitudeInvalid: Boolean = false,
    )

    private val _manualEntry = MutableStateFlow(ManualEntryUi())
    val manualEntry: StateFlow<ManualEntryUi> = _manualEntry.asStateFlow()

    private var resolveJob: Job? = null

    /** Clears errors and stale resolutions; called when the entry dialog opens. */
    fun resetManualEntry() {
        // Cancel an in-flight resolve so its result can't land on the freshly opened dialog.
        resolveJob?.cancel()
        resolveJob = null
        _manualEntry.value = ManualEntryUi()
    }

    /** Geocodes a typed place name into the coordinate fields (v1's Resolve button). */
    fun resolvePlace(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _manualEntry.value.resolving) return
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch { resolveAndAwait(trimmed) }
    }

    /**
     * Geocodes [place] and applies the result to [manualEntry], returning the coordinates on
     * success or `null` if the place could not be resolved (with [PlaceError] set). Shared by the
     * Resolve button and by [confirmManualLocation]'s auto-resolve.
     */
    private suspend fun resolveAndAwait(place: String): LatLong? {
        _manualEntry.update { it.copy(resolving = true, placeError = null, resolved = null) }
        return when (val result = geocoding.resolvePlace(place)) {
            is Geocoding.PlaceResult.Found -> {
                _manualEntry.update {
                    it.copy(resolving = false, resolved = result.location, resolvedQuery = place)
                }
                result.location
            }
            Geocoding.PlaceResult.NotFound -> {
                _manualEntry.update {
                    it.copy(
                        resolving = false,
                        placeError = PlaceError.NOT_FOUND,
                    )
                }
                null
            }
            Geocoding.PlaceResult.Unavailable -> {
                _manualEntry.update {
                    it.copy(resolving = false, placeError = PlaceError.UNAVAILABLE)
                }
                null
            }
        }
    }

    /**
     * Confirm handler for the manual dialog (the "Set Location" button). If a place name is
     * present and hasn't already been resolved into the current fields, geocode it first —
     * v1 required an explicit Resolve tap and forgetting it silently discarded the typed place.
     * Falls back to the typed coordinates otherwise. Returns true when a location was set and
     * the dialog may dismiss; a failed geocode leaves [PlaceError] set and returns false.
     */
    suspend fun confirmManualLocation(
        placeText: String,
        latitudeText: String,
        longitudeText: String,
    ): Boolean {
        if (_manualEntry.value.resolving) return false
        val place = placeText.trim()
        if (place.isNotEmpty() && place != _manualEntry.value.resolvedQuery) {
            val located = resolveAndAwait(place) ?: return false
            controller.setManualLocation(located)
            return true
        }
        return submitManualLocation(latitudeText, longitudeText)
    }

    /**
     * Validates the typed coordinates and, if sound, applies them as the manual location.
     * Returns true on success so the dialog can dismiss; field errors land in [manualEntry].
     * Validation is explicit (v1): out-of-range input is an error, never silently clamped
     * the way `LatLong`'s constructor would.
     */
    fun submitManualLocation(
        latitudeText: String,
        longitudeText: String,
    ): Boolean {
        val latitude = parseCoordinate(latitudeText)
        val longitude = parseCoordinate(longitudeText)
        val latitudeValid = latitude != null && latitude in -90.0..90.0
        val longitudeValid = longitude != null && longitude in -180.0..180.0
        _manualEntry.update {
            it.copy(latitudeInvalid = !latitudeValid, longitudeInvalid = !longitudeValid)
        }
        if (latitude == null || longitude == null || !latitudeValid || !longitudeValid) {
            return false
        }
        controller.setManualLocation(LatLong(latitude, longitude))
        return true
    }

    // ---- Controller delegates (one surface for the UI) -----------------------------------

    fun switchToAuto() = controller.switchToAuto()

    fun switchToManual() = controller.switchToManual()

    fun keepWaiting() = controller.keepWaiting()

    companion object {
        /**
         * v1's coordinate normalization: Arabic-Indic (٠-٩) and Eastern Arabic-Indic (۰-۹)
         * digits map to ASCII, both the comma and the Arabic decimal separator (٫) read as
         * the decimal point, and Unicode minus/dash variants (−, –, —) read as the ASCII
         * minus so pasted coordinates parse.
         */
        fun parseCoordinate(text: String): Double? {
            val normalized =
                buildString {
                    for (char in text.trim()) {
                        when (char) {
                            ',', '٫' -> append('.')
                            '−', '–', '—' -> append('-')
                            in '٠'..'٩' -> append('0' + (char - '٠'))
                            in '۰'..'۹' -> append('0' + (char - '۰'))
                            else -> append(char)
                        }
                    }
                }
            return normalized.toDoubleOrNull()
        }
    }
}
