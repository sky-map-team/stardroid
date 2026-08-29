/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.settings

import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.FakeAnalytics
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.settings.AutoDimness
import com.google.android.stardroid.settings.FakeSettings
import com.google.android.stardroid.settings.FontSize
import com.google.android.stardroid.settings.SensorDamping
import com.google.android.stardroid.settings.SensorSpeed
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val settings = FakeSettings()
    private val analytics = FakeAnalytics()
    private val viewModel by lazy { SettingsViewModel(settings, analytics) }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state starts on the v1 defaults`() =
        testScope.runTest {
            runCurrent()
            val state = viewModel.state.value
            assertThat(state.tapToIdentify).isTrue()
            assertThat(state.tapToIdentifyInAutoMode).isTrue()
            assertThat(state.autoLevelHorizon).isTrue()
            assertThat(state.fontSize).isEqualTo(FontSize.MEDIUM)
            assertThat(state.autoDimness).isEqualTo(AutoDimness.SYSTEM)
            assertThat(state.showSkyGradient).isTrue()
            assertThat(state.disableGyro).isFalse()
            assertThat(state.sensorSpeed).isEqualTo(SensorSpeed.STANDARD)
            assertThat(state.sensorDamping).isEqualTo(SensorDamping.EXTRA_HIGH)
            assertThat(state.reverseMagneticZ).isFalse()
            assertThat(state.useMagneticCorrection).isTrue()
            assertThat(state.viewDirectionMode).isEqualTo(ViewDirectionMode.STANDARD)
        }

    @Test
    fun `every setter round-trips through settings into the ui state`() =
        testScope.runTest {
            viewModel.setTapToIdentify(false)
            viewModel.setTapToIdentifyInAutoMode(true)
            viewModel.setAutoLevelHorizon(false)
            viewModel.setFontSize(FontSize.LARGE)
            viewModel.setAutoDimness(AutoDimness.CLASSIC)
            viewModel.setShowSkyGradient(false)
            viewModel.setDisableGyro(true)
            viewModel.setSensorSpeed(SensorSpeed.FAST)
            viewModel.setSensorDamping(SensorDamping.REALLY_HIGH)
            viewModel.setReverseMagneticZ(true)
            viewModel.setUseMagneticCorrection(false)
            viewModel.setViewDirectionMode(ViewDirectionMode.TELESCOPE)
            runCurrent()

            val state = viewModel.state.value
            assertThat(state.tapToIdentify).isFalse()
            assertThat(state.tapToIdentifyInAutoMode).isTrue()
            assertThat(state.autoLevelHorizon).isFalse()
            assertThat(state.fontSize).isEqualTo(FontSize.LARGE)
            assertThat(state.autoDimness).isEqualTo(AutoDimness.CLASSIC)
            assertThat(state.showSkyGradient).isFalse()
            assertThat(state.disableGyro).isTrue()
            assertThat(state.sensorSpeed).isEqualTo(SensorSpeed.FAST)
            assertThat(state.sensorDamping).isEqualTo(SensorDamping.REALLY_HIGH)
            assertThat(state.reverseMagneticZ).isTrue()
            assertThat(state.useMagneticCorrection).isFalse()
            assertThat(state.viewDirectionMode).isEqualTo(ViewDirectionMode.TELESCOPE)
        }

    @Test
    fun `the analytics opt-out round-trips and every write logs a preference change`() =
        testScope.runTest {
            runCurrent()
            assertThat(viewModel.state.value.enableAnalytics).isTrue()

            viewModel.setEnableAnalytics(false)
            viewModel.setDisableGyro(true)
            runCurrent()

            assertThat(viewModel.state.value.enableAnalytics).isFalse()
            assertThat(settings.enableAnalyticsState.value).isFalse()
            assertThat(
                analytics.events.map {
                    it.params[AnalyticsEvents.PREFERENCE_CHANGE_EVENT_VALUE]
                },
            ).containsExactly("enable_analytics:false", "disable_gyro:true")
                .inOrder()
            assertThat(analytics.eventNames().toSet())
                .containsExactly(AnalyticsEvents.PREFERENCE_CHANGE_EVENT)
        }
}
