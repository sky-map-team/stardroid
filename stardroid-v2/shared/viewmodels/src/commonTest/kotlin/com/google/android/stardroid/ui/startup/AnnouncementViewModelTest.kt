/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.announcements.Announcement
import com.google.android.stardroid.announcements.AnnouncementAction
import com.google.android.stardroid.announcements.AnnouncementSource
import com.google.android.stardroid.announcements.AnnouncementState
import com.google.android.stardroid.announcements.AnnouncementText
import com.google.android.stardroid.announcements.SeenRecord
import com.google.android.stardroid.announcements.Surface
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.settings.FakeSettings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = Instant.parse("2026-10-03T20:00:00Z")
    private val settings = FakeSettings()
    private val state = FakeState()
    private val source = FakeSource()
    private var flagAtLaunch = true
    private var flagNow = true

    private val experiments =
        object : ExperimentConfig {
            override fun isEnabled(experiment: Experiment) = flagAtLaunch

            override fun isEnabledNow(experiment: Experiment) = flagNow
        }

    private class FakeSource : AnnouncementSource {
        var cached = emptyList<Announcement>()
        var onServer: List<Announcement>? = null
        var hangs = false
        var refreshes = 0

        override suspend fun refresh() {
            refreshes++
            if (hangs) awaitCancellation()
            onServer?.let { cached = it }
        }

        override fun current() = cached
    }

    private class FakeState : AnnouncementState {
        override val seen = MutableStateFlow(emptyMap<String, SeenRecord>())

        override suspend fun markShown(
            id: String,
            surface: Surface,
            now: Instant,
        ) {
            val old = seen.value[id] ?: SeenRecord()
            seen.value += id to old.copy(shownOn = old.shownOn + surface)
        }

        override suspend fun dismiss(
            id: String,
            now: Instant,
        ) {
            seen.value += id to (seen.value[id] ?: SeenRecord()).copy(dismissed = true)
        }

        override suspend fun prune(
            activeIds: Set<String>,
            now: Instant,
        ) = Unit
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun message(
        id: String,
        minVersion: Long = 0,
    ) = Announcement(
        id,
        start = now - 1.days,
        end = now + 1.days,
        surfaces = setOf(Surface.INTERSTITIAL),
        minVersion = minVersion,
        text = mapOf("en" to AnnouncementText(id, "Body")),
        action = AnnouncementAction.OpenSky,
    )

    private fun viewModel() =
        AnnouncementViewModel(
            source = source,
            state = state,
            settings = settings,
            experimentConfig = experiments,
            locale = { LocaleSpec("en") },
            appVersion = 0,
            analytics = NoOpAnalytics,
            now = { now },
        )

    @Test
    fun anOpenShowsWhatItJustFetched() =
        runTest(dispatcher) {
            source.onServer = listOf(message("new"))
            val vm = viewModel()

            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            assertThat(vm.pending.value?.id).isEqualTo("new")
        }

    @Test
    fun aHungFetchFallsBackToTheCacheAfterTheWait() =
        runTest(dispatcher) {
            source.cached = listOf(message("cached"))
            source.hangs = true
            val vm = viewModel()
            launch { vm.onAppOpened() }

            advanceTimeBy(AnnouncementViewModel.REFRESH_WAIT - 1.milliseconds)
            assertThat(vm.readyOpen.value).isNull()
            advanceTimeBy(2.milliseconds)
            assertThat(vm.readyOpen.value).isEqualTo(1)

            vm.loadIfNeeded()
            runCurrent()
            assertThat(vm.pending.value?.id).isEqualTo("cached")
        }

    @Test
    fun anOpenAnsweredFromTheCacheStillLooksNew() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAppOpened()
            val first = vm.readyOpen.value

            vm.onAppOpened()

            assertThat(vm.readyOpen.value).isNotEqualTo(first)
        }

    @Test
    fun oneMessagePerOpenAndEachMessageOnce() =
        runTest(dispatcher) {
            source.cached = listOf(message("first"))
            val vm = viewModel()
            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()
            vm.close()

            source.cached = listOf(message("first"), message("second"))
            vm.loadIfNeeded()
            runCurrent()
            assertThat(vm.pending.value).isNull()

            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()
            assertThat(vm.pending.value?.id).isEqualTo("second")
            vm.close()

            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()
            assertThat(vm.pending.value).isNull()
        }

    @Test
    fun aDialogLeftUpStaysWhileLive() =
        runTest(dispatcher) {
            source.cached = listOf(message("a"))
            val vm = viewModel()
            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            source.cached = listOf(message("a"), message("b"))
            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            assertThat(vm.pending.value?.id).isEqualTo("a")
        }

    @Test
    fun aDialogLeftUpGoesWhenExpired() =
        runTest(dispatcher) {
            source.cached = listOf(message("a"))
            val vm = viewModel()
            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            now += 2.days
            vm.onAppOpened()

            assertThat(vm.pending.value).isNull()
        }

    @Test
    fun aDialogLeftUpGoesWhenWithdrawn() =
        runTest(dispatcher) {
            source.cached = listOf(message("a"))
            val vm = viewModel()
            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            source.onServer = emptyList()
            vm.onAppOpened()

            assertThat(vm.pending.value).isNull()
        }

    @Test
    fun theKillSwitchCountsWithinTheLaunch() =
        runTest(dispatcher) {
            source.cached = listOf(message("a"))
            val vm = viewModel()
            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            flagNow = false
            vm.onAppOpened()

            assertThat(vm.pending.value).isNull()
        }

    @Test
    fun switchingOnWaitsForTheNextLaunchButStillFetches() =
        runTest(dispatcher) {
            flagAtLaunch = false
            source.cached = listOf(message("a"))
            val vm = viewModel()

            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            assertThat(vm.pending.value).isNull()
            assertThat(source.refreshes).isEqualTo(1)
        }

    @Test
    fun theOptOutShowsNothing() =
        runTest(dispatcher) {
            settings.announcementsEnabledState.value = false
            source.cached = listOf(message("a"))
            val vm = viewModel()

            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            assertThat(vm.pending.value).isNull()
        }

    @Test
    fun aVersionFloorKeepsAMessageOffAnUnversionedPlatform() =
        runTest(dispatcher) {
            source.cached = listOf(message("android-only", minVersion = 1760))
            val vm = viewModel()

            vm.onAppOpened()
            vm.loadIfNeeded()
            runCurrent()

            assertThat(vm.pending.value).isNull()
        }
}
