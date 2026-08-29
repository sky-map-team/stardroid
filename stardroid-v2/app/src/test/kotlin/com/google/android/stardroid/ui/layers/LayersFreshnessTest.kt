/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.layers

import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.satellites.SatelliteUiStatus
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The freshness badge and the empty-state trigger.
 *
 * Both are about telling the truth when the data is old or missing: the alternative is a layer
 * that silently draws nothing, which a user cannot distinguish from "there is nothing up there".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LayersFreshnessTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        settings: FakeSettings = FakeSettings(),
        freshness: ElementFreshness? = null,
        refreshAllowedIn: Duration = Duration.ZERO,
        onRefresh: suspend () -> Duration = { Duration.ZERO },
    ) = LayersViewModel(
        settings,
        satellitesEnabled = true,
        satelliteStatus =
            MutableStateFlow(freshness?.let { SatelliteUiStatus(it, refreshAllowedIn) }),
        onRefreshSatellites = onRefresh,
    )

    private fun satelliteRow(vm: LayersViewModel) =
        vm.toggles.value.single { it.id == SatelliteLayer.LAYER_ID }

    @Test
    fun `fresh data earns no badge at all`() =
        runTest(dispatcher.scheduler) {
            // The badge is an exception report, not a status light: a badge that is always present
            // is one nobody reads.
            runCurrent()
            assertThat(satelliteRow(viewModel(freshness = ElementFreshness.FRESH)).dataStatus)
                .isNull()
        }

    @Test
    fun `ageing, stale and absent map onto the status palette in order of severity`() =
        runTest(dispatcher.scheduler) {
            val ageing = viewModel(freshness = ElementFreshness.AGEING)
            val stale = viewModel(freshness = ElementFreshness.STALE)
            val absent = viewModel(freshness = ElementFreshness.ABSENT)
            runCurrent()

            assertThat(satelliteRow(ageing).dataStatus).isEqualTo(LayerDataStatus.AGEING)
            assertThat(satelliteRow(stale).dataStatus).isEqualTo(LayerDataStatus.STALE)
            assertThat(satelliteRow(absent).dataStatus).isEqualTo(LayerDataStatus.ABSENT)
        }

    @Test
    fun `no other layer ever carries a badge`() =
        runTest(dispatcher.scheduler) {
            // A star catalog shipped in the APK cannot go stale, so freshness is a satellite-only
            // concern and must not leak onto rows it means nothing for.
            val vm = viewModel(freshness = ElementFreshness.STALE)
            runCurrent()
            val others = vm.toggles.value.filterNot { it.id == SatelliteLayer.LAYER_ID }
            assertThat(others.mapNotNull { it.dataStatus }).isEmpty()
        }

    @Test
    fun `the empty state appears only when the layer is on and there is nothing cached`() =
        runTest(dispatcher.scheduler) {
            // Layer on, no data: explain it.
            val on = FakeSettings().apply { setLayerEnabled(SatelliteLayer.LAYER_ID, true) }
            val missing = viewModel(on, ElementFreshness.ABSENT)
            // Layer on with data: nothing to explain.
            val present = viewModel(on, ElementFreshness.FRESH)
            // Layer off: "you turned it off" needs no card, even with nothing cached.
            val off = FakeSettings().apply { setLayerEnabled(SatelliteLayer.LAYER_ID, false) }
            val hidden = viewModel(off, ElementFreshness.ABSENT)
            runCurrent()

            assertThat(missing.satelliteDataMissing.value).isTrue()
            assertThat(present.satelliteDataMissing.value).isFalse()
            assertThat(hidden.satelliteDataMissing.value).isFalse()
        }

    @Test
    fun `the empty state never appears when the feature is off entirely`() =
        runTest(dispatcher.scheduler) {
            val settings = FakeSettings()
            settings.setLayerEnabled(SatelliteLayer.LAYER_ID, true)
            val vm =
                LayersViewModel(
                    settings,
                    satellitesEnabled = false,
                    satelliteStatus =
                        MutableStateFlow(
                            SatelliteUiStatus(ElementFreshness.ABSENT, Duration.ZERO),
                        ),
                )
            runCurrent()
            assertThat(vm.satelliteDataMissing.value).isFalse()
        }

    @Test
    fun `no Refresh is offered while policy would refuse one`() =
        runTest(dispatcher.scheduler) {
            // A button that silently does nothing reads as a broken app. The real cause is a rate
            // limit that is nobody's fault, so the card says how long instead of offering a
            // control that cannot work.
            val waiting =
                viewModel(freshness = ElementFreshness.ABSENT, refreshAllowedIn = 26.hours)
            val ready = viewModel(freshness = ElementFreshness.ABSENT)
            runCurrent()

            assertThat(waiting.satelliteRefreshWait.value).isEqualTo(26.hours)
            assertThat(ready.satelliteRefreshWait.value).isEqualTo(Duration.ZERO)
        }

    @Test
    fun `a refused tap updates the wait immediately, without waiting for the next poll`() =
        runTest(dispatcher.scheduler) {
            // The poll is minutes apart. If a tap is refused, the card has to explain itself now,
            // not eventually.
            val vm =
                viewModel(
                    freshness = ElementFreshness.ABSENT,
                    onRefresh = { 24.hours },
                )
            runCurrent()
            assertThat(vm.satelliteRefreshWait.value).isEqualTo(Duration.ZERO)

            vm.refreshSatelliteData()
            runCurrent()
            assertThat(vm.satelliteRefreshWait.value).isEqualTo(24.hours)
        }
}
