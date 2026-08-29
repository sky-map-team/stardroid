/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.BuildConfig
import com.google.android.stardroid.FlavorEdges
import com.google.android.stardroid.R
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.render.api.RendererInfo
import com.google.android.stardroid.satellites.SatelliteDiagnosticsState
import com.google.android.stardroid.satellites.forceSatelliteFetchForDebugging
import com.google.android.stardroid.satellites.readSatelliteDiagnostics
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.theme.StatusColors
import com.google.android.stardroid.ui.theme.statusColors
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The diagnostics screen — v1's `DiagnosticActivity` as a full-screen Compose overlay: general
 * device/app facts, the GL renderer identity, live sensor rows colored by calibration status,
 * polled location/time/network state.
 *
 * Each section is built as [DiagnosticsSection] data and then both drawn and — when the user
 * taps Send — formatted into the text report by [DiagnosticsReport]. One source of truth, so the
 * report a user mails us is exactly the screen they were looking at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    nightMode: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val colors = statusColors(nightMode)
    val snapshot by viewModel.snapshots.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var satelliteState by remember { mutableStateOf<SatelliteDiagnosticsState?>(null) }
    var forceFetchResult by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { satelliteState = readSatelliteDiagnostics(context) }
    val sections =
        buildList {
            add(generalSection())
            add(graphicsSection(snapshot.rendererInfo))
            add(sensorsSection(viewModel, colors))
            add(locationAndTimeSection(snapshot, colors))
            add(networkSection(snapshot))
            satelliteState?.let { add(satelliteSection(it)) }
        }
    val reportHeader = stringResource(R.string.diagnostics_report_header)
    val reportSubject = stringResource(R.string.diagnostics_share_subject)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                windowInsets = topBarWindowInsets(stringResource(R.string.diagnostics_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            DiagnosticsShare.send(
                                context,
                                reportSubject,
                                DiagnosticsReport.format(reportHeader, sections),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.diagnostics_share),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                for (section in sections) {
                    SectionHeader(section.title)
                    for (row in section.rows) {
                        DiagnosticRow(row.label, row.value, row.valueColor)
                    }
                }
                // Debug builds only. A discoverable "fetch now" in release is the retry storm
                // the circuit breaker exists to prevent, so the gate is the build type — the
                // one gate an ordinary user cannot reach.
                if (BuildConfig.DEBUG && satelliteState != null) {
                    Button(
                        onClick = {
                            scope.launch {
                                forceFetchResult = forceSatelliteFetchForDebugging(context)
                                satelliteState = readSatelliteDiagnostics(context)
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.diagnostics_satellite_force_fetch))
                    }
                    forceFetchResult?.let { DiagnosticRow("Result", it) }
                }
            }
        }
    }
}

@Composable
private fun generalSection(): DiagnosticsSection {
    val context = LocalContext.current
    val appVersion =
        remember(context) {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            Pair(info.versionName.orEmpty(), info.longVersionCode)
        }
    return DiagnosticsSection(
        stringResource(R.string.diagnostics_section_general),
        listOf(
            DiagnosticsRow(
                stringResource(R.string.diagnostics_device),
                stringResource(
                    R.string.diagnostics_phone_format,
                    Build.MODEL,
                    Build.HARDWARE,
                    Locale.getDefault().language,
                ),
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_android_version),
                "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_sky_map_version),
                stringResource(
                    R.string.diagnostics_sky_map_version_format,
                    appVersion.first,
                    appVersion.second,
                    FlavorEdges.FLAVOR_LABEL,
                ),
            ),
        ),
    )
}

/**
 * What GPU and driver the sky is actually being drawn by — the fact most worth having in a
 * rendering bug report and the one nothing else on the device exposes. The limits row carries
 * the two capability ranges that silently clamp when an implementation declines to honour them
 * (see [RendererInfo]).
 */
@Composable
private fun graphicsSection(info: RendererInfo?): DiagnosticsSection {
    val unavailable = stringResource(R.string.diagnostics_gl_unavailable)
    return DiagnosticsSection(
        stringResource(R.string.diagnostics_section_graphics),
        listOf(
            DiagnosticsRow(
                stringResource(R.string.diagnostics_gl_renderer),
                info?.renderer?.ifBlank { unavailable } ?: unavailable,
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_gl_version),
                if (info == null) {
                    unavailable
                } else {
                    stringResource(
                        R.string.diagnostics_gl_version_format,
                        info.version,
                        info.backend,
                    )
                },
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_gl_limits),
                if (info == null) {
                    unavailable
                } else {
                    stringResource(
                        R.string.diagnostics_gl_limits_format,
                        info.maxTextureSizePx,
                        rangeText(info.lineWidthRange, unavailable),
                        rangeText(info.pointSizeRange, unavailable),
                    )
                },
            ),
        ),
    )
}

