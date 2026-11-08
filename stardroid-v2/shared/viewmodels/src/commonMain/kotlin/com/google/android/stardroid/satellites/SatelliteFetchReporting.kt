/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteFetchPolicy
import kotlinx.datetime.Instant

/**
 * Reports what CelesTrak's usage policy asks us to report, as the aggregate signal that stands in
 * for logs we cannot see: a server refusal (and whether it opened the circuit), and a success
 * that closed a circuit which had been open. Shared so every platform's fetch reports the same.
 */
fun trackSatelliteFetchResult(
    result: RefreshResult.Completed,
    analytics: Analytics,
    now: Instant,
) {
    val outcome = result.outcome
    if (SatelliteFetchPolicy.warrantsReporting(outcome)) {
        analytics.trackEvent(
            AnalyticsEvents.SATELLITE_FETCH_FAILED_EVENT,
            mapOf(
                AnalyticsEvents.SATELLITE_FETCH_HTTP_CODE to
                    (outcome as FetchOutcome.HttpError).statusCode.toString(),
                AnalyticsEvents.SATELLITE_FETCH_CIRCUIT_OPENED to
                    result.state.circuitIsOpen.toString(),
                AnalyticsEvents.SATELLITE_FETCH_CIRCUIT_OPEN_UNTIL to
                    result.state.circuitOpenUntil.toString(),
            ),
        )
    }
    if (outcome is FetchOutcome.Success || outcome is FetchOutcome.NotModified) {
        // Read from the previous state: the success has already cleared the circuit.
        val previous = result.previousState
        val openedAt = previous.lastAttempt
        if (previous.circuitIsOpen && openedAt != null) {
            analytics.trackEvent(
                AnalyticsEvents.SATELLITE_CIRCUIT_CLOSED_EVENT,
                mapOf(
                    AnalyticsEvents.SATELLITE_CIRCUIT_OPEN_HOURS to
                        (now - openedAt).inWholeHours.toString(),
                ),
            )
        }
    }
}
