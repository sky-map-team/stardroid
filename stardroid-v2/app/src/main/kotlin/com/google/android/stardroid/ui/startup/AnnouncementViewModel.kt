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
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** A message waiting in the launch dialog. */
data class PendingAnnouncement(
    val id: String,
    val text: AnnouncementText,
    val action: AnnouncementAction,
)

/**
 * The interstitial surface. [loadIfNeeded] runs once per process, after the startup gates
 * (EULA, welcome, What's New) have cleared; a message is recorded as shown the moment it is
 * picked, so a rotation or relaunch never shows it twice and a notification already posted for
 * it suppresses this dialog ([AnnouncementPolicy]).
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
) : ViewModel() {
    private val _pending = MutableStateFlow<PendingAnnouncement?>(null)
    val pending: StateFlow<PendingAnnouncement?> = _pending.asStateFlow()

    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        if (!experimentConfig.isEnabled(Experiment.ANNOUNCEMENTS)) return
        viewModelScope.launch {
            if (!settings.announcementsEnabled.first()) return@launch
            val now = Clock.System.now()
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
            state.dismiss(current.id, Clock.System.now())
            onDismissed()
        }
    }
}