@Composable
private fun sensorsSection(
    viewModel: DiagnosticsViewModel,
    colors: StatusColors,
): DiagnosticsSection {
    val rows = mutableListOf<DiagnosticsRow>()
    for (kind in SensorKind.entries) {
        val row by viewModel.sensors.getValue(kind).collectAsStateWithLifecycle()
        rows +=
            DiagnosticsRow(
                label = stringResource(sensorName(kind)),
                value = sensorText(row),
                valueColor = sensorColor(row, colors),
            )
    }
    val matrix by viewModel.rotationMatrix.collectAsStateWithLifecycle(null)
    matrix?.let { values ->
        for (rowIndex in 0..2) {
            rows +=
                DiagnosticsRow(
                    label =
                        if (rowIndex == 0) {
                            stringResource(R.string.diagnostics_rotation_matrix)
                        } else {
                            ""
                        },
                    value =
                        values
                            .subList(rowIndex * 3, rowIndex * 3 + 3)
                            .joinToString(",") { "%.2f".format(Locale.US, it) },
                )
        }
    }
    return DiagnosticsSection(stringResource(R.string.diagnostics_section_sensors), rows)
}

@Composable
private fun locationAndTimeSection(
    snapshot: DiagnosticsSnapshot,
    colors: StatusColors,
): DiagnosticsSection =
    DiagnosticsSection(
        stringResource(R.string.diagnostics_section_location_time),
        listOf(
            DiagnosticsRow(
                label = stringResource(R.string.diagnostics_location_permission),
                value =
                    stringResource(
                        if (snapshot.locationPermissionGranted) {
                            R.string.diagnostics_permission_granted
                        } else {
                            R.string.diagnostics_permission_denied
                        },
                    ),
                valueColor = if (snapshot.locationPermissionGranted) colors.good else colors.bad,
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_gps),
                stringResource(
                    when (snapshot.gpsStatus) {
                        GpsStatus.NO_GPS -> R.string.diagnostics_no_gps
                        GpsStatus.ENABLED -> R.string.diagnostics_enabled
                        GpsStatus.DISABLED -> R.string.diagnostics_disabled
                        GpsStatus.PERMISSION_DISABLED -> R.string.diagnostics_permission_disabled
                    },
                ),
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_location),
                locationText(snapshot.locationState),
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_pointing),
                snapshot.pointing?.let { pointing ->
                    "${raText(pointing.raDeg)}, " +
                        stringResource(R.string.diagnostics_dec_format, pointing.decDeg)
                } ?: "",
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_magnetic_correction),
                magneticCorrectionText(snapshot.magneticCorrectionDeg),
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_alignment_adjustment),
                stringResource(
                    R.string.diagnostics_alignment_format,
                    snapshot.alignmentAzimuthDeg,
                    snapshot.alignmentAltitudeDeg,
                ),
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_local_datetime),
                remember(snapshot.time) { formatTime(snapshot.time, ZoneId.systemDefault()) },
            ),
            DiagnosticsRow(
                stringResource(R.string.diagnostics_utc_datetime),
                remember(snapshot.time) { formatTime(snapshot.time, ZoneOffset.UTC) },
            ),
        ),
    )

@Composable
private fun networkSection(snapshot: DiagnosticsSnapshot): DiagnosticsSection =
    DiagnosticsSection(
        stringResource(R.string.diagnostics_section_network),
        listOf(
            DiagnosticsRow(
                stringResource(R.string.diagnostics_connection),
                when (snapshot.network) {
                    NetworkStatus.DISCONNECTED -> stringResource(R.string.diagnostics_disconnected)
                    NetworkStatus.CONNECTED -> stringResource(R.string.diagnostics_connected)
                    NetworkStatus.CONNECTED_WIFI ->
                        stringResource(R.string.diagnostics_connected) +
                            stringResource(R.string.diagnostics_wifi)
                    NetworkStatus.CONNECTED_CELL ->
                        stringResource(R.string.diagnostics_connected) +
                            stringResource(R.string.diagnostics_cell)
                },
            ),
        ),
    )

/**
 * Satellite orbital-data status — the read-only half of the reporting CelesTrak's usage policy
 * requires of us: someone who reports "satellites aren't updating" can read the status code and
 * the pause state straight off this screen (and off the shared report built from it).
 */
