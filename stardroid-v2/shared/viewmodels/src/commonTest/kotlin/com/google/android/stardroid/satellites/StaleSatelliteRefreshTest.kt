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
import com.google.android.stardroid.data.satellites.SatelliteTarget
import com.google.android.stardroid.data.satellites.TextFiles
import com.google.android.stardroid.data.satellites.TleStore
import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    private var outcome: FetchOutcome = FetchOutcome.Success(ISS)

    private val repository =
        SatelliteElementsRepository(
            TleStore(MemoryFiles()),
            CelesTrakClient {
                requests++
                outcome
            },
            targets = listOf(SatelliteTarget.ISS),
            clock = clock,
        )

    private var downloadAllowed = true
    private var downloadAsked = 0
    private val downloadLock = Mutex()

    private suspend fun refresh(offset: Duration = Duration.ZERO) =
        StaleSatelliteRefresh(repository, analytics, offset, clock).refreshIfStale(downloadLock) {
            downloadAsked++
            downloadAllowed
        }

    @Test
    fun anEmptyCacheIsFetched() =
        runTest {
            assertThat(refresh()).isInstanceOf(RefreshResult.Completed::class)
            assertThat(requests).isEqualTo(1)
        }

    @Test
    fun aFreshCacheIsLeftAlone() =
        runTest {
            refresh()
            clock.now = start + 2.days

            assertThat(refresh()).isNull()
            assertThat(requests).isEqualTo(1)
        }

    @Test
    fun aFreshCacheDoesNotAskForTheFlag() =
        runTest {
            refresh()
            clock.now = start + 2.days
            downloadAsked = 0

            refresh()

            assertThat(downloadAsked).isEqualTo(0)
        }

    @Test
    fun aStaleCacheIsNotFetchedWithoutAFreshOnFlag() =
        runTest {
            downloadAllowed = false

            assertThat(refresh()).isNull()
            assertThat(downloadAsked).isEqualTo(1)
            assertThat(requests).isEqualTo(0)
        }

    @Test
    fun theFlagWaitDoesNotHoldTheDownloadLock() =
        runTest {
            downloadLock.lock()
            val pending = launch { refresh() }
            runCurrent()

            // Asked while another download holds the lock, and waits for it only to download.
            assertThat(downloadAsked).isEqualTo(1)
            assertThat(requests).isEqualTo(0)

            downloadLock.unlock()
            pending.join()
            assertThat(requests).isEqualTo(1)
        }

    @Test
    fun cancellingDuringTheFlagWaitMakesNoRequest() =
        runTest {
            val refresher = StaleSatelliteRefresh(repository, analytics, Duration.ZERO, clock)
            val pending =
                launch {
                    refresher.refreshIfStale(downloadLock) {
                        awaitCancellation()
                    }
                }
            runCurrent()

            pending.cancelAndJoin()

            assertThat(requests).isEqualTo(0)
            assertThat(downloadLock.isLocked).isFalse()
        }

    @Test
    fun aDownloadThatRanDuringTheWaitIsNotRepeated() =
        runTest {
            val refresher = StaleSatelliteRefresh(repository, analytics, Duration.ZERO, clock)

            refresher.refreshIfStale(downloadLock) {
                // Another path downloads while this one waits for the flag.
                repository.refresh()
                true
            }

            assertThat(requests).isEqualTo(1)
        }

    @Test
    fun aCachePastThreeDaysIsRefetched() =
        runTest {
            refresh()
            clock.now = start + 3.days + 1.hours

            refresh()

            assertThat(requests).isEqualTo(2)
        }

    @Test
    fun theInstallOffsetPostponesTheRefetch() =
        runTest {
            refresh(offset = 20.hours)
            clock.now = start + 3.days + 1.hours

            assertThat(refresh(offset = 20.hours)).isNull()

            clock.now = start + 3.days + 21.hours
            refresh(offset = 20.hours)

            assertThat(requests).isEqualTo(2)
        }

    @Test
    fun theMinimumIntervalStillApplies() =
        runTest {
            // A failure leaves the cache empty, so it is "stale", but the two-hour minimum holds.
            outcome = FetchOutcome.TransportFailure(Exception("offline"))
            refresh()
            clock.now = start + 1.hours

            assertThat(refresh()).isInstanceOf(RefreshResult.Skipped::class)
            assertThat(requests).isEqualTo(1)
        }

    @Test
    fun aServerRefusalIsReported() =
        runTest {
            outcome = FetchOutcome.HttpError(403)

            refresh()

            assertThat(analytics.events.map { it.name })
                .containsExactly(AnalyticsEvents.SATELLITE_FETCH_FAILED_EVENT)
            assertThat(analytics.events.single().params[AnalyticsEvents.SATELLITE_FETCH_HTTP_CODE])
                .isEqualTo("403")
        }

    @Test
    fun aSuccessThatClosesTheCircuitIsReported() =
        runTest {
            outcome = FetchOutcome.HttpError(403)
            refresh()
            analytics.events.clear()
            outcome = FetchOutcome.Success(ISS)
            // Past the breaker's hold.
            clock.now = start + 3.days

            refresh()

            assertThat(analytics.events.map { it.name })
                .containsExactly(
                    AnalyticsEvents.SATELLITE_FETCH_OK_EVENT,
                    AnalyticsEvents.SATELLITE_CIRCUIT_CLOSED_EVENT,
                )
        }

    private companion object {
        const val ISS =
            "OBJECT_NAME,OBJECT_ID,EPOCH,MEAN_MOTION,ECCENTRICITY," +
                "INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY," +
                "EPHEMERIS_TYPE,CLASSIFICATION_TYPE,NORAD_CAT_ID," +
                "ELEMENT_SET_NO,REV_AT_EPOCH,BSTAR,MEAN_MOTION_DOT," +
                "MEAN_MOTION_DDOT\n" +
                "ISS (ZARYA),1998-067A," +
                "2026-11-14T07:16:30.203040,15.49368089,.000738,51.6324," +
                "277.2306,24.2289,335.9045,0,U,25544,999,59502,.17615E-3," +
                ".9396E-4,0\n"
    }
}
