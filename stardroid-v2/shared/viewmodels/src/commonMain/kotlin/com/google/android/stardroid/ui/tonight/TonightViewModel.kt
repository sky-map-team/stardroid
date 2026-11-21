/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.tonight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.surfaces.NotificationText
import com.google.android.stardroid.surfaces.targetObjectId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * How many highlights the sheet lists. Deliberately separate from the widget's and the digest's
 * default, so the sheet can show more later without changing them (D149a).
 */
const val SHEET_HIGHLIGHT_LIMIT = 3

/** An announcement as the sheet shows it: localized, with an optional search to run. */
data class TonightNotice(
    val title: String,
    val body: String,
    val searchQuery: String?,
)

/** One highlight, worded in full. [targetId] is the catalog id of the card it opens. */
data class TonightRow(
    val text: String,
    val trailing: String,
    val quality: Double,
    val targetId: String,
)

data class TonightState(
    val sunset: String?,
    val darkness: String?,
    val notices: List<TonightNotice>,
    val rows: List<TonightRow>,
    val next: CountdownTarget?,
    val daysToNext: Int,
)

/** The platform's wording for the sheet: the shared sentences and its own clock format. */
class TonightWords(
    val text: NotificationText,
    val formatTime: (Instant) -> String,
)

/**
 * The Tonight sheet (D149): tonight's highlights in full, the sun times and any active
 * announcements. Read-only; the wording is the shared [NotificationText], so it can't disagree
 * with the widget or the digest.
 */
class TonightViewModel(
    private val now: () -> Instant,
    private val location: suspend () -> LatLong?,
    private val showers: suspend () -> List<MeteorShower>,
    private val passes: suspend (LatLong) -> List<SatellitePass>,
    private val notices: suspend () -> List<TonightNotice>,
    private val words: suspend () -> TonightWords,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val limit: Int = SHEET_HIGHLIGHT_LIMIT,
) : ViewModel() {
    private companion object {
        val EMPTY = TonightState(null, null, emptyList(), emptyList(), null, 0)
    }

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _state = MutableStateFlow<TonightState?>(null)
    val state: StateFlow<TonightState?> = _state.asStateFlow()

    private var loading: Job? = null

    fun open() {
        _visible.value = true
        _state.value = null
        loading?.cancel()
        loading =
            viewModelScope.launch {
                // Opened from a notification tap: a catalog or I/O failure shows an empty sheet
                // rather than taking the app down.
                _state.value = guarded { build() } ?: EMPTY
            }
    }

    fun close() {
        loading?.cancel()
        _visible.value = false
    }

    private suspend fun build(): TonightState {
        val at = now()
        val where = location()
        val sky =
            tonightSky(
                at,
                where,
                showers(),
                zone = zone(),
                passes = where?.let { passes(it) }.orEmpty(),
                limit = limit,
            )
        val wording = words()
        val text = wording.text
        val format = wording.formatTime
        val today = at.toLocalDateTime(zone()).date
        return TonightState(
            sunset = sky.sunset?.let(format),
            darkness = sky.darknessStart?.let(format),
            // A bad announcement source must not hide the highlights.
            notices = guarded { notices() } ?: emptyList(),
            rows =
                sky.highlights.map {
                    val line = text.highlight(it)
                    TonightRow(line.text, line.trailing, it.quality, it.targetObjectId)
                },
            next = sky.countdown,
            daysToNext =
                sky.countdown?.let {
                    today.daysUntil(it.time.toLocalDateTime(zone()).date)
                } ?: 0,
        )
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Silent on purpose: commonMain has no logger, and no analytics may carry this.
            null
        }
}
