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
            completed(FetchOutcome.Success("", null)),
            analytics,
            now,
            alwaysSampled,
        )

        assertThat(analytics.events.map { it.name })
            .containsExactly(AnalyticsEvents.SATELLITE_FETCH_OK_EVENT)
    }

    @Test
    fun aSampledNotModifiedIsCounted() {
        trackSatelliteFetchResult(
            completed(FetchOutcome.NotModified),
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
            completed(FetchOutcome.Success("", null)),
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
}
