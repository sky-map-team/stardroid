/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.layers

import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.FakeAnalytics
import com.google.android.stardroid.layers.LayerParameter
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LayersViewModelParameterTest {
    private val settings = FakeSettings()
    private val analytics = FakeAnalytics()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LayersViewModel(settings, analytics, satellitesEnabled = false)

    @Test
    fun `the sheet is offered the declared parameter at its default`() =
        runTest {
            val state = viewModel().parameters.first().discSize()
            assertThat(state.id).isEqualTo(SolarSystemLayer.LAYER_ID)
            assertThat(state.parameter).isEqualTo(LayerParameter.DISC_SIZE_PARAMETER)
            assertThat(state.selected)
                .isEqualTo(LayerParameter.DISC_SIZE_PARAMETER.defaultOption)
        }

    @Test
    fun `choosing an option persists it and shows it back`() =
        runTest {
            val vm = viewModel()
            vm.setParameter(
                SolarSystemLayer.LAYER_ID,
                LayerParameter.DISC_SIZE,
                LayerParameter.DISC_SIZE_TRUE,
            )

            assertThat(
                settings
                    .layerParameter(
                        SolarSystemLayer.LAYER_ID,
                        LayerParameter.DISC_SIZE,
                        LayerParameter.DISC_SIZE_PARAMETER.defaultOption,
                    ).first(),
            ).isEqualTo(LayerParameter.DISC_SIZE_TRUE)
            assertThat(vm.parameters.first().discSize().selected)
                .isEqualTo(LayerParameter.DISC_SIZE_TRUE)
        }

    @Test
    fun `the choice is reported to analytics, with which option was picked`() =
        runTest {
            viewModel().setParameter(
                SolarSystemLayer.LAYER_ID,
                LayerParameter.DISC_SIZE,
                LayerParameter.DISC_SIZE_TRUE,
            )

            val event =
                analytics.events.single { it.name == AnalyticsEvents.LAYER_PARAMETER_EVENT }
            assertThat(event.params)
                .containsExactly(
                    AnalyticsEvents.LAYER_PARAMETER_LAYER,
                    SolarSystemLayer.LAYER_ID.id,
                    AnalyticsEvents.LAYER_PARAMETER_KEY,
                    LayerParameter.DISC_SIZE,
                    AnalyticsEvents.LAYER_PARAMETER_VALUE,
                    LayerParameter.DISC_SIZE_TRUE,
                )
        }

    @Test
    fun `each choice is reported, so the split between modes is measurable`() =
        runTest {
            val vm = viewModel()
            for (option in LayerParameter.DISC_SIZE_PARAMETER.options) {
                vm.setParameter(SolarSystemLayer.LAYER_ID, LayerParameter.DISC_SIZE, option)
            }

            val reported =
                analytics.events
                    .filter { it.name == AnalyticsEvents.LAYER_PARAMETER_EVENT }
                    .map { it.params[AnalyticsEvents.LAYER_PARAMETER_VALUE] }
            assertThat(reported)
                .containsExactlyElementsIn(LayerParameter.DISC_SIZE_PARAMETER.options)
                .inOrder()
        }

    @Test
    fun `toggling a layer still reports its own event, unchanged`() =
        runTest {
            viewModel().setEnabled(SolarSystemLayer.LAYER_ID, enabled = false)
            assertThat(analytics.eventNames()).contains(AnalyticsEvents.LAYER_TOGGLED_EVENT)
        }

    /**
     * The disc-size row specifically.
     *
     * These tests predate the satellite layer's pass-alert toggle, when the registry declared
     * exactly one parameter and `single()` was unambiguous. They are about disc size, so they now
     * say so rather than depending on being the only parameter in the app.
     */
    private fun List<LayerParameterState>.discSize(): LayerParameterState =
        single { it.parameter.key == LayerParameter.DISC_SIZE }
}
