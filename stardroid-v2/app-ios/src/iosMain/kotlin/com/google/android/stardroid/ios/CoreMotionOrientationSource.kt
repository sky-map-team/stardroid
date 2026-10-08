/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.sensors.OneEuroQuaternionSmoother
import com.google.android.stardroid.sensors.OrientationSample
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.SensorConfig
import com.google.android.stardroid.sensors.toRotationMatrix3
import com.google.android.stardroid.sensors.writeQuaternion
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import platform.CoreMotion.CMAttitudeReferenceFrame
import platform.CoreMotion.CMAttitudeReferenceFrameXMagneticNorthZVertical
import platform.CoreMotion.CMAttitudeReferenceFrameXTrueNorthZVertical
import platform.CoreMotion.CMMotionManager
import platform.CoreMotion.CMRotationMatrix
import platform.Foundation.NSOperationQueue

/**
 * [OrientationSource] over Core Motion's fused device motion: the iOS counterpart of Android's
 * `SensorOrientationSource` rotation-vector path, emitting the same matrix (rows are world East,
 * North and Up in phone coordinates; both platforms put the phone's X right, Y up the screen and
 * Z out of it), through the same 1€ smoother under the same settings.
 *
 * Core Motion reports attitude against a reference frame whose X is north, Y west and Z up, and
 * its rotation matrix takes that frame's coordinates to the phone's, so the frame's axes in phone
 * coordinates are the matrix's columns. Android's rows are those columns, reordered, with west
 * negated into east.
 *
 * The frame is true north whenever the app may use location, which Core Motion needs to correct
 * the compass, and the user hasn't turned the correction off; the graph pairs this source with
 * zero declination for that reason. Otherwise the frame is magnetic north, uncorrected, until a
 * shared geomagnetic model exists.
 * Android instead computes the declination itself, at the map's location, which is the wrong
 * place for the compass when that location was entered by hand.
 *
 * One [CMMotionManager] serves every collector (Apple asks for one per app). Updates run only
 * while [active], the way Android gates its listeners to the process's STARTED state.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)
class CoreMotionOrientationSource(
    private val config: Flow<SensorConfig>,
    trueNorthAllowed: Flow<Boolean>,
    active: Flow<Boolean>,
) : OrientationSource {
    private val motionManager =
        CMMotionManager().apply {
            deviceMotionUpdateInterval = UPDATE_INTERVAL_SECONDS
            // Lets iOS show its own calibration prompt when the compass needs a figure-eight.
            showsDeviceMovementDisplay = true
        }

    // Off the main thread, where a busy frame would otherwise queue samples up behind it; the
    // conflated channel hands the main thread only the newest.
    private val motionQueue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }

    override val available: Boolean
        get() = motionManager.deviceMotionAvailable

    private val attitudes: Flow<Attitude> =
        combine(active, trueNorthAllowed) { isActive, trueNorth ->
            if (isActive) referenceFrame(trueNorth) else null
        }.distinctUntilChanged()
            .flatMapLatest { frame -> if (frame == null) emptyFlow() else deviceMotion(frame) }
            .shareIn(MainScope(), SharingStarted.WhileSubscribed())

    override fun orientations(): Flow<Matrix3> = orientationSamples().map { it.smoothed }

    // Android keeps this separate from [orientations] to spare the map the raw matrix's cost.
    // Here the raw matrix is computed either way, so the map's stream is just this one's half.
    override fun orientationSamples(): Flow<OrientationSample> =
        config
            .distinctUntilChanged { old, new ->
                // The gyro and magnetic-Z switches only steer Android's legacy path.
                old.smoothingEnabled == new.smoothingEnabled &&
                    old.steadiness == new.steadiness &&
                    old.easeOff == new.easeOff
            }.flatMapLatest { current ->
                flow {
                    val smoother = smootherFor(current)
                    val quaternion = FloatArray(4)
                    attitudes.collect { attitude ->
                        val raw = attitude.matrix
                        val smoothed =
                            smoother
                                ?.update(raw.writeQuaternion(quaternion), attitude.nanos)
                                ?.toRotationMatrix3()
                                ?: raw
                        emit(OrientationSample(raw, smoothed))
                    }
                }
            }.conflate()

    private fun referenceFrame(trueNorth: Boolean): CMAttitudeReferenceFrame {
        val frames = CMMotionManager.availableAttitudeReferenceFrames()
        val trueNorthAvailable = frames and CMAttitudeReferenceFrameXTrueNorthZVertical != 0uL
        return if (trueNorth && trueNorthAvailable) {
            CMAttitudeReferenceFrameXTrueNorthZVertical
        } else {
            CMAttitudeReferenceFrameXMagneticNorthZVertical
        }
    }

    private fun deviceMotion(frame: CMAttitudeReferenceFrame): Flow<Attitude> =
        callbackFlow {
            motionManager.startDeviceMotionUpdatesUsingReferenceFrame(
                frame,
                motionQueue,
            ) { motion, _ ->
                if (motion != null) {
                    val matrix = motion.attitude.rotationMatrix.useContents { toPhoneToWorld() }
                    trySend(Attitude(matrix, (motion.timestamp * NANOS_PER_SECOND).toLong()))
                }
            }
            awaitClose { motionManager.stopDeviceMotionUpdates() }
        }.conflate()

    /** One attitude sample: Android's phone→world matrix and Core Motion's timestamp. */
    private class Attitude(val matrix: Matrix3, val nanos: Long)

    private companion object {
        /**
         * 100 Hz, so every 60 Hz frame (and most 120 Hz ones) gets a fresh sample; the conflated
         * flow drops the rest. Core Motion rounds the interval *up* to a whole number of its 10 ms
         * ticks, measured on an iPhone 13: asking for 1/60 s delivered every 30 ms, at 33 Hz.
         */
        const val UPDATE_INTERVAL_SECONDS = 1.0 / 100

        const val NANOS_PER_SECOND = 1e9

        /** Rows East, North, Up: the reference frame's −Y, X and Z columns. */
        fun CMRotationMatrix.toPhoneToWorld() =
            Matrix3(
                -m12, -m22, -m32,
                m11, m21, m31,
                m13, m23, m33,
            )

        /**
         * Android's fused-path tuning, as Core Motion's attitude is gyro-fused like a rotation
         * vector; `null` when smoothing is off.
         */
        fun smootherFor(config: SensorConfig) =
            if (!config.smoothingEnabled) {
                null
            } else {
                OneEuroQuaternionSmoother(
                    minCutoff =
                        OneEuroQuaternionSmoother.minCutoffFor(
                            config.steadiness,
                            legacyPath = false,
                        ),
                    beta =
                        OneEuroQuaternionSmoother.betaFor(
                            config.easeOff,
                            legacyPath = false,
                        ),
                    speedFloor = OneEuroQuaternionSmoother.speedFloorFor(legacyPath = false),
                )
            }
    }
}
