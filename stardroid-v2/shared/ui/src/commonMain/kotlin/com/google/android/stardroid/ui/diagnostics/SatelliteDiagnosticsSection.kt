/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import androidx.compose.runtime.Composable
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.satellites.SatelliteDiagnosticsState
import com.google.android.stardroid.ui.common.formattedStringResource
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.diagnostics_satellite_circuit_open
import com.google.android.stardroid.ui.resources.diagnostics_satellite_circuit_open_format
import com.google.android.stardroid.ui.resources.diagnostics_satellite_data
import com.google.android.stardroid.ui.resources.diagnostics_satellite_data_format
import com.google.android.stardroid.ui.resources.diagnostics_satellite_data_none
import com.google.android.stardroid.ui.resources.diagnostics_satellite_last_fetch
import com.google.android.stardroid.ui.resources.diagnostics_satellite_never_fetched
import com.google.android.stardroid.ui.resources.diagnostics_section_satellites
import org.jetbrains.compose.resources.stringResource

@Composable
fun satelliteDiagnosticsSection(state: SatelliteDiagnosticsState): DiagnosticsSection {
    val rows = mutableListOf<DiagnosticsRow>()
    rows +=
        DiagnosticsRow(
            stringResource(Res.string.diagnostics_satellite_data),
            if (state.freshness == ElementFreshness.ABSENT || state.ageDays == null) {
                stringResource(Res.string.diagnostics_satellite_data_none)
            } else {
                formattedStringResource(
                    Res.string.diagnostics_satellite_data_format,
                    state.satelliteCount,
                    state.ageDays!!,
                )
            },
        )
    rows +=
        DiagnosticsRow(
            stringResource(Res.string.diagnostics_satellite_last_fetch),
            state.lastStatusCode?.let { code ->
                state.lastSuccess?.let { "$code at $it" } ?: "$code"
            } ?: stringResource(Res.string.diagnostics_satellite_never_fetched),
        )
    state.circuitOpenUntil?.let { until ->
        rows +=
            DiagnosticsRow(
                stringResource(Res.string.diagnostics_satellite_circuit_open),
                formattedStringResource(
                    Res.string.diagnostics_satellite_circuit_open_format,
                    until.toString(),
                    state.consecutiveFailures,
                ),
            )
    }
    return DiagnosticsSection(stringResource(Res.string.diagnostics_section_satellites), rows)
}
