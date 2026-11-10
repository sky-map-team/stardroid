/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import kotlinx.datetime.Instant

/**
 * What the Diagnostics screen shows about satellite element sets. CelesTrak's usage policy
 * requires clients to report non-200 responses to a human; this is that half (the analytics events
 * are the aggregate half), so "satellites aren't updating" can be read off the screen.
 */
data class SatelliteDiagnosticsState(
    val freshness: ElementFreshness,
    val ageDays: Double?,
    val satelliteCount: Int,
    val lastStatusCode: Int?,
    val lastSuccess: Instant?,
    val circuitOpenUntil: Instant?,
    val consecutiveFailures: Int,
)

/** Touches disk, so call off the main thread. */
fun SatelliteElementsRepository.diagnostics(): SatelliteDiagnosticsState {
    val elements = current()
    val state = fetchState()
    return SatelliteDiagnosticsState(
        freshness = elements.freshness,
        ageDays = elements.ageDays,
        satelliteCount = elements.tles.size,
        lastStatusCode = state.lastStatusCode,
        lastSuccess = state.lastSuccess,
        circuitOpenUntil = state.circuitOpenUntil,
        consecutiveFailures = state.consecutiveFailures,
    )
}
