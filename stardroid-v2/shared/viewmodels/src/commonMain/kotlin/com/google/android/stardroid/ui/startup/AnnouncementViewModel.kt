/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.announcements.AnnouncementAction
import com.google.android.stardroid.announcements.AnnouncementPolicy
import com.google.android.stardroid.announcements.AnnouncementSource
import com.google.android.stardroid.announcements.AnnouncementState
import com.google.android.stardroid.announcements.AnnouncementText
import com.google.android.stardroid.announcements.Surface
import com.google.android.stardroid.announcements.announcementsOn
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.ExperimentConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A message waiting in the launch dialog. */
data class PendingAnnouncement(
    val id: String,
    val text: AnnouncementText,
    val action: AnnouncementAction,
)

/**
 * The interstitial surface. [loadIfNeeded] runs after the startup gates (EULA, welcome, What's
 * New) have cleared: once per process on Android, and once per open on iOS, whose process can
 * outlive many ([onAppOpened]). A message is recorded as shown the moment it is picked, so a
 * rotation or relaunch never shows it twice and a notification already posted for it suppresses
 * this dialog ([AnnouncementPolicy]).
 */
class AnnouncementViewModel(
    private val source: AnnouncementSource,
    private val state: AnnouncementState,
    private val settings: Settings,
    private val experimentConfig: ExperimentConfig,
    private val locale: () -> LocaleSpec,
    private val appVersion: Long,
    private val analytics: Analytics,
    // Runs once the dismissal is persisted, so surfaces that render from state (the widget
    // banner) can repaint without it.
    private val onDismissed: suspend () -> Unit = {},
    private val now: () -> Instant = Clock.System::now,
) : ViewModel() {
    private val _pending = MutableStateFlow<PendingAnnouncement?>(null)
    val pending: StateFlow<PendingAnnouncement?> = _pending.asStateFlow()

    private val _readyOpen = MutableStateFlow<Int?>(null)

    /**
     * iOS: the number of the open whose refresh has settled, or null during one. A number, as a
     * cached fetch settles within a frame and a conflated flag would look unchanged to Compose.
     */
    val readyOpen: StateFlow<Int?> = _readyOpen.asStateFlow()
    private var opens = 0

    private var loaded = false

    /**
     * iOS, which has no background refresh: each open fetches, waiting only briefly so that a
     * slow reply can't raise a dialog in mid-use. A later reply is there for the next open.
     */
    suspend fun onAppOpened() {
        _readyOpen.value = null
        val open = ++opens
        // Whatever the flag says, as this fetch is also how the flags themselves are refreshed.
        withTimeoutOrNull(REFRESH_WAIT) { source.refresh() }
        val at = now()
        val active = source.current()
        state.prune(active.map { it.id }.toSet(), at)
        // A dialog left up when the app was last put away may have expired or been withdrawn.
        _pending.value?.let { shown ->
            val live =
                announcementsOn(experimentConfig, settings) &&
                    AnnouncementPolicy
                        .forSheet(at, active, state.seen.first(), appVersion)
                        .any { it.id == shown.id }
            if (!live) _pending.value = null
        }
        loaded = _pending.value != null
        _readyOpen.value = open
    }

    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            if (!announcementsOn(experimentConfig, settings)) return@launch
            val now = now()
            val message =
                AnnouncementPolicy.next(
                    Surface.INTERSTITIAL,
                    now,
                    source.current(),
                    state.seen.first(),
                    appVersion,
                ) ?: return@launch
            val text = message.localized(locale()) ?: return@launch
            state.markShown(message.id, Surface.INTERSTITIAL, now)
            analytics.trackEvent(
                AnalyticsEvents.ANNOUNCEMENT_SHOWN_EVENT,
                mapOf(
                    AnalyticsEvents.ANNOUNCEMENT_ID to message.id,
                    AnalyticsEvents.ANNOUNCEMENT_SURFACE to Surface.INTERSTITIAL.wire,
                ),
            )
            _pending.value = PendingAnnouncement(message.id, text, message.action)
        }
    }

    /** Close the dialog, leaving the message eligible for the widget. */
    fun close() {
        _pending.value = null
    }

    /** "Dismiss": the user does not want it, so it goes from every surface. */
    fun dismiss() {
        val current = _pending.value ?: return
        _pending.value = null
        analytics.trackEvent(
            AnalyticsEvents.ANNOUNCEMENT_DISMISSED_EVENT,
            mapOf(AnalyticsEvents.ANNOUNCEMENT_ID to current.id),
        )
        viewModelScope.launch {
            state.dismiss(current.id, now())
            onDismissed()
        }
    }

    companion object {
        val REFRESH_WAIT: Duration = 3.seconds
    }
}
