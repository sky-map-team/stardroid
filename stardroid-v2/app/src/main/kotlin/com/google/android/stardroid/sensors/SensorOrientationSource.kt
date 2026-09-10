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
import com.google.android.stardroid.settings.RotationSmoothingLevel
import com.google.android.stardroid.settings.SensorDamping
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
 * without one fall back to v1's classic accelerometer+magnetometer path: the two raw streams
 * fused by the pure `orientationFromSensors` vector-rejection construction, and the resulting
 * orientation smoothed by a [QuaternionSlerpSmoother] at the configured damping — the same
 * smoother the fused path uses, applied post-fusion (issue #1007). v1 instead smoothed each
 * raw stream per-axis before fusing. A [config] change re-registers the listeners under the
 * new parameters — v1 applied its preferences on controller restart.
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
                    // The settings screen hides speed/damping/reverseMagneticZ while the fused
                    // path is active (they don't apply to it); rotationLowPass/rotationDeadband
                    // are the settings shown here, so they're the only ones that can change.
                    old.rotationLowPass == new.rotationLowPass &&
                        old.rotationDeadband == new.rotationDeadband
                else ->
                    old.damping == new.damping &&
                        old.reverseMagneticZ == new.reverseMagneticZ
            }
        }.flatMapLatest { current ->
            when {
                sensorManager == null -> emptyFlow()
                rotationSensor != null && !current.disableGyro ->
                    rotationVectorOrientations(
                        sensorManager,
                        rotationSensor,
                        current.rotationLowPass,
                        current.rotationDeadband,
                    )
                accelerometer != null && magnetometer != null ->
                    legacyOrientations(sensorManager, accelerometer, magnetometer, current)
                else -> emptyFlow()
            }
        }

    private fun rotationVectorOrientations(
        manager: SensorManager,
        sensor: Sensor,
        rotationLowPass: RotationSmoothingLevel,
        rotationDeadband: RotationSmoothingLevel,
    ): Flow<Matrix3> =
        callbackFlow {
            // Some devices (e.g. Galaxy S4) report more than four values; Android only needs four.
            // Others report only three (no scalar component): passing a four-element array with a
            // zeroed slot 3 makes getRotationMatrixFromVector treat q0 as 0 instead of deriving it,
            // so keep a three-element buffer too and pass whichever matches the sample's arity.
            val truncated3 = FloatArray(3)
            val truncated4 = FloatArray(4)
            val quaternion = FloatArray(4)
            val rotationMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            // Both off by default (see SensorConfig): only allocate/run the smoother when at
            // least one is actually selected, so devices that leave both off pay no cost at all.
            val smoother =
                if (rotationLowPass == RotationSmoothingLevel.OFF &&
                    rotationDeadband == RotationSmoothingLevel.OFF
                ) {
                    null
                } else {
                    QuaternionSlerpSmoother(
                        alpha = QuaternionSlerpSmoother.alphaFor(rotationLowPass),
                        deadbandRadians =
                            QuaternionSlerpSmoother.deadbandRadiansFor(
                                rotationDeadband,
                            ),
                    )
                }
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val vector =
                            if (smoother != null) {
                                smoother.update(toQuaternion(event.values, quaternion))
                            } else if (event.values.size >= 4) {
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

    /** Fills [out] with the sample's quaternion `(x, y, z, w)`, deriving `w` if absent. */
    private fun toQuaternion(
        values: FloatArray,
        out: FloatArray,
    ): FloatArray {
        out[0] = values[0]
        out[1] = values[1]
        out[2] = values[2]
        out[3] =
            if (values.size >= 4) {
                values[3]
            } else {
                val sumSquares = out[0] * out[0] + out[1] * out[1] + out[2] * out[2]
                kotlin.math.sqrt(kotlin.math.max(0f, 1f - sumSquares))
            }
        return out
    }

    private fun legacyOrientations(
        manager: SensorManager,
        accelerometer: Sensor,
        magnetometer: Sensor,
        config: SensorConfig,
    ): Flow<Matrix3> =
        callbackFlow {
            val damping = dampingSettingsFor(config.damping)
            // No deadband: the exponent law is itself a soft one, and unlike the fused path
            // (where deadband is a separate user setting) the legacy ladder has never had a
            // hard cut-off to preserve.
            val smoother =
                QuaternionSlerpSmoother(
                    alpha = damping.alpha,
                    deadbandRadians = 0f,
                    exponent = damping.exponent,
                )
            var acceleration: Vector3? = null
            var magneticField: Vector3? = null
            val quaternion = FloatArray(4)
            val naturalMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        when (event.sensor.type) {
                            Sensor.TYPE_ACCELEROMETER -> acceleration = event.values.toVector3()
                            Sensor.TYPE_MAGNETIC_FIELD ->
                                // v1 PlainSmootherModelAdaptor: the mis-mounted-magnetometer
                                // workaround negates Z.
                                magneticField =
                                    event.values.toVector3(negateZ = config.reverseMagneticZ)
                        }
                        val accel = acceleration ?: return
                        val mag = magneticField ?: return
                        val fused = orientationFromSensors(accel, mag) ?: return
                        // Smoothing happens here, on the fused orientation in the natural
                        // frame, rather than on the two raw vectors feeding
                        // orientationFromSensors. v1's per-axis smoothers ran the
                        // accelerometer far more responsively than the magnetometer, so during
                        // motion "up" tracked while "north" lagged and the constructed frame
                        // was transiently inconsistent in a way neither the raw nor the settled
                        // data is; one smoother on the result gives the whole frame a single
                        // uniform lag. Smoothing before the display remap also keeps a screen
                        // rotation from being smoothed through as though it were movement.
                        val smoothed =
                            smoother.update(fused.writeQuaternion(quaternion))
                                .toRotationMatrix3()
                        // ROTATION_0 needs no remap; skip the FloatArray round-trip (two
                        // allocations per event) on the hot path in that common case.
                        val remapped =
                            if (displayRotation() == Surface.ROTATION_0) {
                                smoothed
                            } else {
                                smoothed.writeToFloatArray(naturalMatrix)
                                remapToDisplayFrame(naturalMatrix, remappedMatrix).toMatrix3()
                            }
                        trySend(remapped)
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) = Unit
                }
            // Both paths sample at the same fixed rate. v1 let the user pick, but the
            // smoothing fraction below is applied per sample, so rate and damping interact:
            // exposing both would mean twelve combinations of which only one is ever tuned.
            // SENSOR_DELAY_FASTEST also meant a 0-microsecond request, which Android 12 and up
            // reject outright unless the app declares HIGH_SAMPLING_RATE_SENSORS (issue #1007).
            manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            manager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { manager.unregisterListener(listener) }
        }.conflate()

    /** One raw 3-axis sample as a [Vector3], optionally with Z negated. */
    private fun FloatArray.toVector3(negateZ: Boolean = false) =
        Vector3(
            this[0].toDouble(),
            this[1].toDouble(),
            if (negateZ) -this[2].toDouble() else this[2].toDouble(),
        )

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

    /** One smoother's parameters: SLERP fraction `alpha · angle^(exponent - 1)`, clamped to 1. */
    internal data class DampingSettings(val alpha: Float, val exponent: Int)

    companion object {
        /**
         * The legacy path's damping ladder, re-expressed for [QuaternionSlerpSmoother].
         *
         * v1's `ACC_DAMPING_SETTINGS`/`MAG_DAMPING_SETTINGS` constants can't be carried over
         * numerically — they acted on raw sensor units (m/s², µT) where these act on radians of
         * rotation — but the character they gave each rung is preserved: crush sub-degree
         * jitter, pass real movement through almost unattenuated. At half a degree per sample
         * the fractions here run 0.0009 / 0.0005 / 0.0002 / 0.00006; by ten degrees they are
         * 0.37 / 0.18 / 0.09 / 0.02.
         *
         * Every rung shares the same exponent, so the ladder is strictly monotonic at every
         * angle — a mixed-exponent ladder has rungs that overtake each other on fast movement,
         * which is confusing to tune against.
         *
         * Field-tuned on a Pixel 9 Pro over two passes (issue #1007), each of which picked the
         * heaviest rung on offer, so each shifted the whole ladder down: "About right" now
         * carries what the first pass called "Laggy" twice over. "Laggy" is deliberately set
         * heavier than anything yet judged, to bracket the top end rather than keep chasing
         * it. Still one device's judgement, and worth re-checking on a genuinely gyro-less
         * one.
         */
        internal fun dampingSettingsFor(damping: SensorDamping): DampingSettings =
            when (damping) {
                SensorDamping.STANDARD -> DampingSettings(12f, 3)
                SensorDamping.HIGH -> DampingSettings(6f, 3)
                SensorDamping.EXTRA_HIGH -> DampingSettings(3f, 3)
                SensorDamping.REALLY_HIGH -> DampingSettings(0.8f, 3)
            }
    }
}
