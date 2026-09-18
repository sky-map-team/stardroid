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
 * fused by the pure `orientationFromSensors` vector-rejection construction. Either way the
 * resulting orientation goes through one [OneEuroQuaternionSmoother] under the same parameters
 * (issue #1007) — v1 instead smoothed each raw stream per-axis before fusing, and only on the
 * legacy path. A [config] change re-registers the listeners under the new parameters — v1
 * applied its preferences on controller restart.
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
        configChanges().flatMapLatest { current ->
            when {
                sensorManager == null -> emptyFlow()
                rotationSensor != null && !current.disableGyro ->
                    rotationVectorOrientations(sensorManager, rotationSensor, current)
                accelerometer != null && magnetometer != null ->
                    legacyOrientations(sensorManager, accelerometer, magnetometer, current)
                else -> emptyFlow()
            }
        }

    /**
     * Same routing as [orientations], but each event computes both the raw and smoothed matrix
     * (see [rotationVectorOrientationSamples]/[legacyOrientationSamples]) — kept as a separate
     * implementation, not built on top of [orientations], so a collector that only wants the
     * smoothed stream (the map, on every user's hot sensor path) never pays for the extra work.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun orientationSamples(): Flow<OrientationSample> =
        configChanges().flatMapLatest { current ->
            when {
                sensorManager == null -> emptyFlow()
                rotationSensor != null && !current.disableGyro ->
                    rotationVectorOrientationSamples(sensorManager, rotationSensor, current)
                accelerometer != null && magnetometer != null ->
                    legacyOrientationSamples(sensorManager, accelerometer, magnetometer, current)
                else -> emptyFlow()
            }
        }

    private fun configChanges(): Flow<SensorConfig> =
        config.distinctUntilChanged { old, new ->
            // The smoothing parameters now drive both paths, so the only setting that doesn't
            // always matter is the magnetic-Z reversal, which the fused path never reads.
            old.disableGyro == new.disableGyro &&
                old.smoothingEnabled == new.smoothingEnabled &&
                old.steadiness == new.steadiness &&
                old.easeOff == new.easeOff &&
                (usesFusedPath(old) || old.reverseMagneticZ == new.reverseMagneticZ)
        }

    /** Whether [config] selects the fused rotation-vector path over the legacy one. */
    private fun usesFusedPath(config: SensorConfig) =
        rotationSensor != null && !config.disableGyro

    private fun rotationVectorOrientations(
        manager: SensorManager,
        sensor: Sensor,
        config: SensorConfig,
    ): Flow<Matrix3> =
        callbackFlow {
            // Some devices (e.g. Galaxy S4) report more than four values, others only three
            // (no scalar component); toQuaternion normalizes both into this buffer, deriving
            // the scalar where it's missing.
            val quaternion = FloatArray(4)
            val rotationMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val smoother = smootherFor(config)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val raw = toQuaternion(event.values, quaternion)
                        val vector = smoother?.update(raw, event.timestamp) ?: raw
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

    /** [rotationVectorOrientations], but emitting the pre-smoothing matrix alongside it. */
    private fun rotationVectorOrientationSamples(
        manager: SensorManager,
        sensor: Sensor,
        config: SensorConfig,
    ): Flow<OrientationSample> =
        callbackFlow {
            val quaternion = FloatArray(4)
            val rawMatrix = FloatArray(9)
            val rawRemapped = FloatArray(9)
            val smoothedMatrix = FloatArray(9)
            val smoothedRemapped = FloatArray(9)
            val smoother = smootherFor(config)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val raw = toQuaternion(event.values, quaternion)
                        SensorManager.getRotationMatrixFromVector(rawMatrix, raw)
                        val rawResult = remapToDisplayFrame(rawMatrix, rawRemapped).toMatrix3()
                        val smoothed = smoother?.update(raw, event.timestamp)
                        val smoothedResult =
                            if (smoothed == null) {
                                rawResult
                            } else {
                                SensorManager.getRotationMatrixFromVector(smoothedMatrix, smoothed)
                                remapToDisplayFrame(smoothedMatrix, smoothedRemapped).toMatrix3()
                            }
                        trySend(OrientationSample(rawResult, smoothedResult))
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
            val smoother = smootherFor(config)
            var acceleration: Vector3? = null
            var magneticField: Vector3? = null
            val quaternion = FloatArray(4)
            val naturalMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                            // v1 PlainSmootherModelAdaptor: the mis-mounted-magnetometer
                            // workaround negates Z.
                            magneticField =
                                event.values.toVector3(negateZ = config.reverseMagneticZ)
                            // Cached, not acted on. Emitting an orientation from each stream
                            // would interleave two sensors' hardware timestamps, which are
                            // independent and step backwards against each other — and the
                            // smoother reads elapsed time between samples. Driving everything
                            // from the accelerometer keeps that clock monotonic and evenly
                            // spaced, and stops each frame being computed twice, once from a
                            // stale accelerometer and once from a stale magnetometer.
                            return
                        }
                        acceleration = event.values.toVector3()
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
                            smoother
                                ?.update(fused.writeQuaternion(quaternion), event.timestamp)
                                ?.toRotationMatrix3()
                                ?: fused
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

    /** [legacyOrientations], but emitting the pre-smoothing fused matrix alongside it. */
    private fun legacyOrientationSamples(
        manager: SensorManager,
        accelerometer: Sensor,
        magnetometer: Sensor,
        config: SensorConfig,
    ): Flow<OrientationSample> =
        callbackFlow {
            val smoother = smootherFor(config)
            var acceleration: Vector3? = null
            var magneticField: Vector3? = null
            val quaternion = FloatArray(4)
            val naturalMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                            magneticField =
                                event.values.toVector3(negateZ = config.reverseMagneticZ)
                            return
                        }
                        acceleration = event.values.toVector3()
                        val accel = acceleration ?: return
                        val mag = magneticField ?: return
                        val fused = orientationFromSensors(accel, mag) ?: return
                        val smoothed =
                            smoother
                                ?.update(fused.writeQuaternion(quaternion), event.timestamp)
                                ?.toRotationMatrix3()
                                ?: fused
                        val remappedFused =
                            if (displayRotation() == Surface.ROTATION_0) {
                                fused
                            } else {
                                fused.writeToFloatArray(naturalMatrix)
                                remapToDisplayFrame(naturalMatrix, remappedMatrix).toMatrix3()
                            }
                        val remappedSmoothed =
                            if (smoothed === fused) {
                                remappedFused
                            } else if (displayRotation() == Surface.ROTATION_0) {
                                smoothed
                            } else {
                                smoothed.writeToFloatArray(naturalMatrix)
                                remapToDisplayFrame(naturalMatrix, remappedMatrix).toMatrix3()
                            }
                        trySend(OrientationSample(remappedFused, remappedSmoothed))
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) = Unit
                }
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

    /**
     * One [OneEuroQuaternionSmoother] under [config]'s parameters, for either path, or `null`
     * when smoothing is off — in which case the sensor's orientation reaches the view untouched
     * and no filter is allocated or run at all.
     */
    private fun smootherFor(config: SensorConfig) =
        if (!config.smoothingEnabled) {
            null
        } else {
            val legacyPath = !usesFusedPath(config)
            OneEuroQuaternionSmoother(
                minCutoff = OneEuroQuaternionSmoother.minCutoffFor(config.steadiness, legacyPath),
                beta = OneEuroQuaternionSmoother.betaFor(config.easeOff, legacyPath),
                speedFloor = OneEuroQuaternionSmoother.speedFloorFor(legacyPath),
            )
        }

}
