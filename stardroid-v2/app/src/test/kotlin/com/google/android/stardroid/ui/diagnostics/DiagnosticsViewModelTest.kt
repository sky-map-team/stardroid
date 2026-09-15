/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import com.google.android.stardroid.location.LocationSource
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.sensors.FakeSensorStatusSource
import com.google.android.stardroid.sensors.MagneticDeclinationSource
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorReading
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val settings = FakeSettings()
    private val sensors = FakeSensorStatusSource(present = setOf(SensorKind.MAGNETOMETER))
    private val locationStates = MutableStateFlow<LocationState>(LocationState.Unset)
    private val camera =
        MutableStateFlow(SkyCamera(Vector3(0.0, 1.0, 0.0), Vector3.UNIT_Z, 45.0))
    private val time = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private var permissionGranted = true
    private var gps = GpsStatus.ENABLED
    private var network = NetworkStatus.CONNECTED_WIFI

    private val viewModel by lazy {
        DiagnosticsViewModel(
            sensorStatus = sensors,
            locationStates = locationStates,
            camera = camera,
            settings = settings,
            declinationSource =
                object : MagneticDeclinationSource {
                    override fun declinationDeg(
                        location: LatLong,
                        time: Instant,
                    ): Double = 5.0
                },
            now = { time },
            isLocationPermissionGranted = { permissionGranted },
            gpsStatus = { gps },
            networkStatus = { network },
            ioContext = dispatcher,
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `missing sensors report absent, present ones stream readings`() =
        testScope.runTest {
            val magnetometer = viewModel.sensors.getValue(SensorKind.MAGNETOMETER)
            val gyroscope = viewModel.sensors.getValue(SensorKind.GYROSCOPE)
            val collector = launch { magnetometer.collect {} }
            runCurrent()

            assertThat(gyroscope.value).isEqualTo(SensorRow.Absent)
            assertThat(magnetometer.value).isEqualTo(SensorRow.Present(reading = null))

            val reading = SensorReading(SensorAccuracy.HIGH, listOf(10f, -4f, 33f))
            sensors.emit(SensorKind.MAGNETOMETER, reading)
            // Sensor rows are sampled at UPDATE_PERIOD_MILLIS to avoid recomposing on every
            // raw sensor sample.
            advanceTimeBy(DiagnosticsViewModel.UPDATE_PERIOD_MILLIS + 1)
            runCurrent()
            assertThat(magnetometer.value).isEqualTo(SensorRow.Present(reading))
            collector.cancel()
        }

    @Test
    fun `snapshot samples permission gps network pointing and times`() =
        testScope.runTest {
            val collector = launch { viewModel.snapshots.collect {} }
            runCurrent()

            val snapshot = viewModel.snapshots.value
            assertThat(snapshot.locationPermissionGranted).isTrue()
            assertThat(snapshot.gpsStatus).isEqualTo(GpsStatus.ENABLED)
            assertThat(snapshot.network).isEqualTo(NetworkStatus.CONNECTED_WIFI)
            assertThat(snapshot.locationState).isEqualTo(LocationState.Unset)
            assertThat(snapshot.time).isEqualTo(time)
            // Camera looks along +y: RA 90°, dec 0°.
            assertThat(snapshot.pointing!!.raDeg).isWithin(1e-9).of(90.0)
            assertThat(snapshot.pointing!!.decDeg).isWithin(1e-9).of(0.0)
            collector.cancel()
        }

    @Test
    fun `magnetic correction is declination plus the alignment azimuth when enabled`() =
        testScope.runTest {
            locationStates.value =
                LocationState.Confirmed(
                    LatLong(50.0, 0.0),
                    LocationSource.AUTO,
                    accuracyM = null,
                    timestampMillis = 0L,
                )
            settings.sensorAzimuthAdjustmentState.value = 2.5
            settings.sensorAltitudeAdjustmentState.value = -1.5
            val collector = launch { viewModel.snapshots.collect {} }
            runCurrent()
            assertThat(viewModel.snapshots.value.magneticCorrectionDeg).isEqualTo(7.5)
            // The raw drag-to-align pair surfaces read-only (D64).
            assertThat(viewModel.snapshots.value.alignmentAzimuthDeg).isEqualTo(2.5)
            assertThat(viewModel.snapshots.value.alignmentAltitudeDeg).isEqualTo(-1.5)

            // Correction off: only the alignment offset remains (matching the map's camera).
            settings.useMagneticCorrectionState.value = false
            advanceTimeBy(DiagnosticsViewModel.UPDATE_PERIOD_MILLIS + 1)
            runCurrent()
            assertThat(viewModel.snapshots.value.magneticCorrectionDeg).isEqualTo(2.5)
            collector.cancel()
        }

    @Test
    fun `rotation matrix expands the quaternion - identity for the zero rotation`() {
        val identity =
            DiagnosticsViewModel.rotationMatrixFromVector(listOf(0f, 0f, 0f, 1f))
        assertThat(identity)
            .containsExactly(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            .inOrder()

        // 90° about z: x maps to y.
        val halfSqrt2 = kotlin.math.sqrt(2f) / 2f
        val quarterTurn =
            DiagnosticsViewModel.rotationMatrixFromVector(listOf(0f, 0f, halfSqrt2))
        assertThat(quarterTurn[0]).isWithin(1e-6f).of(0f)
        assertThat(quarterTurn[1]).isWithin(1e-6f).of(-1f)
        assertThat(quarterTurn[3]).isWithin(1e-6f).of(1f)
        assertThat(quarterTurn[4]).isWithin(1e-6f).of(0f)
    }
}
