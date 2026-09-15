/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.sensors.CalibrationPrompt
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.sensors.SensorAccuracyMonitor
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorStatusSource
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The compass-calibration screen's state (screens-and-startup.md): the live magnetometer
 * calibration level, the "don't show again" preference, and the auto-prompt stream the map
 * collects while it's visible (v1 ran its `SensorAccuracyMonitor` for the life of the map
 * activity). Kept separate from Diagnostics — the roadmap's device-calibration mode will grow
 * this screen.
 */
class CompassCalibrationViewModel(
    private val sensorStatus: SensorStatusSource,
    private val settings: Settings,
    nowMillis: () -> Long,
    private val analytics: Analytics = NoOpAnalytics,
) : ViewModel() {
    val hasMagnetometer: Boolean = sensorStatus.hasSensor(SensorKind.MAGNETOMETER)

    /** Null until the first reading arrives; active only while the screen collects. */
    val accuracy: StateFlow<SensorAccuracy?> =
        sensorStatus
            .readings(SensorKind.MAGNETOMETER)
            .map { it.accuracy }
            // A short timeout survives config-change recomposition without unregistering and
            // immediately re-registering the magnetometer listener.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dontShowAgain: StateFlow<Boolean> =
        settings.dontShowCalibrationDialog
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDontShowAgain(enabled: Boolean) {
        viewModelScope.launch { settings.setDontShowCalibrationDialog(enabled) }
    }

    /**
     * Low-accuracy nudges for the map to act on (open this screen, or toast if opted out).
     * Cold: monitoring runs only while the map collects, and stops with it.
     */
    val prompts: Flow<CalibrationPrompt> =
        SensorAccuracyMonitor(settings, nowMillis)
            .prompts(sensorStatus.readings(SensorKind.MAGNETOMETER))
            .onEach { prompt ->
                // v1 SensorAccuracyMonitor's calibration-funnel events.
                analytics.trackEvent(
                    when (prompt) {
                        CalibrationPrompt.SCREEN ->
                            AnalyticsEvents.CALIBRATION_AUTO_TRIGGERED_EVENT
                        CalibrationPrompt.TOAST ->
                            AnalyticsEvents.CALIBRATION_TOAST_SHOWN_EVENT
                    },
                )
            }
}
