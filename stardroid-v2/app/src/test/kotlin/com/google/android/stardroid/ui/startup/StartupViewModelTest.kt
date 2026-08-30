/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.FakeAnalytics
import com.google.android.stardroid.startup.FakeStartupState
import com.google.android.stardroid.startup.StartupRouter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val state = FakeStartupState()

    // A floor of 0 keeps this generic gating suite independent of the real
    // WARM_WELCOME_RESET_VERSION_CODE, which is far above the synthetic appVersion here.
    private val router = StartupRouter(state, appVersion = 7L, warmWelcomeResetVersionCode = 0L)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state loads from null to the fresh-install gates`() =
        runTest(dispatcher.scheduler) {
            val viewModel = StartupViewModel(router, state)
            assertThat(viewModel.state.value).isNull()

            dispatcher.scheduler.runCurrent()

            assertThat(viewModel.state.value)
                .isEqualTo(
                    StartupUiState(
                        needsEula = true,
                        needsWarmWelcome = true,
                        needsWhatsNew = true,
                        needsWhatsNewDuringWarmWelcome = false,
                    ),
                )
        }

    @Test
    fun `accepting the eula clears its gate`() =
        runTest(dispatcher.scheduler) {
            val viewModel = StartupViewModel(router, state)
            dispatcher.scheduler.runCurrent()

            viewModel.acceptEula()
            dispatcher.scheduler.runCurrent()

            assertThat(viewModel.state.value?.needsEula).isFalse()
        }

    @Test
    fun `completing the warm welcome clears What's New and arms the sensor opt-out`() =
        runTest(dispatcher.scheduler) {
            val viewModel = StartupViewModel(router, state)
            dispatcher.scheduler.runCurrent()

            viewModel.completeWarmWelcome()
            dispatcher.scheduler.runCurrent()

            assertThat(viewModel.state.value?.needsWarmWelcome).isFalse()
            assertThat(viewModel.state.value?.needsWhatsNew).isFalse()
            assertThat(viewModel.suppressMissingSensorWarning.value).isTrue()
        }

    @Test
    fun `eula decisions and the warm-welcome funnel hit analytics`() =
        runTest(dispatcher.scheduler) {
            val analytics = FakeAnalytics()
            val viewModel = StartupViewModel(router, state, analytics)
            dispatcher.scheduler.runCurrent()

            viewModel.acceptEula()
            viewModel.rejectEula()
            viewModel.warmWelcomeStarted()
            viewModel.warmWelcomeSlideViewed(2)
            viewModel.completeWarmWelcome()
            dispatcher.scheduler.runCurrent()

            assertThat(analytics.eventNames())
                .containsExactly(
                    AnalyticsEvents.TOS_ACCEPTED_EVENT,
                    AnalyticsEvents.TOS_REJECTED_EVENT,
                    AnalyticsEvents.WARM_WELCOME_STARTED_EVENT,
                    AnalyticsEvents.WARM_WELCOME_SLIDE_VIEWED_EVENT,
                    AnalyticsEvents.WARM_WELCOME_COMPLETED_EVENT,
                ).inOrder()
            assertThat(analytics.events[3].params)
                .containsEntry(AnalyticsEvents.WARM_WELCOME_SLIDE_NUMBER, 2)
            assertThat(analytics.userProperties)
                .containsEntry(AnalyticsEvents.COMPLETED_WARM_WELCOME, "true")
        }

    @Test
    fun `skipping the warm welcome still marks it seen but logs the skip`() =
        runTest(dispatcher.scheduler) {
            val analytics = FakeAnalytics()
            val viewModel = StartupViewModel(router, state, analytics)
            dispatcher.scheduler.runCurrent()

            viewModel.skipWarmWelcome()
            dispatcher.scheduler.runCurrent()

            assertThat(viewModel.state.value?.needsWarmWelcome).isFalse()
            assertThat(analytics.eventNames())
                .containsExactly(AnalyticsEvents.WARM_WELCOME_SKIPPED_EVENT)
            assertThat(analytics.userProperties)
                .containsEntry(AnalyticsEvents.COMPLETED_WARM_WELCOME, "false")
        }

    @Test
    fun `a v1 upgrader logs the upgrade event once the tour starts`() =
        runTest(dispatcher.scheduler) {
            val v1UpgradeState = FakeStartupState()
            val v1UpgradeRouter =
                StartupRouter(
                    v1UpgradeState,
                    appVersion = 7L,
                    warmWelcomeResetVersionCode = 0L,
                    isV1Upgrade = true,
                )
            val analytics = FakeAnalytics()
            val viewModel = StartupViewModel(v1UpgradeRouter, v1UpgradeState, analytics)
            dispatcher.scheduler.runCurrent()

            viewModel.warmWelcomeStarted()
            dispatcher.scheduler.runCurrent()

            assertThat(analytics.eventNames())
                .containsExactly(
                    AnalyticsEvents.WARM_WELCOME_STARTED_EVENT,
                    AnalyticsEvents.UPGRADED_TO_V2_EVENT,
                ).inOrder()
            assertThat(analytics.events[1].params)
                .containsEntry(AnalyticsEvents.UPGRADED_TO_V2_NEW_VERSION, 7L)
        }

    @Test
    fun `a non-v1 install does not log the upgrade event`() =
        runTest(dispatcher.scheduler) {
            val analytics = FakeAnalytics()
            val viewModel = StartupViewModel(router, state, analytics)
            dispatcher.scheduler.runCurrent()

            viewModel.warmWelcomeStarted()
            dispatcher.scheduler.runCurrent()

            assertThat(analytics.eventNames())
                .containsExactly(AnalyticsEvents.WARM_WELCOME_STARTED_EVENT)
        }

    @Test
    fun `dismissing What's New marks the current version seen`() =
        runTest(dispatcher.scheduler) {
            val viewModel = StartupViewModel(router, state)
            dispatcher.scheduler.runCurrent()

            viewModel.dismissWhatsNew()
            dispatcher.scheduler.runCurrent()

            assertThat(viewModel.state.value?.needsWhatsNew).isFalse()
            assertThat(state.whatsNewSeenVersionState.value).isEqualTo(7L)
        }
}
