/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.content.Context
import android.util.Log
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteFetchPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * What the Diagnostics screen shows about satellite element sets.
 *
 * This exists because CelesTrak's usage policy requires clients to **report non-200 responses to
 * a human**, and this is the human-readable half of that obligation: a user who says "satellites
 * aren't updating" can read the status and code straight off the screen and hand them to us. The
 * analytics events (D96) are the aggregate half.
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

/** Reads the current state. Touches disk, so callers should be off the main thread. */
suspend fun readSatelliteDiagnostics(context: Context): SatelliteDiagnosticsState =
    withContext(Dispatchers.IO) {
        val repository = satelliteEntryPoint(context).satelliteElementsRepository()
        val elements = repository.current()
        val state = repository.fetchState()
        SatelliteDiagnosticsState(
            freshness = elements.freshness,
            ageDays = elements.ageDays,
            satelliteCount = elements.tles.size,
            lastStatusCode = state.lastStatusCode,
            lastSuccess = state.lastSuccess,
            circuitOpenUntil = state.circuitOpenUntil,
            consecutiveFailures = state.consecutiveFailures,
        )
    }

/**
 * Fetches immediately, bypassing the circuit breaker and the two-hour minimum interval.
 *
 * ### Debug builds only.
 *
 * Every call site must be behind `BuildConfig.DEBUG`; the Diagnostics row that reaches this is,
 * and nothing else should call it. See
 * [com.google.android.stardroid.data.satellites.SatelliteElementsRepository.forceRefreshIgnoringPolicy]
 * for why that gate is a build-type gate rather than a hidden gesture.
 *
 * **CelesTrak's two-hour minimum applies to a developer's IP as much as to a user's.** Someone
 * leaning on this button can get the office address firewalled, which breaks testing for
 * everyone — so every use is logged loudly, on purpose.
 */
suspend fun forceSatelliteFetchForDebugging(context: Context): String =
    withContext(Dispatchers.IO) {
        Log.w(
            TAG,
            "Debug force-fetch: bypassing the circuit breaker and the " +
                "${SatelliteFetchPolicy.MINIMUM_QUERY_INTERVAL} minimum query interval. " +
                "CelesTrak's policy applies to this IP too — do not hammer this.",
        )
        val repository = satelliteEntryPoint(context).satelliteElementsRepository()
        when (val result = repository.forceRefreshIgnoringPolicy()) {
            is RefreshResult.Skipped ->
                // forceRefreshIgnoringPolicy never consults the policy, so this branch is
                // unreachable; reported rather than ignored so a future refactor that reintroduces
                // the gate shows up here instead of silently doing nothing.
                "Unexpectedly skipped (retry after ${result.retryAfter})"

            is RefreshResult.Completed -> describe(result.outcome)
        }
    }

private fun describe(outcome: FetchOutcome): String =
    when (outcome) {
        is FetchOutcome.Success -> "OK — ${outcome.body.lineSequence().count()} lines"
        FetchOutcome.NotModified -> "304 Not Modified — cache is current"
        is FetchOutcome.HttpError -> "HTTP ${outcome.statusCode} — circuit opened"
        is FetchOutcome.TransportFailure -> "No response — ${outcome.cause.message}"
        is FetchOutcome.UnusableBody -> "Unusable body — ${outcome.reason}"
        is FetchOutcome.StorageFailure -> "Could not save — ${outcome.cause.message}"
    }

private const val TAG = "SatelliteRefresh"
