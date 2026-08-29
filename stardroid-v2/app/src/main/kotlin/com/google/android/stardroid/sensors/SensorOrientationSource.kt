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
import android.view.Surface
import com.google.android.stardroid.astronomy.orientationFromSensors
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.settings.SensorDamping
import com.google.android.stardroid.settings.SensorSpeed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * [OrientationSource] backed by [SensorManager] — the port of v1's
 * `SensorOrientationController`.
 *
 * Prefers the fused rotation-vector sensor (unless [SensorConfig.disableGyro]); devices
 * without one fall back to v1's classic accelerometer+magnetometer path: each stream smoothed
 * by an [ExponentiallyWeightedSmoother] at the configured damping/speed, then fused by the
 * pure `orientationFromSensors` vector-rejection construction. A [config] change re-registers
 * the listeners under the new parameters — v1 applied its preferences on controller restart.
 *
 * Sensors report in the device's *natural* frame; [displayRotation] (a [Surface] `ROTATION_*`
 * value, read per sample) remaps each matrix into the current display frame so `STANDARD`
 * screen-up stays up in landscape. v1 never needed this — it pinned its activities to
 * `screenOrientation="nosensor"`.
 */
class SensorOrientationSource(
    private val sensorManager: SensorManager?,
    private val config: Flow<SensorConfig> = flowOf(SensorConfig()),
    private val displayRotation: () -> Int = { Surface.ROTATION_0 },
) : OrientationSource {
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    override val available: Boolean
        get() = rotationSensor != null || (accelerometer != null && magnetometer != null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun orientations(): Flow<Matrix3> =
        config.distinctUntilChanged { old, new ->
            when {
                old.disableGyro != new.disableGyro -> false
                rotationSensor != null && !old.disableGyro ->
                    // The settings screen disables speed/damping/reverseMagneticZ while the
                    // gyro path is active, so they can't have changed underneath us here.
                    true
                else ->
                    old.speed == new.speed &&
                        old.damping == new.damping &&
                        old.reverseMagneticZ == new.reverseMagneticZ
            }
        }.flatMapLatest { current ->
            when {
                sensorManager == null -> emptyFlow()
                rotationSensor != null && !current.disableGyro ->
                    rotationVectorOrientations(sensorManager, rotationSensor)
                accelerometer != null && magnetometer != null ->
                    legacyOrientations(sensorManager, accelerometer, magnetometer, current)
                else -> emptyFlow()
            }
        }

    private fun rotationVectorOrientations(
        manager: SensorManager,
        sensor: Sensor,
    ): Flow<Matrix3> =
        callbackFlow {
            // Some devices (e.g. Galaxy S4) report more than four values; Android only needs four.
            // Others report only three (no scalar component): passing a four-element array with a
            // zeroed slot 3 makes getRotationMatrixFromVector treat q0 as 0 instead of deriving it,
            // so keep a three-element buffer too and pass whichever matches the sample's arity.
            val truncated3 = FloatArray(3)
            val truncated4 = FloatArray(4)
            val rotationMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val vector =
                            if (event.values.size >= 4) {
                                System.arraycopy(event.values, 0, truncated4, 0, 4)
                                truncated4
                            } else {
                                System.arraycopy(
                                    event.values,
                                    0,
                                    truncated3,
                                    0,
                                    minOf(event.values.size, 3),
                                )
                                truncated3
                            }
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, vector)
                        val remapped = remapToDisplayFrame(rotationMatrix, remappedMatrix)
                        trySend(remapped.toMatrix3())
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) = Unit
                }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { manager.unregisterListener(listener) }
        }.conflate()

    private fun legacyOrientations(
        manager: SensorManager,
        accelerometer: Sensor,
        magnetometer: Sensor,
        config: SensorConfig,
    ): Flow<Matrix3> =
        callbackFlow {
            val (accDamping, magDamping) = dampingSettingsFor(config.damping)
            val accelerationSmoother =
                ExponentiallyWeightedSmoother(accDamping.alpha, accDamping.exponent)
            val magneticSmoother =
                ExponentiallyWeightedSmoother(magDamping.alpha, magDamping.exponent)
            val delay = sensorDelayFor(config.speed)
            var acceleration: Vector3? = null
            var magneticField: Vector3? = null
            val naturalMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        when (event.sensor.type) {
                            Sensor.TYPE_ACCELEROMETER ->
                                acceleration = accelerationSmoother.update(event.values)
                            Sensor.TYPE_MAGNETIC_FIELD -> {
                                val smoothed = magneticSmoother.update(event.values)
                                // v1 PlainSmootherModelAdaptor: the mis-mounted-magnetometer
                                // workaround negates Z (smoothing is linear, so the order is
                                // immaterial).
                                magneticField =
                                    if (config.reverseMagneticZ) {
                                        Vector3(smoothed.x, smoothed.y, -smoothed.z)
                                    } else {
                                        smoothed
                                    }
                            }
                        }
                        val accel = acceleration ?: return
                        val mag = magneticField ?: return
                        orientationFromSensors(accel, mag)?.let { matrix ->
                            // ROTATION_0 needs no remap; skip the FloatArray round-trip (two
                            // allocations per event) on the hot path in that common case.
                            val remapped =
                                if (displayRotation() == Surface.ROTATION_0) {
                                    matrix
                                } else {
                                    matrix.writeToFloatArray(naturalMatrix)
                                    remapToDisplayFrame(
                                        naturalMatrix,
                                        remappedMatrix,
                                    ).toMatrix3()
                                }
                            trySend(remapped)
                        }
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) = Unit
                }
            manager.registerListener(listener, accelerometer, delay)
            manager.registerListener(listener, magnetometer, delay)
            awaitClose { manager.unregisterListener(listener) }
        }.conflate()

    /**
     * Rotates a natural-frame rotation matrix into the current display frame. The axis pairs
     * are the standard [SensorManager.remapCoordinateSystem] mapping for each display rotation.
     */
    private fun remapToDisplayFrame(
        natural: FloatArray,
        out: FloatArray,
    ): FloatArray {
        val (axisX, axisY) =
            when (displayRotation()) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> return natural
            }
        SensorManager.remapCoordinateSystem(natural, axisX, axisY, out)
        return out
    }

    private fun Matrix3.writeToFloatArray(out: FloatArray) {
        out[0] = xx.toFloat()
        out[1] = xy.toFloat()
        out[2] = xz.toFloat()
        out[3] = yx.toFloat()
        out[4] = yy.toFloat()
        out[5] = yz.toFloat()
        out[6] = zx.toFloat()
        out[7] = zy.toFloat()
        out[8] = zz.toFloat()
    }

    private fun FloatArray.toMatrix3() =
        Matrix3(
            this[0].toDouble(), this[1].toDouble(), this[2].toDouble(),
            this[3].toDouble(), this[4].toDouble(), this[5].toDouble(),
            this[6].toDouble(), this[7].toDouble(), this[8].toDouble(),
        )

    /** One smoother's parameters — v1 `SensorOrientationController.SensorDampingSettings`. */
    internal data class DampingSettings(val alpha: Float, val exponent: Int)

    companion object {
        /**
         * v1's `ACC_DAMPING_SETTINGS`/`MAG_DAMPING_SETTINGS` tables, indexed by the same
         * ladder (standard → really high).
         */
        internal fun dampingSettingsFor(
            damping: SensorDamping,
        ): Pair<DampingSettings, DampingSettings> =
            when (damping) {
                SensorDamping.STANDARD ->
                    DampingSettings(0.7f, 3) to DampingSettings(0.05f, 3)
                SensorDamping.HIGH ->
                    DampingSettings(0.7f, 3) to DampingSettings(0.001f, 4)
                SensorDamping.EXTRA_HIGH ->
                    DampingSettings(0.1f, 3) to DampingSettings(0.0001f, 5)
                SensorDamping.REALLY_HIGH ->
                    DampingSettings(0.1f, 3) to DampingSettings(0.000001f, 5)
            }

        /** v1's speed mapping: standard = GAME, slow = NORMAL, fast = FASTEST. */
        internal fun sensorDelayFor(speed: SensorSpeed): Int =
            when (speed) {
                SensorSpeed.SLOW -> SensorManager.SENSOR_DELAY_NORMAL
                SensorSpeed.STANDARD -> SensorManager.SENSOR_DELAY_GAME
                SensorSpeed.FAST -> SensorManager.SENSOR_DELAY_FASTEST
            }
    }
}
