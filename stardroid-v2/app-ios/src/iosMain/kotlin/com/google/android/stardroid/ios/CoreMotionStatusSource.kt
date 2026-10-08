/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorReading
import com.google.android.stardroid.sensors.SensorStatusSource
import com.google.android.stardroid.sensors.writeQuaternion
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import platform.CoreMotion.CMAttitudeReferenceFrameXMagneticNorthZVertical
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracy
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracyHigh
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracyLow
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracyMedium
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracyUncalibrated
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
 * - Rotation vector: the fused attitude against magnetic north, as Android's (x, y, z, w)
 *   quaternion of the phone→world (East, North, Up) rotation.
 * - Light: iOS offers apps no ambient light sensor.
 *
 * The accelerometer and gyroscope have no calibration level on iOS, so their accuracy is null.
 * Updates run only while collected, at ~50 Hz (Android's GAME rate), on a manager of their own:
 * the map's [CoreMotionOrientationSource] keeps its device-motion handler.
 */
@OptIn(ExperimentalForeignApi::class)
class CoreMotionStatusSource : SensorStatusSource {
    private val manager =
        CMMotionManager().apply {
            accelerometerUpdateInterval = UPDATE_INTERVAL_SECONDS
            gyroUpdateInterval = UPDATE_INTERVAL_SECONDS
            deviceMotionUpdateInterval = UPDATE_INTERVAL_SECONDS
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
            SensorKind.MAGNETOMETER -> deviceMotion.map { it.magneticField }
            SensorKind.ROTATION_VECTOR -> deviceMotion.map { it.rotationVector }
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

    /** One device-motion stream (a manager runs one handler) for the two kinds that need it. */
    private val deviceMotion: Flow<Motion> =
        callbackFlow {
            val quaternion = FloatArray(4)
            manager.startDeviceMotionUpdatesUsingReferenceFrame(
                CMAttitudeReferenceFrameXMagneticNorthZVertical,
                queue,
            ) { motion, _ ->
                if (motion == null) return@startDeviceMotionUpdatesUsingReferenceFrame
                val field =
                    motion.magneticField.useContents {
                        SensorReading(
                            accuracy = calibration(accuracy),
                            values = listOf(field.x, field.y, field.z).map { it.toFloat() },
                        )
                    }
                motion.attitude.rotationMatrix.useContents { toPhoneToWorld() }
                    .writeQuaternion(quaternion)
                trySend(Motion(field, SensorReading(accuracy = null, quaternion.toList())))
            }
            awaitClose { manager.stopDeviceMotionUpdates() }
        }.shareIn(MainScope(), SharingStarted.WhileSubscribed())

    private class Motion(val magneticField: SensorReading, val rotationVector: SensorReading)

    private companion object {
        /** 50 Hz, near Android's `SENSOR_DELAY_GAME`, which its status source asks for. */
        const val UPDATE_INTERVAL_SECONDS = 1.0 / 50

        /** Android's `SensorManager.STANDARD_GRAVITY`, in m/s². */
        const val STANDARD_GRAVITY = 9.80665

        /** Core Motion's compass calibration on Android's accuracy ladder. */
        fun calibration(accuracy: CMMagneticFieldCalibrationAccuracy): SensorAccuracy =
            when (accuracy) {
                CMMagneticFieldCalibrationAccuracyHigh -> SensorAccuracy.HIGH
                CMMagneticFieldCalibrationAccuracyMedium -> SensorAccuracy.MEDIUM
                CMMagneticFieldCalibrationAccuracyLow -> SensorAccuracy.LOW
                CMMagneticFieldCalibrationAccuracyUncalibrated -> SensorAccuracy.UNRELIABLE
                else -> SensorAccuracy.UNRELIABLE
            }
    }
}
