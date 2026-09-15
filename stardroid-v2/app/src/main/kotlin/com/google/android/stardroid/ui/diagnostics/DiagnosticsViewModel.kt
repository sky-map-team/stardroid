/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.RendererInfo
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.sensors.MagneticDeclinationSource
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorReading
import com.google.android.stardroid.sensors.SensorStatusSource
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.math.sqrt

/** GPS provider status (v1's `updateLocation` provider probe). */
enum class GpsStatus {
    NO_GPS,
    ENABLED,
    DISABLED,

    /** Probing the provider threw a `SecurityException` (v1's "Permission disabled"). */
    PERMISSION_DISABLED,
}

/** Network connectivity as v1 reported it: connected or not, and over what. */
enum class NetworkStatus {
    DISCONNECTED,
    CONNECTED,
    CONNECTED_WIFI,
    CONNECTED_CELL,
}

/** One sensor's diagnostics row: absent, or present with its latest reading (if any). */
sealed class SensorRow {
    data object Absent : SensorRow()

    data class Present(val reading: SensorReading?) : SensorRow()
}

/** The 500 ms snapshot of everything the diagnostics screen polls (v1's update runnable). */
data class DiagnosticsSnapshot(
    val locationPermissionGranted: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.NO_GPS,
    val locationState: LocationState = LocationState.Unset,
    val magneticCorrectionDeg: Double = 0.0,
    /** The drag-to-align correction pair (D64), read-only here — the setter is the drag. */
    val alignmentAzimuthDeg: Double = 0.0,
    val alignmentAltitudeDeg: Double = 0.0,
    val pointing: RaDec? = null,
    val time: Instant = Instant.fromEpochMilliseconds(0),
    val network: NetworkStatus = NetworkStatus.DISCONNECTED,
    /** Null until the GL surface has been created at least once. */
    val rendererInfo: RendererInfo? = null,
)

/**
 * Feeds the diagnostics screen (screens-and-startup.md): live per-sensor rows plus a polled
 * snapshot of location/pointing/time/network state, both active only while the screen
 * collects — the sensors unregister the moment the screen closes (v1 onPause).
 *
 * Static device/app facts (model, versions) don't belong here: the screen reads them straight
 * from `Build` and the package info.
 */
@OptIn(FlowPreview::class)
class DiagnosticsViewModel(
    sensorStatus: SensorStatusSource,
    private val locationStates: StateFlow<LocationState>,
    private val camera: StateFlow<SkyCamera>,
    private val settings: Settings,
    private val declinationSource: MagneticDeclinationSource,
    private val now: () -> Instant,
    private val isLocationPermissionGranted: () -> Boolean,
    private val gpsStatus: () -> GpsStatus,
    private val networkStatus: () -> NetworkStatus,
    private val rendererInfo: () -> RendererInfo? = { null },
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : ViewModel() {
    /** Sensor rows keyed in v1's display order. */
    val sensors: Map<SensorKind, StateFlow<SensorRow>> =
        SensorKind.entries.associateWith { kind ->
            if (!sensorStatus.hasSensor(kind)) {
                MutableStateFlow(SensorRow.Absent)
            } else {
                sensorStatus
                    .readings(kind)
                    // Sensors can report far faster than the eye can read; match the polled
                    // snapshot's cadence instead of recomposing on every raw sample.
                    .sample(UPDATE_PERIOD_MILLIS)
                    .map<SensorReading, SensorRow> { SensorRow.Present(it) }
                    .stateIn(
                        viewModelScope,
                        // A short timeout survives config-change recomposition without
                        // unregistering and immediately re-registering the sensor listener.
                        SharingStarted.WhileSubscribed(5_000),
                        SensorRow.Present(reading = null),
                    )
            }
        }

    /**
     * The rotation-vector quaternion as a row-major rotation matrix — v1 showed
     * `getRotationMatrixFromVector`'s output; this is the same construction in pure Kotlin
     * (Android's helper is unavailable to JVM tests).
     */
    val rotationMatrix: Flow<List<Float>?> =
        sensors.getValue(SensorKind.ROTATION_VECTOR).map { row ->
            val values = (row as? SensorRow.Present)?.reading?.values ?: return@map null
            if (values.size < 3) return@map null
            rotationMatrixFromVector(values)
        }

    /**
     * The correction the map actually applies (MapViewModel's declination plus the
     * drag-to-align azimuth offset, D64 — v1 showed its model's live value the same way),
     * plus the raw alignment pair as its own read-only row. Reactive to settings changes
     * rather than only re-sampling them on the next 500 ms tick.
     */
    val snapshots: StateFlow<DiagnosticsSnapshot> =
        combine(
            flow {
                while (true) {
                    emit(now())
                    delay(UPDATE_PERIOD_MILLIS)
                }
            },
            locationStates,
            settings.useMagneticCorrection,
            settings.sensorAzimuthAdjustmentDeg,
            settings.sensorAltitudeAdjustmentDeg,
        ) { time, locationState, useMagneticCorrection, azimuthAdjustment, altitudeAdjustment ->
            val location = (locationState as? LocationState.Confirmed)?.location
            val declination =
                if (location != null && useMagneticCorrection) {
                    declinationSource.declinationDeg(location, time)
                } else {
                    0.0
                }
            DiagnosticsSnapshot(
                locationPermissionGranted = isLocationPermissionGranted(),
                gpsStatus = gpsStatus(),
                locationState = locationState,
                magneticCorrectionDeg = declination + azimuthAdjustment,
                alignmentAzimuthDeg = azimuthAdjustment,
                alignmentAltitudeDeg = altitudeAdjustment,
                pointing = RaDec.fromGeocentricVector(camera.value.lineOfSight),
                time = time,
                network = networkStatus(),
                rendererInfo = rendererInfo(),
            )
        }.flowOn(ioContext)
            // Matches the sensor flows' timeout: survives config-change recomposition without
            // redoing the IPC-backed gpsStatus()/networkStatus() calls.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsSnapshot())

    companion object {
        /** v1 `DiagnosticActivity.UPDATE_PERIOD_MILLIS`. */
        const val UPDATE_PERIOD_MILLIS = 500L

        /**
         * `SensorManager.getRotationMatrixFromVector` in pure Kotlin: the standard
         * quaternion-to-matrix expansion, deriving the scalar term when the sensor only
         * reports the vector part.
         */
        internal fun rotationMatrixFromVector(values: List<Float>): List<Float> {
            val x = values[0]
            val y = values[1]
            val z = values[2]
            val w =
                if (values.size >= 4 && values[3] != 0f) {
                    values[3]
                } else {
                    val remainder = 1f - x * x - y * y - z * z
                    if (remainder > 0f) sqrt(remainder) else 0f
                }
            return listOf(
                1f - 2f * (y * y + z * z), 2f * (x * y - z * w), 2f * (x * z + y * w),
                2f * (x * y + z * w), 1f - 2f * (x * x + z * z), 2f * (y * z - x * w),
                2f * (x * z - y * w), 2f * (y * z + x * w), 1f - 2f * (x * x + y * y),
            )
        }
    }
}
