/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

/** How to nag about a poorly calibrated compass. */
enum class CalibrationPrompt {
    /** Open the calibration screen in its auto-dismissable form. */
    SCREEN,

    /** The user opted out of the screen; show v1's low-accuracy toast instead. */
    TOAST,
}

/**
 * Port of v1's `SensorAccuracyMonitor`: watches the magnetometer's calibration level while the
 * map is visible and prompts when it drops below MEDIUM — at most once per
 * [MIN_INTERVAL_BETWEEN_WARNINGS_MILLIS], persisted so restarts don't re-nag ([Settings]
 * replaces v1's SharedPreferences pair). Pure flow logic; the caller supplies the readings and
 * acts on the prompts.
 */
class SensorAccuracyMonitor(
    private val settings: Settings,
    private val nowMillis: () -> Long,
) {
    /**
     * The prompts to show, derived from [readings]. Fires on the first reading and on
     * accuracy *changes* (v1 only heard `onAccuracyChanged` after its first sample).
     */
    fun prompts(readings: Flow<SensorReading>): Flow<CalibrationPrompt> =
        readings
            .map { it.accuracy }
            .distinctUntilChanged()
            .transform { accuracy ->
                // Null means the platform reported an accuracy value we don't recognize — treat
                // it as unknown rather than nag on every such reading.
                val noPromptNeeded =
                    accuracy == null ||
                        accuracy == SensorAccuracy.HIGH ||
                        accuracy == SensorAccuracy.MEDIUM
                if (noPromptNeeded) {
                    return@transform
                }
                val now = nowMillis()
                val lastWarned = settings.lastCalibrationWarningMillis.first()
                if (now - lastWarned < MIN_INTERVAL_BETWEEN_WARNINGS_MILLIS) return@transform
                settings.setLastCalibrationWarningMillis(now)
                emit(
                    if (settings.dontShowCalibrationDialog.first()) {
                        CalibrationPrompt.TOAST
                    } else {
                        CalibrationPrompt.SCREEN
                    },
                )
            }

    companion object {
        /** v1: three minutes between warnings. */
        const val MIN_INTERVAL_BETWEEN_WARNINGS_MILLIS = 180_000L
    }
}
