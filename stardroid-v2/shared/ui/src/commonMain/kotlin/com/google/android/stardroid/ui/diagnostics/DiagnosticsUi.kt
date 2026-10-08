/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.render.api.RendererInfo
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.FetchResult
import com.google.android.stardroid.ui.common.formatAndroidStyle
import com.google.android.stardroid.ui.common.formattedStringResource
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.diagnostics_accelerometer
import com.google.android.stardroid.ui.resources.diagnostics_alignment_adjustment
import com.google.android.stardroid.ui.resources.diagnostics_alignment_format
import com.google.android.stardroid.ui.resources.diagnostics_calibration_dialog
import com.google.android.stardroid.ui.resources.diagnostics_calibration_dialog_shown
import com.google.android.stardroid.ui.resources.diagnostics_calibration_dialog_suppressed
import com.google.android.stardroid.ui.resources.diagnostics_cell
import com.google.android.stardroid.ui.resources.diagnostics_compass
import com.google.android.stardroid.ui.resources.diagnostics_connected
import com.google.android.stardroid.ui.resources.diagnostics_connection
import com.google.android.stardroid.ui.resources.diagnostics_dec_format
import com.google.android.stardroid.ui.resources.diagnostics_device
import com.google.android.stardroid.ui.resources.diagnostics_disabled
import com.google.android.stardroid.ui.resources.diagnostics_disconnected
import com.google.android.stardroid.ui.resources.diagnostics_ease_off
import com.google.android.stardroid.ui.resources.diagnostics_east
import com.google.android.stardroid.ui.resources.diagnostics_enabled
import com.google.android.stardroid.ui.resources.diagnostics_experiments_fetch_now
import com.google.android.stardroid.ui.resources.diagnostics_experiments_last_fetched
import com.google.android.stardroid.ui.resources.diagnostics_experiments_never_fetched
import com.google.android.stardroid.ui.resources.diagnostics_gl_limits_format
import com.google.android.stardroid.ui.resources.diagnostics_gl_renderer
import com.google.android.stardroid.ui.resources.diagnostics_gl_unavailable
import com.google.android.stardroid.ui.resources.diagnostics_gl_version_format
import com.google.android.stardroid.ui.resources.diagnostics_gps
import com.google.android.stardroid.ui.resources.diagnostics_gyro_disabled
import com.google.android.stardroid.ui.resources.diagnostics_gyro_fused
import com.google.android.stardroid.ui.resources.diagnostics_gyro_mode
import com.google.android.stardroid.ui.resources.diagnostics_gyroscope
import com.google.android.stardroid.ui.resources.diagnostics_jitter_format
import com.google.android.stardroid.ui.resources.diagnostics_jitter_pending
import com.google.android.stardroid.ui.resources.diagnostics_jitter_raw
import com.google.android.stardroid.ui.resources.diagnostics_jitter_smoothed
import com.google.android.stardroid.ui.resources.diagnostics_light_level
import com.google.android.stardroid.ui.resources.diagnostics_local_datetime
import com.google.android.stardroid.ui.resources.diagnostics_location
import com.google.android.stardroid.ui.resources.diagnostics_location_acquiring
import com.google.android.stardroid.ui.resources.diagnostics_location_format
import com.google.android.stardroid.ui.resources.diagnostics_location_hardware_unavailable
import com.google.android.stardroid.ui.resources.diagnostics_location_permission
import com.google.android.stardroid.ui.resources.diagnostics_location_unset
import com.google.android.stardroid.ui.resources.diagnostics_magnetic_correction
import com.google.android.stardroid.ui.resources.diagnostics_magnetic_correction_format
import com.google.android.stardroid.ui.resources.diagnostics_no_gps
import com.google.android.stardroid.ui.resources.diagnostics_permission_denied
import com.google.android.stardroid.ui.resources.diagnostics_permission_disabled
import com.google.android.stardroid.ui.resources.diagnostics_permission_granted
import com.google.android.stardroid.ui.resources.diagnostics_phone_format
import com.google.android.stardroid.ui.resources.diagnostics_pointing
import com.google.android.stardroid.ui.resources.diagnostics_report_header
import com.google.android.stardroid.ui.resources.diagnostics_reverse_magnetic_z
import com.google.android.stardroid.ui.resources.diagnostics_rotation
import com.google.android.stardroid.ui.resources.diagnostics_rotation_matrix
import com.google.android.stardroid.ui.resources.diagnostics_satellite_force_fetch
import com.google.android.stardroid.ui.resources.diagnostics_section_experiments
import com.google.android.stardroid.ui.resources.diagnostics_section_general
import com.google.android.stardroid.ui.resources.diagnostics_section_graphics
import com.google.android.stardroid.ui.resources.diagnostics_section_location_time
import com.google.android.stardroid.ui.resources.diagnostics_section_network
import com.google.android.stardroid.ui.resources.diagnostics_section_orientation_settings
import com.google.android.stardroid.ui.resources.diagnostics_section_recent_log
import com.google.android.stardroid.ui.resources.diagnostics_section_sensors
import com.google.android.stardroid.ui.resources.diagnostics_sensor_absent
import com.google.android.stardroid.ui.resources.diagnostics_sensor_rate_format
import com.google.android.stardroid.ui.resources.diagnostics_share
import com.google.android.stardroid.ui.resources.diagnostics_share_subject
import com.google.android.stardroid.ui.resources.diagnostics_sky_map_version
import com.google.android.stardroid.ui.resources.diagnostics_sky_map_version_format
import com.google.android.stardroid.ui.resources.diagnostics_smoothing
import com.google.android.stardroid.ui.resources.diagnostics_steadiness
import com.google.android.stardroid.ui.resources.diagnostics_title
import com.google.android.stardroid.ui.resources.diagnostics_use_magnetic_correction_setting
import com.google.android.stardroid.ui.resources.diagnostics_utc_datetime
import com.google.android.stardroid.ui.resources.diagnostics_view_direction_mode
import com.google.android.stardroid.ui.resources.diagnostics_west
import com.google.android.stardroid.ui.resources.diagnostics_wifi
import com.google.android.stardroid.ui.resources.settings_back
import com.google.android.stardroid.ui.theme.StatusColors
import com.google.android.stardroid.ui.theme.statusColors
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * What only the platform knows: the General section's facts about the device and the build, and
 * the names of its OS and graphics API for the row labels.
 *
 * @property model the device's marketing model, e.g. "Pixel 9 Pro" or "iPhone".
 * @property hardware the board or machine identifier, e.g. "caiman" or "iPhone14,5".
 * @property osVersionLabel the row label naming the OS, e.g. "Android version".
 * @property graphicsVersionLabel the Graphics row label for the driver, e.g. "OpenGL".
 * @property graphicsLimitsLabel the Graphics row label for the limits, e.g. "GL limits".
 */
