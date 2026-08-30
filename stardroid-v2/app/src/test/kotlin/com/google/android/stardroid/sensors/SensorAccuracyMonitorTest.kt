/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SensorAccuracyMonitorTest {
    private val settings = FakeSettings()
    private var nowMillis = 1_000_000L
    private val monitor = SensorAccuracyMonitor(settings) { nowMillis }
    private val readings = MutableSharedFlow<SensorReading>()

    private fun reading(accuracy: SensorAccuracy) = SensorReading(accuracy, emptyList())

    @Test
    fun `low accuracy prompts the calibration screen and stamps the warning time`() =
        runTest {
            val prompts = mutableListOf<CalibrationPrompt>()
            val job = launch { monitor.prompts(readings).collect { prompts.add(it) } }
            runCurrent()

            readings.emit(reading(SensorAccuracy.LOW))
            runCurrent()

            assertThat(prompts).containsExactly(CalibrationPrompt.SCREEN)
            assertThat(settings.lastCalibrationWarningMillisState.value).isEqualTo(nowMillis)
            job.cancel()
        }

    @Test
    fun `good accuracy stays silent`() =
        runTest {
            val prompts = mutableListOf<CalibrationPrompt>()
            val job = launch { monitor.prompts(readings).collect { prompts.add(it) } }
            runCurrent()

            readings.emit(reading(SensorAccuracy.HIGH))
            readings.emit(reading(SensorAccuracy.MEDIUM))
            runCurrent()

            assertThat(prompts).isEmpty()
            job.cancel()
        }

    @Test
    fun `warnings are throttled to one per three minutes, persisted`() =
        runTest {
            val prompts = mutableListOf<CalibrationPrompt>()
            val job = launch { monitor.prompts(readings).collect { prompts.add(it) } }
            runCurrent()

            readings.emit(reading(SensorAccuracy.LOW))
            runCurrent()
            // A different bad level inside the window stays quiet...
            nowMillis += SensorAccuracyMonitor.MIN_INTERVAL_BETWEEN_WARNINGS_MILLIS - 1
            readings.emit(reading(SensorAccuracy.UNRELIABLE))
            runCurrent()
            assertThat(prompts).hasSize(1)

            // ...but re-warns once the window has passed.
            nowMillis += SensorAccuracyMonitor.MIN_INTERVAL_BETWEEN_WARNINGS_MILLIS
            readings.emit(reading(SensorAccuracy.LOW))
            runCurrent()
            assertThat(prompts).hasSize(2)
            job.cancel()
        }

    @Test
    fun `a steady bad level only warns once - v1 only heard accuracy changes`() =
        runTest {
            val prompts = mutableListOf<CalibrationPrompt>()
            val job = launch { monitor.prompts(readings).collect { prompts.add(it) } }
            runCurrent()

            readings.emit(reading(SensorAccuracy.LOW))
            nowMillis += 2 * SensorAccuracyMonitor.MIN_INTERVAL_BETWEEN_WARNINGS_MILLIS
            readings.emit(reading(SensorAccuracy.LOW))
            runCurrent()

            assertThat(prompts).hasSize(1)
            job.cancel()
        }

    @Test
    fun `the opted-out user gets a toast instead of the screen`() =
        runTest {
            settings.dontShowCalibrationDialogState.value = true
            val prompts = mutableListOf<CalibrationPrompt>()
            val job = launch { monitor.prompts(readings).collect { prompts.add(it) } }
            runCurrent()

            readings.emit(reading(SensorAccuracy.UNRELIABLE))
            runCurrent()

            assertThat(prompts).containsExactly(CalibrationPrompt.TOAST)
            job.cancel()
        }
}
