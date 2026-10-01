/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import kotlinx.coroutines.flow.Flow

/** The sensors the diagnostics screen reports on (v1 `DiagnosticActivity`'s five). */
enum class SensorKind {
    ACCELEROMETER,
    MAGNETOMETER,
    GYROSCOPE,
    ROTATION_VECTOR,
    LIGHT,
}

/**
 * A sensor's calibration level — the `SensorManager.SENSOR_STATUS_*` ladder as a type, with
 * the platform constants' values hardcoded so pure-JVM consumers never touch [SensorManager].
 */
enum class SensorAccuracy(private val platformValue: Int) {
    NO_CONTACT(-1),
    UNRELIABLE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    ;

    companion object {
        fun fromPlatform(value: Int): SensorAccuracy? =
            entries.firstOrNull { it.platformValue == value }
    }
}

/**
 * One sensor sample: the calibration level and the raw values. Values arrive as a defensive
 * copy — platform events recycle their arrays.
 */
data class SensorReading(
    val accuracy: SensorAccuracy?,
    val values: List<Float>,
)

/**
 * Presence and live readings for the sensors the diagnostics and calibration screens watch.
 * An interface (unlike [OrientationSource]'s fused stream, this is raw per-sensor data) so
 * ViewModels stay JVM-testable.
 */
interface SensorStatusSource {
    fun hasSensor(kind: SensorKind): Boolean

    /** Cold flow of readings; registers on collect, unregisters on cancel. */
    fun readings(kind: SensorKind): Flow<SensorReading>
}