data class DiagnosticsPlatform(
    val model: String,
    val hardware: String,
    val osVersionLabel: StringResource,
    val osVersion: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val buildLabel: String,
    val graphicsVersionLabel: StringResource,
    val graphicsLimitsLabel: StringResource,
)

/**
 * The diagnostics screen — v1's `DiagnosticActivity` as a full-screen Compose overlay: general
 * device/app facts, the GL renderer identity, live sensor rows colored by calibration status,
 * polled location/time/network state.
 *
 * Each section is built as [DiagnosticsSection] data and then both drawn and — when the user
 * taps Send — formatted into the text report by [DiagnosticsReport]. One source of truth, so the
 * report a user mails us is exactly the screen they were looking at.
 *
 * The host supplies what only its platform has: the [platform] facts, the way the report leaves
 * ([onSendReport]), the app's own recent log, and Android's satellite data status (with a
 * debug-only fetch, [onForceSatelliteFetch]); null or empty hides each. Back belongs to the host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    nightMode: Boolean,
    experimentConfig: ExperimentConfig,
    platform: DiagnosticsPlatform,
    onBack: () -> Unit,
    onSendReport: (subject: String, body: String) -> Unit,
    recentLogLines: suspend () -> List<String> = { emptyList() },
    satelliteSection: DiagnosticsSection? = null,
    onForceSatelliteFetch: (suspend () -> String)? = null,
) {
    val colors = statusColors(nightMode)
    val snapshot by viewModel.snapshots.collectAsStateWithLifecycle()
    val jitter by viewModel.pointingJitter.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var forceFetchResult by remember { mutableStateOf<String?>(null) }
    // Bumped after a forced experiment fetch so the section is rebuilt with the new values.
    var experimentsRefresh by remember { mutableIntStateOf(0) }
    var experimentFetchResult by remember { mutableStateOf<String?>(null) }
    val sections =
        buildList {
            add(generalSection(platform))
            add(graphicsSection(platform, snapshot.rendererInfo))
            add(sensorsSection(viewModel, colors))
            add(orientationSettingsSection(snapshot, jitter))
            add(locationAndTimeSection(snapshot, colors))
            add(networkSection(snapshot))
            satelliteSection?.let { add(it) }
            add(experimentsSection(experimentConfig, experimentsRefresh))
        }
    val reportHeader = stringResource(Res.string.diagnostics_report_header)
    val reportSubject = stringResource(Res.string.diagnostics_share_subject)
    val recentLogTitle = stringResource(Res.string.diagnostics_section_recent_log)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.diagnostics_title)) },
                windowInsets = topBarWindowInsets(stringResource(Res.string.diagnostics_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val logLines = recentLogLines()
                                val reportSections =
                                    if (logLines.isEmpty()) {
                                        sections
                                    } else {
                                        sections +
                                            DiagnosticsSection(
                                                recentLogTitle,
                                                logLines.map { DiagnosticsRow("", it) },
                                            )
                                    }
                                onSendReport(
                                    reportSubject,
                                    DiagnosticsReport.format(reportHeader, reportSections),
                                )
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(Res.string.diagnostics_share),
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
                if (experimentConfig.canFetch) {
                    Button(
                        onClick = {
                            scope.launch {
                                val result = viewModel.fetchExperiments(experimentConfig)
                                experimentFetchResult =
                                    when (result) {
                                        FetchResult.Unsupported -> "Not supported in this build"
                                        is FetchResult.Success ->
                                            if (result.updated) {
                                                "Fetched; values changed"
                                            } else {
                                                "Fetched; no change"
                                            }
                                        is FetchResult.Failure -> "Failed: ${result.message}"
                                    }
                                experimentsRefresh++
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(Res.string.diagnostics_experiments_fetch_now))
                    }
                    experimentFetchResult?.let { DiagnosticRow("Result", it) }
                }
                // Debug builds only, which the host decides. A discoverable "fetch now" in
                // release is the retry storm the circuit breaker exists to prevent, so the gate
                // is the build type — the one gate an ordinary user cannot reach.
                if (onForceSatelliteFetch != null) {
                    Button(
                        onClick = { scope.launch { forceFetchResult = onForceSatelliteFetch() } },
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(Res.string.diagnostics_satellite_force_fetch))
                    }
                    forceFetchResult?.let { DiagnosticRow("Result", it) }
                }
            }
        }
    }
}

@Composable
private fun generalSection(platform: DiagnosticsPlatform): DiagnosticsSection =
    DiagnosticsSection(
        stringResource(Res.string.diagnostics_section_general),
        listOf(
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_device),
                formattedStringResource(
                    Res.string.diagnostics_phone_format,
                    platform.model,
                    platform.hardware,
                    Locale.current.language,
                ),
            ),
            DiagnosticsRow(stringResource(platform.osVersionLabel), platform.osVersion),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_sky_map_version),
                formattedStringResource(
                    Res.string.diagnostics_sky_map_version_format,
                    platform.appVersionName,
                    platform.appVersionCode,
                    platform.buildLabel,
                ),
            ),
        ),
    )

/**
 * What GPU and driver the sky is actually being drawn by — the fact most worth having in a
 * rendering bug report and the one nothing else on the device exposes. The limits row carries
 * the two capability ranges that silently clamp when an implementation declines to honour them
 * (see [RendererInfo]).
 */
