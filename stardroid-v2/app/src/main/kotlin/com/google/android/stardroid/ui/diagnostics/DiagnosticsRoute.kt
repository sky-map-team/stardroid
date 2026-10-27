/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.stardroid.BuildConfig
import com.google.android.stardroid.FlavorEdges
import com.google.android.stardroid.R
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.satellites.SatelliteDiagnosticsState
import com.google.android.stardroid.satellites.forceSatelliteFetchForDebugging
import com.google.android.stardroid.satellites.readSatelliteDiagnostics
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.diagnostics_android_version
import com.google.android.stardroid.ui.resources.diagnostics_gl_limits
import com.google.android.stardroid.ui.resources.diagnostics_gl_version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android's diagnostics destination: the shared [DiagnosticsScreen] with what only Android has —
 * the device and flavor facts, the report mailed by intent ([DiagnosticsShare]), the process's
 * own logcat ([DiagnosticsLog]), and the satellite data status, with its debug-only fetch.
 */
@Composable
fun DiagnosticsRoute(
    viewModel: DiagnosticsViewModel,
    nightMode: Boolean,
    satellitesEnabled: Boolean,
    experimentConfig: ExperimentConfig,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var satelliteState by remember { mutableStateOf<SatelliteDiagnosticsState?>(null) }
    LaunchedEffect(satellitesEnabled) {
        satelliteState = if (satellitesEnabled) readSatelliteDiagnostics(context) else null
    }
    val platform = remember(context) { androidPlatform(context) }
    DiagnosticsScreen(
        viewModel,
        nightMode = nightMode,
        experimentConfig = experimentConfig,
        platform = platform,
        onBack = onBack,
        onSendReport = { subject, body -> DiagnosticsShare.send(context, subject, body) },
        recentLogLines = { withContext(Dispatchers.IO) { DiagnosticsLog.recentLines() } },
        satelliteSection = satelliteState?.let { satelliteSection(it) },
        onForceSatelliteFetch =
            if (BuildConfig.DEBUG && satelliteState != null) {
                {
                    forceSatelliteFetchForDebugging(context).also {
                        satelliteState = readSatelliteDiagnostics(context)
                    }
                }
            } else {
                null
            },
    )
}

private fun androidPlatform(context: Context): DiagnosticsPlatform {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return DiagnosticsPlatform(
        model = Build.MODEL,
        hardware = Build.HARDWARE,
        osVersionLabel = Res.string.diagnostics_android_version,
        osVersion = "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
        appVersionName = info.versionName.orEmpty(),
        appVersionCode = info.longVersionCode,
        buildLabel = FlavorEdges.FLAVOR_LABEL,
        graphicsVersionLabel = Res.string.diagnostics_gl_version,
        graphicsLimitsLabel = Res.string.diagnostics_gl_limits,
    )
}

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
