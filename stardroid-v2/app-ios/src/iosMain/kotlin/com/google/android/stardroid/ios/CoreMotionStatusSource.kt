/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorReading
import com.google.android.stardroid.sensors.SensorStatusSource
import com.google.android.stardroid.sensors.writeQuaternion
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

/**
 * [SensorStatusSource] over Core Motion: Android's raw sensors in Android's units and axes (both
 * put the phone's X right, Y up the screen and Z out of it), so the diagnostics rows read alike.
 *
 * - Accelerometer: Core Motion's g, negated into Android's m/s². Face up on a table, Android
 *   reads +9.8 on Z, the push of the table; Core Motion reads −1, gravity.
 * - Magnetometer: device motion's calibrated field in µT, as Android's `TYPE_MAGNETIC_FIELD` is
 *   bias-corrected, with Core Motion's calibration level as the accuracy.
 * - Gyroscope: rad/s, the same right-hand sign as Android's.
 * - Rotation vector: the map's fused attitude, as Android's (x, y, z, w) quaternion of the
 *   phone→world (East, North, Up) rotation.
 * - Light: iOS offers apps no ambient light sensor.
 *
 * The accelerometer and gyroscope have no calibration level on iOS, so their accuracy is null.
 * They run only while collected, at ~50 Hz (Android's GAME rate). The magnetometer and rotation
 * vector come from the map's device-motion stream ([deviceMotion]), at its rate and only while
 * the app is in the foreground: [manager] is the app's one manager, and it runs one
 * device-motion handler.
 */
@OptIn(ExperimentalForeignApi::class)
internal class CoreMotionStatusSource(
    private val manager: CMMotionManager,
    private val deviceMotion: Flow<CoreMotionSample>,
) : SensorStatusSource {
    init {
        manager.accelerometerUpdateInterval = UPDATE_INTERVAL_SECONDS
        manager.gyroUpdateInterval = UPDATE_INTERVAL_SECONDS
    }

    private val queue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }

    override fun hasSensor(kind: SensorKind): Boolean =
        when (kind) {
            SensorKind.ACCELEROMETER -> manager.accelerometerAvailable
            SensorKind.MAGNETOMETER -> manager.magnetometerAvailable
            SensorKind.GYROSCOPE -> manager.gyroAvailable
            SensorKind.ROTATION_VECTOR -> manager.deviceMotionAvailable
            SensorKind.LIGHT -> false
        }

    override fun readings(kind: SensorKind): Flow<SensorReading> {
        if (!hasSensor(kind)) return emptyFlow()
        return when (kind) {
            SensorKind.ACCELEROMETER -> accelerometer()
            SensorKind.GYROSCOPE -> gyroscope()
            SensorKind.MAGNETOMETER -> deviceMotion.map { it.field }
            SensorKind.ROTATION_VECTOR ->
                deviceMotion.map {
                    SensorReading(
                        accuracy = null,
                        values = it.matrix.writeQuaternion(FloatArray(4)).toList(),
                    )
                }
            SensorKind.LIGHT -> emptyFlow()
        }.conflate()
    }

    private fun accelerometer(): Flow<SensorReading> =
        callbackFlow {
            manager.startAccelerometerUpdatesToQueue(queue) { data, _ ->
                data?.acceleration?.useContents {
                    trySend(
                        SensorReading(
                            accuracy = null,
                            values = listOf(x, y, z).map { (-it * STANDARD_GRAVITY).toFloat() },
                        ),
                    )
                }
            }
            awaitClose { manager.stopAccelerometerUpdates() }
        }

    private fun gyroscope(): Flow<SensorReading> =
        callbackFlow {
            manager.startGyroUpdatesToQueue(queue) { data, _ ->
                data?.rotationRate?.useContents {
                    trySend(
                        SensorReading(
                            accuracy = null,
                            values = listOf(x, y, z).map { it.toFloat() },
                        ),
                    )
                }
            }
            awaitClose { manager.stopGyroUpdates() }
        }

    private companion object {
        /** 50 Hz, near Android's `SENSOR_DELAY_GAME`, which its status source asks for. */
        const val UPDATE_INTERVAL_SECONDS = 1.0 / 50

        /** Android's `SensorManager.STANDARD_GRAVITY`, in m/s². */
        const val STANDARD_GRAVITY = 9.80665
    }
}
