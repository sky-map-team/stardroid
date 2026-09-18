/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import com.google.android.stardroid.astronomy.LocalFrame
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.location.LocationSource
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.rotationMatrix
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.sensors.FakeSensorStatusSource
import com.google.android.stardroid.sensors.MagneticDeclinationSource
import com.google.android.stardroid.sensors.OrientationSample
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorReading
import com.google.android.stardroid.settings.FakeSettings
import com.google.android.stardroid.settings.OneEuroEaseOff
import com.google.android.stardroid.settings.OneEuroSteadiness
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    // A simple right-handed frame (east=x, north=y, up=z) — enough to exercise the pointing
    // jitter math without needing a real observer/time-derived SkyModel.localFrame.
    private val localFrame =
        MutableStateFlow(
            LocalFrame(
                trueNorth = Vector3(0.0, 1.0, 0.0),
                up = Vector3(0.0, 0.0, 1.0),
                trueEast = Vector3(1.0, 0.0, 0.0),
                magneticNorth = Vector3(0.0, 1.0, 0.0),
                magneticEast = Vector3(1.0, 0.0, 0.0),
            ),
        )
    private val orientationSamples = MutableSharedFlow<OrientationSample>(replay = 1)
    private val orientationSource = FakeOrientationSource(orientationSamples)

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
            orientationSource = orientationSource,
            localFrame = localFrame,
            ioContext = dispatcher,
        )
    }

    private class FakeOrientationSource(
        private val samples: Flow<OrientationSample>,
    ) : OrientationSource {
        override val available = true

        override fun orientations(): Flow<Matrix3> = throw NotImplementedError("unused in tests")

        override fun orientationSamples(): Flow<OrientationSample> = samples
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
    fun `snapshot surfaces the orientation settings driving the sensor pipeline`() =
        testScope.runTest {
            settings.disableGyroState.value = true
            settings.smoothingEnabledState.value = true
            settings.steadinessState.value = OneEuroSteadiness.HIGH
            settings.easeOffState.value = OneEuroEaseOff.LOW
            settings.reverseMagneticZState.value = true
            settings.viewDirectionModeState.value = ViewDirectionMode.TELESCOPE
            settings.dontShowCalibrationDialogState.value = true
            val collector = launch { viewModel.snapshots.collect {} }
            runCurrent()

            val snapshot = viewModel.snapshots.value
            assertThat(snapshot.disableGyro).isTrue()
            assertThat(snapshot.smoothingEnabled).isTrue()
            assertThat(snapshot.steadiness).isEqualTo(OneEuroSteadiness.HIGH)
            assertThat(snapshot.easeOff).isEqualTo(OneEuroEaseOff.LOW)
            assertThat(snapshot.reverseMagneticZ).isTrue()
            assertThat(snapshot.useMagneticCorrection).isTrue()
            assertThat(snapshot.viewDirectionMode).isEqualTo(ViewDirectionMode.TELESCOPE)
            assertThat(snapshot.dontShowCalibrationDialog).isTrue()
            collector.cancel()
        }

    @Test
    fun `pointing jitter shows the raw wobble the smoothed stream damps out`() =
        testScope.runTest {
            val collector = launch { viewModel.pointingJitter.collect {} }
            runCurrent()
            assertThat(viewModel.pointingJitter.value).isNull()

            // A phone yawing back and forth by 2° on the raw path, held dead steady on the
            // smoothed path — as if the filter were doing its job perfectly.
            for (angleDeg in listOf(-2.0, 2.0, -2.0, 2.0, -2.0, 2.0)) {
                orientationSamples.emit(
                    OrientationSample(
                        raw = rotationMatrix(angleDeg, Vector3.UNIT_X),
                        smoothed = Matrix3.IDENTITY,
                    ),
                )
                runCurrent()
            }

            val jitter = viewModel.pointingJitter.value
            assertThat(jitter).isNotNull()
            assertThat(jitter!!.smoothed.azimuthStdDevDeg).isWithin(1e-9).of(0.0)
            assertThat(jitter.smoothed.altitudeStdDevDeg).isWithin(1e-9).of(0.0)
            // The constant-orientation smoothed samples produce exactly zero jitter on both
            // axes; the wobbling raw samples must show up on at least one.
            assertThat(jitter.raw.azimuthStdDevDeg + jitter.raw.altitudeStdDevDeg)
                .isGreaterThan(0.5)
            collector.cancel()
        }

    @Test
    fun `pointing jitter reacts to a live view-direction-mode change`() =
        testScope.runTest {
            val collector = launch { viewModel.pointingJitter.collect {} }
            runCurrent()

            // The same identity orientation, resolved under two different view directions —
            // STANDARD and TELESCOPE point along different phone axes (SkyModel.kt), so the
            // resolved azimuth/altitude must differ even though the raw matrix didn't change.
            val identitySample =
                OrientationSample(raw = Matrix3.IDENTITY, smoothed = Matrix3.IDENTITY)
            orientationSamples.emit(identitySample)
            runCurrent()

            settings.viewDirectionModeState.value = ViewDirectionMode.TELESCOPE
            orientationSamples.emit(identitySample)
            runCurrent()

            // A regression of a prior bug read viewDirectionMode off a StateFlow that was never
            // actually collected, so its .value stayed pinned at STANDARD forever and this
            // second sample would have resolved identically to the first — zero jitter.
            val jitter = viewModel.pointingJitter.value
            assertThat(jitter).isNotNull()
            assertThat(jitter!!.raw.azimuthStdDevDeg + jitter.raw.altitudeStdDevDeg)
                .isGreaterThan(0.0)
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
