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
import com.google.android.stardroid.math.Matrix3
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class SensorOrientationSourceTest {
    private val accelerometer = sensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val manager = mock(SensorManager::class.java)
    private val listeners = mutableListOf<SensorEventListener>()

    init {
        `when`(manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)).thenReturn(accelerometer)
        `when`(manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)).thenReturn(magnetometer)
        `when`(manager.registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), anyInt()))
            .thenAnswer {
                listeners += it.getArgument<SensorEventListener>(0)
                true
            }
    }

    private fun sensor(type: Int) = mock(Sensor::class.java).also { `when`(it.type).thenReturn(type) }

    // SensorEvent has no public constructor; values/sensor/timestamp are public final fields.
    private fun event(
        sensor: Sensor,
        timestampNanos: Long,
        vararg values: Float,
    ): SensorEvent {
        val event = mock(SensorEvent::class.java)
        fun set(
            name: String,
            value: Any,
        ) = SensorEvent::class.java.getField(name).apply { isAccessible = true }.set(event, value)
        set("sensor", sensor)
        set("timestamp", timestampNanos)
        set("values", values)
        return event
    }

    private fun SensorEventListener.accel(
        timestampNanos: Long,
        vararg values: Float,
    ) = onSensorChanged(event(accelerometer, timestampNanos, *values))

    private fun SensorEventListener.mag(
        timestampNanos: Long,
        vararg values: Float,
    ) = onSensorChanged(event(magnetometer, timestampNanos, *values))

    private fun source(config: SensorConfig) =
        SensorOrientationSource(manager, flowOf(config))

    private fun kotlinx.coroutines.test.TestScope.collect(
        source: SensorOrientationSource,
        out: MutableList<Matrix3>,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.orientations().collect { out += it }
        }
    }

    @Test
    fun `legacy path emits only on accelerometer events`() =
        runTest {
            val out = mutableListOf<Matrix3>()
            val source = source(SensorConfig())
            collect(source, out)
            val listener = listeners.first()

            listener.mag(1_000_000L, 20f, 0f, -40f)
            assertThat(out).isEmpty()
            listener.accel(2_000_000L, 0f, 0f, 9.8f)
            assertThat(out).hasSize(1)
            listener.mag(3_000_000L, 20f, 0f, -40f)
            assertThat(out).hasSize(1)
            listener.accel(4_000_000L, 0f, 0f, 9.8f)
            assertThat(out).hasSize(2)
            assertThat(source.rejectedSampleCount).isEqualTo(0)
        }

    @Test
    fun `magnetometer timestamps never reach the smoother`() =
        runTest {
            val config = SensorConfig(smoothingEnabled = true)
            val clean = mutableListOf<Matrix3>()
            val interleaved = mutableListOf<Matrix3>()
            val frames = listOf(0f, 0.2f, 0.4f, 0.6f)

            collect(source(config), clean)
            val cleanListener = listeners.last()
            cleanListener.mag(0L, 20f, 0f, -40f)
            frames.forEachIndexed { i, tilt ->
                cleanListener.accel((i + 1) * 20_000_000L, tilt, 0f, 9.8f)
            }

            collect(source(config), interleaved)
            val interleavedListener = listeners.last()
            interleavedListener.mag(0L, 20f, 0f, -40f)
            frames.forEachIndexed { i, tilt ->
                // Magnetometer clock stepping backwards against the accelerometer's.
                interleavedListener.mag(5_000_000_000L - i * 1_000_000L, 20f, 0f, -40f)
                interleavedListener.accel((i + 1) * 20_000_000L, tilt, 0f, 9.8f)
            }

            assertThat(interleaved).isEqualTo(clean)
        }

    @Test
    fun `without a rotation vector sensor disableGyro does not change the tables`() =
        runTest {
            val off = mutableListOf<Matrix3>()
            val on = mutableListOf<Matrix3>()
            val frames = listOf(0f, 1f, 2f, 3f)

            collect(source(SensorConfig(smoothingEnabled = true, disableGyro = false)), off)
            val offListener = listeners.last()
            collect(source(SensorConfig(smoothingEnabled = true, disableGyro = true)), on)
            val onListener = listeners.last()
            listOf(offListener, onListener).forEach { it.mag(0L, 20f, 0f, -40f) }
            frames.forEachIndexed { i, tilt ->
                listOf(offListener, onListener).forEach {
                    it.accel((i + 1) * 20_000_000L, tilt, 0f, 9.8f)
                }
            }

            // Reference: the same fused orientations through a smoother on the legacy tables.
            val config = SensorConfig(smoothingEnabled = true)
            val reference =
                OneEuroQuaternionSmoother(
                    minCutoff = OneEuroQuaternionSmoother.minCutoffFor(config.steadiness, true),
                    beta = OneEuroQuaternionSmoother.betaFor(config.easeOff, true),
                    speedFloor = OneEuroQuaternionSmoother.speedFloorFor(true),
                )
            val quaternion = FloatArray(4)
            val expected =
                frames.mapIndexed { i, tilt ->
                    accelMagOrientation(
                        com.google.android.stardroid.math.Vector3(tilt.toDouble(), 0.0, 9.8f.toDouble()),
                        com.google.android.stardroid.math.Vector3(20.0, 0.0, -40.0),
                        quaternion,
                    )
                    reference.update(quaternion, (i + 1) * 20_000_000L).toRotationMatrix3()
                }

            assertThat(on).isEqualTo(off)
            assertThat(on).isEqualTo(expected)
        }
}
