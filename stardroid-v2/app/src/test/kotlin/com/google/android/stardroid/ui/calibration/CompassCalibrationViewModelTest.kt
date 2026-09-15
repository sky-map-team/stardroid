/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.calibration

import com.google.android.stardroid.sensors.CalibrationPrompt
import com.google.android.stardroid.sensors.FakeSensorStatusSource
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorReading
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class CompassCalibrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val settings = FakeSettings()
    private val sensors = FakeSensorStatusSource()
    private val viewModel by lazy {
        CompassCalibrationViewModel(sensors, settings, nowMillis = { 1_000_000L })
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `accuracy follows the magnetometer readings while collected`() =
        testScope.runTest {
            val collector = launch { viewModel.accuracy.collect {} }
            runCurrent()
            assertThat(viewModel.accuracy.value).isNull()

            sensors.emit(
                SensorKind.MAGNETOMETER,
                SensorReading(SensorAccuracy.MEDIUM, listOf(1f, 2f, 3f)),
            )
            runCurrent()
            assertThat(viewModel.accuracy.value).isEqualTo(SensorAccuracy.MEDIUM)
            collector.cancel()
        }

    @Test
    fun `dont-show-again round-trips through settings`() =
        testScope.runTest {
            runCurrent()
            assertThat(viewModel.dontShowAgain.value).isFalse()
            viewModel.setDontShowAgain(true)
            runCurrent()
            assertThat(viewModel.dontShowAgain.value).isTrue()
            assertThat(settings.dontShowCalibrationDialogState.value).isTrue()
        }

    @Test
    fun `prompts surface the monitor's nudges`() =
        testScope.runTest {
            val prompts = mutableListOf<CalibrationPrompt>()
            val collector = launch { viewModel.prompts.collect { prompts.add(it) } }
            runCurrent()

            sensors.emit(
                SensorKind.MAGNETOMETER,
                SensorReading(SensorAccuracy.LOW, emptyList()),
            )
            runCurrent()

            assertThat(prompts).containsExactly(CalibrationPrompt.SCREEN)
            collector.cancel()
        }

    @Test
    fun `magnetometer presence is reported`() {
        assertThat(viewModel.hasMagnetometer).isTrue()
        val without =
            CompassCalibrationViewModel(
                FakeSensorStatusSource(present = emptySet()),
                settings,
                nowMillis = { 0L },
            )
        assertThat(without.hasMagnetometer).isFalse()
    }
}
