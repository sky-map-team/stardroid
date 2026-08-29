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
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.startup.StartupRouter
import com.google.android.stardroid.startup.StartupState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the startup gate needs to compose; null while the DataStore first loads. */
data class StartupUiState(
    val needsEula: Boolean,
    val needsWarmWelcome: Boolean,
    val needsWhatsNew: Boolean,
    val needsWhatsNewDuringWarmWelcome: Boolean,
)

/**
 * The v1 `SplashScreenActivity` sequence as reactive state: the splash holds until the
 * first [state] arrives, the EULA blocks everything, then the warm welcome or the What's
 * New dialog, then the map. Marks write through [StartupRouter], so each gate clears
 * itself the moment its fact persists.
 */
class StartupViewModel(
    private val router: StartupRouter,
    startupState: StartupState,
    private val analytics: Analytics = NoOpAnalytics,
) : ViewModel() {
    val state: StateFlow<StartupUiState?> =
        combine(
            router.needsEula,
            router.needsWarmWelcome,
            router.needsWhatsNew,
            router.needsWhatsNewDuringWarmWelcome,
            ::StartupUiState,
        ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Warm-welcome completion set this in v1; the map's no-sensor dialog honors it. */
    val suppressMissingSensorWarning: StateFlow<Boolean> =
        startupState.suppressMissingSensorWarning
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun acceptEula() {
        analytics.trackEvent(AnalyticsEvents.TOS_ACCEPTED_EVENT)
        viewModelScope.launch { router.markEulaAccepted() }
    }

    /** Declining only logs — the activity exits (v1 `eulaRejected`); nothing persists. */
    fun rejectEula() {
        analytics.trackEvent(AnalyticsEvents.TOS_REJECTED_EVENT)
    }

    /**
     * The welcome pager came up on first run (v1's funnel start). Tutorial replays from the
     * overflow sheet bypass these funnel callbacks entirely, so the D49 metrics stay first-run
     * only. A v1-history device also logs the one-off upgrade event here — this fires once per
     * arrival, whether or not the tour itself gets finished.
     */
    fun warmWelcomeStarted() {
        analytics.trackEvent(AnalyticsEvents.WARM_WELCOME_STARTED_EVENT)
        if (router.isV1Upgrade) {
            analytics.trackEvent(
                AnalyticsEvents.UPGRADED_TO_V2_EVENT,
                mapOf(AnalyticsEvents.UPGRADED_TO_V2_NEW_VERSION to router.appVersion),
            )
        }
    }

    fun warmWelcomeSlideViewed(slideNumber: Int) {
        analytics.trackEvent(
            AnalyticsEvents.WARM_WELCOME_SLIDE_VIEWED_EVENT,
            mapOf(AnalyticsEvents.WARM_WELCOME_SLIDE_NUMBER to slideNumber),
        )
    }

    /** Skip marks the welcome seen exactly like completion (v1); only the funnel differs. */
    fun skipWarmWelcome() {
        analytics.trackEvent(AnalyticsEvents.WARM_WELCOME_SKIPPED_EVENT)
        analytics.setUserProperty(AnalyticsEvents.COMPLETED_WARM_WELCOME, "false")
        viewModelScope.launch { router.markWarmWelcomeSeen() }
    }

    fun completeWarmWelcome() {
        analytics.trackEvent(AnalyticsEvents.WARM_WELCOME_COMPLETED_EVENT)
        analytics.setUserProperty(AnalyticsEvents.COMPLETED_WARM_WELCOME, "true")
        viewModelScope.launch { router.markWarmWelcomeSeen() }
    }

    fun dismissWhatsNew() {
        viewModelScope.launch { router.markWhatsNewSeen() }
    }
}
