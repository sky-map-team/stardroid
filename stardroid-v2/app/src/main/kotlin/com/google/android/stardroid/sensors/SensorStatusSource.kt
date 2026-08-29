/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow

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

/** [SensorStatusSource] on [SensorManager]. */
class SensorManagerStatusSource(
    private val sensorManager: SensorManager?,
) : SensorStatusSource {
    override fun hasSensor(kind: SensorKind): Boolean = defaultSensor(kind) != null

    override fun readings(kind: SensorKind): Flow<SensorReading> {
        val manager = sensorManager ?: return emptyFlow()
        val sensor = defaultSensor(kind) ?: return emptyFlow()
        return callbackFlow {
            // Track the latest values so a bare accuracy callback still carries a full reading.
            var lastValues: List<Float> = emptyList()
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        lastValues = event.values.toList()
                        trySend(
                            SensorReading(SensorAccuracy.fromPlatform(event.accuracy), lastValues),
                        )
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) {
                        trySend(SensorReading(SensorAccuracy.fromPlatform(accuracy), lastValues))
                    }
                }
            if (!manager.registerListener(listener, sensor, delayFor(kind))) {
                close()
            }
            awaitClose { manager.unregisterListener(listener) }
        }.conflate()
    }

    private fun defaultSensor(kind: SensorKind): Sensor? =
        sensorManager?.getDefaultSensor(
            when (kind) {
                SensorKind.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
                SensorKind.MAGNETOMETER -> Sensor.TYPE_MAGNETIC_FIELD
                SensorKind.GYROSCOPE -> Sensor.TYPE_GYROSCOPE
                SensorKind.ROTATION_VECTOR -> Sensor.TYPE_ROTATION_VECTOR
                SensorKind.LIGHT -> Sensor.TYPE_LIGHT
            },
        )

    /** v1 registered the light sensor at UI rate and the rest at NORMAL. */
    private fun delayFor(kind: SensorKind): Int =
        when (kind) {
            SensorKind.LIGHT -> SensorManager.SENSOR_DELAY_UI
            else -> SensorManager.SENSOR_DELAY_NORMAL
        }
}
