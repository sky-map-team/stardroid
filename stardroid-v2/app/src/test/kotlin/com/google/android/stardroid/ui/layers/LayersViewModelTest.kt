/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.layers

import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LayersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettings()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `layers default to enabled in registry order, except those the registry defaults off`() =
        runTest(dispatcher.scheduler) {
            val vm = LayersViewModel(settings, satellitesEnabled = false)
            runCurrent()
            assertThat(vm.toggles.value.map { it.id })
                .containsExactlyElementsIn(LayerRegistry.toggleableIds(satellitesEnabled = false))
                .inOrder()
            for (toggle in vm.toggles.value) {
                assertThat(toggle.enabled).isEqualTo(LayerRegistry.defaultEnabled(toggle.id))
            }
        }

    @Test
    fun `setEnabled writes through settings and updates the toggle state`() =
        runTest(dispatcher.scheduler) {
            val vm = LayersViewModel(settings, satellitesEnabled = false)
            runCurrent()
            vm.setEnabled(CatalogLayers.STARS_LAYER_ID, false)
            runCurrent()
            val stars = vm.toggles.value.single { it.id == CatalogLayers.STARS_LAYER_ID }
            assertThat(stars.enabled).isFalse()
            // The explicit disable, plus every layer that defaults off on its own.
            val expectedDisabled =
                1 + LayerRegistry.toggleableIds(satellitesEnabled = false).count {
                    !LayerRegistry.defaultEnabled(it)
                }
            assertThat(vm.toggles.value.count { !it.enabled }).isEqualTo(expectedDisabled)
        }

    @Test
    fun `sky gradient toggle defaults on and writes through settings`() =
        runTest(dispatcher.scheduler) {
            val vm = LayersViewModel(settings, satellitesEnabled = false)
            runCurrent()
            assertThat(vm.skyGradientEnabled.value).isTrue()
            vm.setSkyGradientEnabled(false)
            runCurrent()
            assertThat(vm.skyGradientEnabled.value).isFalse()
        }

    @Test
    fun `hud toggle defaults on and writes through settings`() =
        runTest(dispatcher.scheduler) {
            val vm = LayersViewModel(settings, satellitesEnabled = false)
            runCurrent()
            assertThat(vm.hudEnabled.value).isTrue()
            vm.setHudEnabled(false)
            runCurrent()
            assertThat(vm.hudEnabled.value).isFalse()
            assertThat(settings.showHudState.value).isFalse()
        }

    @Test
    fun `the satellites row appears exactly when the experiment is on`() =
        runTest {
            // The test that was missing. The gate existed and LayerRegistry.toggleableIds() was
            // asserted directly, but nothing checked that the ViewModel was actually *given* the
            // flag - so a defaulted-and-never-wired parameter hid the layer from the UI entirely
            // while every test passed.
            val settings = FakeSettings()

            val off = LayersViewModel(settings, satellitesEnabled = false)
            assertThat(off.toggles.value.map { it.id })
                .doesNotContain(SatelliteLayer.LAYER_ID)

            val on = LayersViewModel(settings, satellitesEnabled = true)
            assertThat(on.toggles.value.map { it.id }).contains(SatelliteLayer.LAYER_ID)
        }
}