@Composable
private fun satelliteSection(state: SatelliteDiagnosticsState): DiagnosticsSection {
    val rows = mutableListOf<DiagnosticsRow>()
    rows +=
        DiagnosticsRow(
            stringResource(R.string.diagnostics_satellite_data),
            if (state.freshness == ElementFreshness.ABSENT || state.ageDays == null) {
                stringResource(R.string.diagnostics_satellite_data_none)
            } else {
                stringResource(
                    R.string.diagnostics_satellite_data_format,
                    state.satelliteCount,
                    state.ageDays,
                )
            },
        )
    rows +=
        DiagnosticsRow(
            stringResource(R.string.diagnostics_satellite_last_fetch),
            state.lastStatusCode?.let { code ->
                state.lastSuccess?.let { "$code at $it" } ?: "$code"
            } ?: stringResource(R.string.diagnostics_satellite_never_fetched),
        )
    state.circuitOpenUntil?.let { until ->
        rows +=
            DiagnosticsRow(
                stringResource(R.string.diagnostics_satellite_circuit_open),
                stringResource(
                    R.string.diagnostics_satellite_circuit_open_format,
                    until.toString(),
                    state.consecutiveFailures,
                ),
            )
    }
    return DiagnosticsSection(stringResource(R.string.diagnostics_section_satellites), rows)
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/** A GL capability range as `min–max`, or [absent] when the driver declined to report one. */
private fun rangeText(
    range: RendererInfo.Range?,
    absent: String,
): String = range?.let { "%.1f–%.1f".format(Locale.US, it.min, it.max) } ?: absent

@Composable
private fun sensorText(row: SensorRow): String =
    when (row) {
        is SensorRow.Absent -> stringResource(R.string.diagnostics_sensor_absent)
        is SensorRow.Present ->
            row.reading?.values?.joinToString(",") { "%.2f".format(Locale.US, it) } ?: ""
    }

/** v1's decoder: absent grey; unreliable/no-contact red, low orange, medium yellow, high green. */
private fun sensorColor(
    row: SensorRow,
    colors: StatusColors,
): Color =
    when (row) {
        is SensorRow.Absent -> colors.absent
        is SensorRow.Present ->
            when (row.reading?.accuracy) {
                SensorAccuracy.HIGH -> colors.good
                SensorAccuracy.MEDIUM -> colors.ok
                SensorAccuracy.LOW -> colors.warning
                SensorAccuracy.UNRELIABLE, SensorAccuracy.NO_CONTACT -> colors.bad
                null -> Color.Unspecified
            }
    }

@Composable
private fun locationText(state: LocationState): String =
    when (state) {
        is LocationState.Confirmed ->
            stringResource(
                R.string.diagnostics_location_format,
                state.location.latitudeDeg,
                state.location.longitudeDeg,
                state.source.name.lowercase(),
            )
        is LocationState.Unset -> stringResource(R.string.diagnostics_location_unset)
        is LocationState.Acquiring, is LocationState.AcquiringTimeout ->
            stringResource(R.string.diagnostics_location_acquiring)
        is LocationState.HardwareUnavailable ->
            stringResource(R.string.diagnostics_location_hardware_unavailable)
        is LocationState.PermissionDenied, is LocationState.PermissionPermanentlyDenied ->
            stringResource(R.string.diagnostics_permission_denied)
    }

@Composable
private fun magneticCorrectionText(degrees: Double): String =
    stringResource(
        R.string.diagnostics_magnetic_correction_format,
        kotlin.math.abs(degrees),
        stringResource(if (degrees >= 0) R.string.diagnostics_east else R.string.diagnostics_west),
    )

/** v1's `getDegreeInHour`: RA degrees as truncated h/m/s. */
private fun raText(raDeg: Double): String {
    val hours = raDeg / 15.0
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    val s = (((hours - h) * 60 - m) * 60).toInt()
    return "${h}h ${m}m ${s}s"
}

private val diagnosticsTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss")

private fun formatTime(
    time: Instant,
    zone: ZoneId,
): String =
    diagnosticsTimeFormatter
        .withZone(zone)
        .format(java.time.Instant.ofEpochMilli(time.toEpochMilliseconds()))

@androidx.annotation.StringRes
private fun sensorName(kind: SensorKind): Int =
    when (kind) {
        SensorKind.ACCELEROMETER -> R.string.diagnostics_accelerometer
        SensorKind.MAGNETOMETER -> R.string.diagnostics_compass
        SensorKind.GYROSCOPE -> R.string.diagnostics_gyroscope
        SensorKind.ROTATION_VECTOR -> R.string.diagnostics_rotation
        SensorKind.LIGHT -> R.string.diagnostics_light_level
    }