@Composable
private fun graphicsSection(
    platform: DiagnosticsPlatform,
    info: RendererInfo?,
): DiagnosticsSection {
    val unavailable = stringResource(Res.string.diagnostics_gl_unavailable)
    return DiagnosticsSection(
        stringResource(Res.string.diagnostics_section_graphics),
        listOf(
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_gl_renderer),
                info?.renderer?.ifBlank { unavailable } ?: unavailable,
            ),
            DiagnosticsRow(
                stringResource(platform.graphicsVersionLabel),
                if (info == null) {
                    unavailable
                } else {
                    formattedStringResource(
                        Res.string.diagnostics_gl_version_format,
                        info.version,
                        info.backend,
                    )
                },
            ),
            DiagnosticsRow(
                stringResource(platform.graphicsLimitsLabel),
                if (info == null) {
                    unavailable
                } else {
                    formattedStringResource(
                        Res.string.diagnostics_gl_limits_format,
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
    val rates by viewModel.sensorRates.collectAsStateWithLifecycle()
    for (kind in SensorKind.entries) {
        val row by viewModel.sensors.getValue(kind).collectAsStateWithLifecycle()
        rows +=
            DiagnosticsRow(
                label = stringResource(sensorName(kind)),
                value = sensorText(row) + rateSuffix(row, rates[kind]),
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
                            stringResource(Res.string.diagnostics_rotation_matrix)
                        } else {
                            ""
                        },
                    value =
                        values
                            .subList(rowIndex * 3, rowIndex * 3 + 3)
                            .joinToString(",") { twoPlaces(it) },
                )
        }
    }
    return DiagnosticsSection(stringResource(Res.string.diagnostics_section_sensors), rows)
}

/**
 * The settings that shape the sensor rows above — reads directly off [DiagnosticsSnapshot]
 * rather than [Settings] itself, so the report always matches what the screen is showing.
 */
@Composable
private fun orientationSettingsSection(
    snapshot: DiagnosticsSnapshot,
    jitter: PointingJitterSnapshot?,
): DiagnosticsSection =
    DiagnosticsSection(
        stringResource(Res.string.diagnostics_section_orientation_settings),
        listOf(
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_gyro_mode),
                stringResource(
                    if (snapshot.disableGyro) {
                        Res.string.diagnostics_gyro_disabled
                    } else {
                        Res.string.diagnostics_gyro_fused
                    },
                ),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_smoothing),
                onOffText(snapshot.smoothingEnabled),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_steadiness),
                enumDisplayName(snapshot.steadiness),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_ease_off),
                enumDisplayName(snapshot.easeOff),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_reverse_magnetic_z),
                onOffText(snapshot.reverseMagneticZ),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_use_magnetic_correction_setting),
                onOffText(snapshot.useMagneticCorrection),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_view_direction_mode),
                enumDisplayName(snapshot.viewDirectionMode),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_calibration_dialog),
                stringResource(
                    if (snapshot.dontShowCalibrationDialog) {
                        Res.string.diagnostics_calibration_dialog_suppressed
                    } else {
                        Res.string.diagnostics_calibration_dialog_shown
                    },
                ),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_jitter_raw),
                jitterText(jitter?.raw),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_jitter_smoothed),
                jitterText(jitter?.smoothed),
            ),
        ),
    )

