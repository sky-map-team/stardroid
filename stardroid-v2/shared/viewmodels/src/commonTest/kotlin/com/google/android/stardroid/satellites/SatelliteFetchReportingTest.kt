/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.FakeAnalytics
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteFetchState
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

class SatelliteFetchReportingTest {
    private val now = Instant.parse("2026-11-12T12:00:00Z")
    private val analytics = FakeAnalytics()

    // nextInt(until) is overridden directly: the default rejection-samples nextBits and would
    // spin forever on a constant source.
    private val alwaysSampled =
        object : Random() {
            override fun nextBits(bitCount: Int) = 0

            override fun nextInt(until: Int) = 0
        }

    private val neverSampled =
        object : Random() {
            override fun nextBits(bitCount: Int) = 0

            override fun nextInt(until: Int) = until - 1
        }

    private fun completed(outcome: FetchOutcome) =
        RefreshResult.Completed(outcome, SatelliteFetchState(), SatelliteFetchState())

    @Test
    fun aSampledSuccessIsCounted() {
        trackSatelliteFetchResult(
            completed(FetchOutcome.Success("")),
            analytics,
            now,
            alwaysSampled,
        )

        assertThat(analytics.events.map { it.name })
            .containsExactly(AnalyticsEvents.SATELLITE_FETCH_OK_EVENT)
    }

    @Test
    fun anUnsampledSuccessIsNotCounted() {
        trackSatelliteFetchResult(
            completed(FetchOutcome.Success("")),
            analytics,
            now,
            neverSampled,
        )

        assertThat(analytics.events.size).isEqualTo(0)
    }

    @Test
    fun aFailureIsNeverCountedAsASuccess() {
        trackSatelliteFetchResult(
            completed(FetchOutcome.HttpError(503)),
            analytics,
            now,
            alwaysSampled,
        )

        assertThat(analytics.events.map { it.name })
            .containsExactly(AnalyticsEvents.SATELLITE_FETCH_FAILED_EVENT)
    }

    @Test
    fun aTimeoutThatOpenedTheCircuitIsReportedAsATimeout() {
        val open = SatelliteFetchState(circuitOpenUntil = now + 24.hours)
        trackSatelliteFetchResult(
            RefreshResult.Completed(
                FetchOutcome.ServerUnreachable(Exception()),
                open,
                SatelliteFetchState(),
            ),
            analytics,
            now,
            neverSampled,
        )

        assertThat(analytics.events.map { it.name })
            .containsExactly(AnalyticsEvents.SATELLITE_FETCH_FAILED_EVENT)
        assertThat(analytics.events.single().params[AnalyticsEvents.SATELLITE_FETCH_HTTP_CODE])
            .isEqualTo(AnalyticsEvents.SATELLITE_FETCH_TIMEOUT_CODE)
    }

    @Test
    fun aFirstTimeoutThatLeftTheCircuitShutIsNotReported() {
        trackSatelliteFetchResult(
            completed(FetchOutcome.ServerUnreachable(Exception())),
            analytics,
            now,
            neverSampled,
        )

        assertThat(analytics.events.size).isEqualTo(0)
    }

    @Test
    fun beingOfflineIsNeverReported() {
        trackSatelliteFetchResult(
            RefreshResult.Completed(
                FetchOutcome.TransportFailure(Exception()),
                SatelliteFetchState(circuitOpenUntil = now + 24.hours),
                SatelliteFetchState(circuitOpenUntil = now + 24.hours),
            ),
            analytics,
            now,
            neverSampled,
        )

        assertThat(analytics.events.size).isEqualTo(0)
    }
}
