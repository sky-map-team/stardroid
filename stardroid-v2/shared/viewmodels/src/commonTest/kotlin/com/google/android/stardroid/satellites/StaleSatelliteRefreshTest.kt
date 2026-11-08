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
import com.google.android.stardroid.data.satellites.CelesTrakClient
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import com.google.android.stardroid.data.satellites.TextFiles
import com.google.android.stardroid.data.satellites.TleStore
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class StaleSatelliteRefreshTest {
    private class MemoryFiles : TextFiles {
        private val files = mutableMapOf<String, String>()

        override fun read(name: String) = files[name]

        override fun replace(
            name: String,
            text: String,
        ) {
            files[name] = text
        }
    }

    private class MutableClock(
        var now: Instant,
    ) : Clock {
        override fun now() = now
    }

    private val start = Instant.parse("2026-11-07T12:00:00Z")
    private val clock = MutableClock(start)
    private val analytics = FakeAnalytics()
    private var requests = 0
    private var outcome: FetchOutcome = FetchOutcome.Success(ISS, null)

    private val repository =
        SatelliteElementsRepository(
            TleStore(MemoryFiles()),
            CelesTrakClient { _, _ ->
                requests++
                outcome
            },
            clock = clock,
        )

    private fun refresh(offset: Duration = Duration.ZERO) =
        StaleSatelliteRefresh(repository, analytics, offset, clock).refreshIfStale()

    @Test
    fun anEmptyCacheIsFetched() {
        assertThat(refresh()).isInstanceOf(RefreshResult.Completed::class)
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun aFreshCacheIsLeftAlone() {
        refresh()
        clock.now = start + 2.days

        assertThat(refresh()).isNull()
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun aCachePastThreeDaysIsRefetched() {
        refresh()
        clock.now = start + 3.days + 1.hours

        refresh()

        assertThat(requests).isEqualTo(2)
    }

    @Test
    fun theInstallOffsetPostponesTheRefetch() {
        refresh(offset = 20.hours)
        clock.now = start + 3.days + 1.hours

        assertThat(refresh(offset = 20.hours)).isNull()

        clock.now = start + 3.days + 21.hours
        refresh(offset = 20.hours)

        assertThat(requests).isEqualTo(2)
    }

    @Test
    fun theMinimumIntervalStillApplies() {
        // A failure leaves the cache empty, so it is "stale", but the two-hour minimum holds.
        outcome = FetchOutcome.TransportFailure(Exception("offline"))
        refresh()
        clock.now = start + 1.hours

        assertThat(refresh()).isInstanceOf(RefreshResult.Skipped::class)
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun aServerRefusalIsReported() {
        outcome = FetchOutcome.HttpError(403)

        refresh()

        assertThat(analytics.events.map { it.name })
            .containsExactly(AnalyticsEvents.SATELLITE_FETCH_FAILED_EVENT)
        assertThat(analytics.events.single().params[AnalyticsEvents.SATELLITE_FETCH_HTTP_CODE])
            .isEqualTo("403")
    }

    @Test
    fun aSuccessThatClosesTheCircuitIsReported() {
        outcome = FetchOutcome.HttpError(403)
        refresh()
        analytics.events.clear()
        outcome = FetchOutcome.Success(ISS, null)
        // Past the breaker's hold.
        clock.now = start + 3.days

        refresh()

        assertThat(analytics.events.map { it.name })
            .containsExactly(AnalyticsEvents.SATELLITE_CIRCUIT_CLOSED_EVENT)
    }

    private companion object {
        const val ISS =
            "ISS (ZARYA)\n" +
                "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993\n" +
                "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882\n"
    }
}