@Composable
private fun onOffText(enabled: Boolean): String =
    stringResource(
        if (enabled) Res.string.diagnostics_enabled else Res.string.diagnostics_disabled,
    )

/** "az σ0.42°, alt σ0.18°" over the trailing window (D95), or a placeholder before it fills. */
@Composable
private fun jitterText(jitter: PointingJitter?): String =
    if (jitter == null) {
        stringResource(Res.string.diagnostics_jitter_pending)
    } else {
        formattedStringResource(
            Res.string.diagnostics_jitter_format,
            jitter.azimuthStdDevDeg,
            jitter.altitudeStdDevDeg,
        )
    }

/** `VERY_HIGH` -> `Very High` — technical enum names are diagnostic values, not translated. */
private fun enumDisplayName(value: Enum<*>): String =
    value.name
        .split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }

@Composable
private fun locationAndTimeSection(
    snapshot: DiagnosticsSnapshot,
    colors: StatusColors,
): DiagnosticsSection =
    DiagnosticsSection(
        stringResource(Res.string.diagnostics_section_location_time),
        listOf(
            DiagnosticsRow(
                label = stringResource(Res.string.diagnostics_location_permission),
                value =
                    stringResource(
                        if (snapshot.locationPermissionGranted) {
                            Res.string.diagnostics_permission_granted
                        } else {
                            Res.string.diagnostics_permission_denied
                        },
                    ),
                valueColor = if (snapshot.locationPermissionGranted) colors.good else colors.bad,
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_gps),
                stringResource(
                    when (snapshot.gpsStatus) {
                        GpsStatus.NO_GPS -> Res.string.diagnostics_no_gps
                        GpsStatus.ENABLED -> Res.string.diagnostics_enabled
                        GpsStatus.DISABLED -> Res.string.diagnostics_disabled
                        GpsStatus.PERMISSION_DISABLED -> Res.string.diagnostics_permission_disabled
                    },
                ),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_location),
                locationText(snapshot.locationState),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_pointing),
                snapshot.pointing?.let { pointing ->
                    "${raText(pointing.raDeg)}, " +
                        formattedStringResource(Res.string.diagnostics_dec_format, pointing.decDeg)
                } ?: "",
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_magnetic_correction),
                magneticCorrectionText(snapshot.magneticCorrectionDeg),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_alignment_adjustment),
                formattedStringResource(
                    Res.string.diagnostics_alignment_format,
                    snapshot.alignmentAzimuthDeg,
                    snapshot.alignmentAltitudeDeg,
                ),
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_local_datetime),
                remember(snapshot.time) {
                    formatTime(snapshot.time, TimeZone.currentSystemDefault())
                },
            ),
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_utc_datetime),
                remember(snapshot.time) { formatTime(snapshot.time, TimeZone.UTC) },
            ),
        ),
    )

@Composable
private fun networkSection(snapshot: DiagnosticsSnapshot): DiagnosticsSection =
    DiagnosticsSection(
        stringResource(Res.string.diagnostics_section_network),
        listOf(
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_connection),
                when (snapshot.network) {
                    NetworkStatus.DISCONNECTED ->
                        stringResource(Res.string.diagnostics_disconnected)
                    NetworkStatus.CONNECTED -> stringResource(Res.string.diagnostics_connected)
                    NetworkStatus.CONNECTED_WIFI ->
                        stringResource(Res.string.diagnostics_connected) +
                            stringResource(Res.string.diagnostics_wifi)
                    NetworkStatus.CONNECTED_CELL ->
                        stringResource(Res.string.diagnostics_connected) +
                            stringResource(Res.string.diagnostics_cell)
                },
            ),
        ),
    )

/**
 * The current value of every [Experiment] flag as the app is reading it — Remote Config on gms,
 * the shipped defaults on fdroid and iOS. Re-read on every recomposition — cheap in-memory
 * lookups — so a fetch that activates while the screen is open shows up with the next
 * recomposition.
 */
@Composable
private fun experimentsSection(
    config: ExperimentConfig,
    @Suppress("UNUSED_PARAMETER") refresh: Int,
): DiagnosticsSection {
    val lastFetched =
        config.lastFetchTimeMillis?.let {
            formatTime(Instant.fromEpochMilliseconds(it), TimeZone.currentSystemDefault())
        } ?: stringResource(Res.string.diagnostics_experiments_never_fetched)
    val rows =
        buildList {
            if (config.canFetch) {
                add(
                    DiagnosticsRow(
                        stringResource(Res.string.diagnostics_experiments_last_fetched),
                        lastFetched,
                    ),
                )
            }
            Experiment.entries.forEach {
                add(
                    DiagnosticsRow(it.key, config.isEnabled(it).toString()),
                )
            }
        }
    return DiagnosticsSection(stringResource(Res.string.diagnostics_section_experiments), rows)
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

/** A raw value at two places, the same in every locale: the report is read by developers. */
private fun twoPlaces(value: Float): String = formatAndroidStyle("%.2f", arrayOf(value))

/** A GL capability range as `min–max`, or [absent] when the driver declined to report one. */
private fun rangeText(
    range: RendererInfo.Range?,
    absent: String,
): String = range?.let { formatAndroidStyle("%.1f–%.1f", arrayOf(it.min, it.max)) } ?: absent

@Composable
private fun sensorText(row: SensorRow): String =
    when (row) {
        is SensorRow.Absent -> stringResource(Res.string.diagnostics_sensor_absent)
        is SensorRow.Present -> row.reading?.values?.joinToString(",") { twoPlaces(it) } ?: ""
    }

/**
 * " (52 Hz, 0.1s ago)" for a present, reporting sensor — nothing for an absent one or one that
 * hasn't delivered a first event yet, since [sensorText] already says so.
 */
@Composable
private fun rateSuffix(
    row: SensorRow,
    rate: SensorRateInfo?,
): String {
    if (row !is SensorRow.Present || rate == null) return ""
    val staleForMillis = rate.staleForMillis ?: return ""
    return " " +
        formattedStringResource(
            Res.string.diagnostics_sensor_rate_format,
            rate.hz,
            staleForMillis / 1000.0,
        )
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
            formattedStringResource(
                Res.string.diagnostics_location_format,
                state.location.latitudeDeg,
                state.location.longitudeDeg,
                state.source.name.lowercase(),
            )
        is LocationState.Unset -> stringResource(Res.string.diagnostics_location_unset)
        is LocationState.Acquiring, is LocationState.AcquiringTimeout ->
            stringResource(Res.string.diagnostics_location_acquiring)
        is LocationState.HardwareUnavailable ->
            stringResource(Res.string.diagnostics_location_hardware_unavailable)
        is LocationState.PermissionDenied, is LocationState.PermissionPermanentlyDenied ->
            stringResource(Res.string.diagnostics_permission_denied)
    }

@Composable
private fun magneticCorrectionText(degrees: Double): String =
    formattedStringResource(
        Res.string.diagnostics_magnetic_correction_format,
        abs(degrees),
        stringResource(
            if (degrees >= 0) Res.string.diagnostics_east else Res.string.diagnostics_west,
        ),
    )

/** v1's `getDegreeInHour`: RA degrees as truncated h/m/s. */
private fun raText(raDeg: Double): String {
    val hours = raDeg / 15.0
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    val s = (((hours - h) * 60 - m) * 60).toInt()
    return "${h}h ${m}m ${s}s"
}

/**
 * "2026-Oct-08 11:32:05". The month is English everywhere, where Android's `DateTimeFormatter`
 * used to name it in the device's language: the report is read by developers.
 */
private val diagnosticsTimeFormat =
    LocalDateTime.Format {
        year()
        char('-')
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char('-')
        dayOfMonth()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

private fun formatTime(
    time: Instant,
    zone: TimeZone,
): String = diagnosticsTimeFormat.format(time.toLocalDateTime(zone))

private fun sensorName(kind: SensorKind): StringResource =
    when (kind) {
        SensorKind.ACCELEROMETER -> Res.string.diagnostics_accelerometer
        SensorKind.MAGNETOMETER -> Res.string.diagnostics_compass
        SensorKind.GYROSCOPE -> Res.string.diagnostics_gyroscope
        SensorKind.ROTATION_VECTOR -> Res.string.diagnostics_rotation
        SensorKind.LIGHT -> Res.string.diagnostics_light_level
    }
