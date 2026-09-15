/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The store and the repository, against a fake client and a temporary directory.
 *
 * No socket and no device: the client is a one-method interface precisely so this is possible,
 * which is the argument that kept a real HTTP library out of the dependency graph.
 */
class SatelliteElementsRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    private val issElements =
        """
        ISS (ZARYA)
        1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993
        2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882
        """.trimIndent()

    private class FakeClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeClient(
        var outcome: FetchOutcome,
    ) : CelesTrakClient {
        var calls = 0
        var lastIfModifiedSince: String? = null

        override fun fetch(
            group: SatelliteGroup,
            ifModifiedSince: String?,
        ): FetchOutcome {
            calls++
            lastIfModifiedSince = ifModifiedSince
            return outcome
        }
    }

    private fun repository(
        client: FakeClient,
        clock: FakeClock,
    ) = SatelliteElementsRepository(TleStore(tempDir), client, SatelliteGroup.STATIONS, clock)

    @Test
    fun `a successful fetch caches the elements and parses them back`() {
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val client = FakeClient(FetchOutcome.Success(issElements, "Sat, 15 Aug 2026 02:00:00 GMT"))

        val result = repository(client, clock).refresh()
        assertThat(result).isInstanceOf(RefreshResult.Completed::class.java)

        val elements = repository(client, clock).current()
        assertThat(elements.tles.map { it.noradId }).containsExactly(25544)
        assertThat(elements.tles.single().name).isEqualTo("ISS (ZARYA)")
        assertThat(elements.freshness).isEqualTo(ElementFreshness.FRESH)
    }

    @Test
    fun `nothing cached reports an absent state rather than an empty success`() {
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val elements = repository(FakeClient(FetchOutcome.NotModified), clock).current()

        assertThat(elements.freshness).isEqualTo(ElementFreshness.ABSENT)
        assertThat(elements.tles).isEmpty()
        assertThat(elements.fetchedAt).isNull()
        // The empty state must be distinguishable from "fetched, but nothing up there".
        assertThat(elements.mayShowPassTimes).isFalse()
    }

    @Test
    fun `freshness grades with age, and pass times are suppressed past ten days`() {
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val client = FakeClient(FetchOutcome.Success(issElements, null))
        repository(client, clock).refresh()

        fun freshnessAfter(elapsed: Duration): SatelliteElements {
            clock.now = Instant.parse("2026-08-15T12:00:00Z") + elapsed
            return repository(client, clock).current()
        }

        assertThat(freshnessAfter(1.days).freshness).isEqualTo(ElementFreshness.FRESH)
        assertThat(freshnessAfter(5.days).freshness).isEqualTo(ElementFreshness.AGEING)
        assertThat(freshnessAfter(5.days).mayShowPassTimes).isTrue()

        // Past ten days the map layer is still worth drawing, but a confidently wrong "21:47"
        // is worse than admitting the data is old.
        val stale = freshnessAfter(12.days)
        assertThat(stale.freshness).isEqualTo(ElementFreshness.STALE)
        assertThat(stale.mayShowPassTimes).isFalse()
        assertThat(stale.tles).isNotEmpty()
    }

    @Test
    fun `an open circuit skips the request entirely`() {
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val client = FakeClient(FetchOutcome.HttpError(503))

        repository(client, clock).refresh()
        assertThat(client.calls).isEqualTo(1)

        // Same day, circuit open: no socket is opened at all. This is the behaviour that keeps the
        // install base off CelesTrak's firewall.
        clock.now += 3.hours
        val skipped = repository(client, clock).refresh()
        assertThat(client.calls).isEqualTo(1)
        assertThat(skipped).isInstanceOf(RefreshResult.Skipped::class.java)
        assertThat((skipped as RefreshResult.Skipped).retryAfter).isGreaterThan(Duration.ZERO)

        // After the backoff expires it tries again.
        clock.now += 22.hours
        repository(client, clock).refresh()
        assertThat(client.calls).isEqualTo(2)
    }

    @Test
    fun `the circuit survives a new repository instance`() {
        // Process death must not reset the breaker — otherwise every app restart is a fresh
        // request, which is precisely the storm the policy forbids.
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val client = FakeClient(FetchOutcome.HttpError(500))
        repository(client, clock).refresh()

        clock.now += 1.hours
        val afterRestart = repository(FakeClient(FetchOutcome.HttpError(500)), clock)
        assertThat(afterRestart.refresh()).isInstanceOf(RefreshResult.Skipped::class.java)
    }

    @Test
    fun `only the debug force path bypasses the breaker`() {
        // Two affordances with opposite contracts, and deliberately separate methods rather than a
        // flag: refresh() always respects policy, forceRefreshIgnoringPolicy() never does. The
        // design is explicit that they must not share an implementation, or the safe one inherits
        // the unsafe one's behaviour in some later edit.
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val client = FakeClient(FetchOutcome.HttpError(503))
        repository(client, clock).refresh()
        assertThat(client.calls).isEqualTo(1)

        clock.now += 1.hours
        assertThat(
            repository(client, clock).refresh(),
        ).isInstanceOf(RefreshResult.Skipped::class.java)
        assertThat(client.calls).isEqualTo(1)

        repository(client, clock).forceRefreshIgnoringPolicy()
        assertThat(client.calls).isEqualTo(2)
    }

    @Test
    fun `a captive portal's 200 never replaces good cached elements`() {
        // The failure mode this guards: a hotel login page arrives with a valid 200, and caching
        // it would swap working element sets for an HTML document that parses to nothing.
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        repository(FakeClient(FetchOutcome.Success(issElements, null)), clock).refresh()

        clock.now += 13.hours
        val portal = FakeClient(FetchOutcome.Success("<html>Please sign in to WiFi</html>", null))
        val result = repository(portal, clock).refresh() as RefreshResult.Completed

        assertThat(result.outcome).isInstanceOf(FetchOutcome.UnusableBody::class.java)
        // The good elements are still there, and the circuit did not open over someone's hotel.
        assertThat(
            repository(portal, clock).current().tles.map { it.noradId },
        ).containsExactly(25544)
        assertThat(result.state.circuitOpenUntil).isNull()
    }

    @Test
    fun `offline leaves the cache intact and the circuit shut`() {
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        repository(FakeClient(FetchOutcome.Success(issElements, null)), clock).refresh()

        clock.now += 13.hours
        val offline = FakeClient(FetchOutcome.TransportFailure(IOException("airplane mode")))
        val result = repository(offline, clock).refresh() as RefreshResult.Completed

        assertThat(result.state.circuitOpenUntil).isNull()
        assertThat(repository(offline, clock).current().tles).isNotEmpty()
    }

    @Test
    fun `a conditional request is sent once a Last-Modified is known`() {
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val client = FakeClient(FetchOutcome.Success(issElements, "Sat, 15 Aug 2026 02:00:00 GMT"))

        repository(client, clock).refresh()
        assertThat(client.lastIfModifiedSince).isNull()

        clock.now += 13.hours
        repository(client, clock).refresh()
        assertThat(client.lastIfModifiedSince).isEqualTo("Sat, 15 Aug 2026 02:00:00 GMT")
    }

    @Test
    fun `304 refreshes the timestamps without touching the cache`() {
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        repository(
            FakeClient(FetchOutcome.Success(issElements, "Sat, 15 Aug 2026 02:00:00 GMT")),
            clock,
        )
            .refresh()
        val originalFetchedAt =
            repository(
                FakeClient(FetchOutcome.NotModified),
                clock,
            ).current().fetchedAt

        clock.now += 13.hours
        val result =
            repository(
                FakeClient(FetchOutcome.NotModified),
                clock,
            ).refresh() as RefreshResult.Completed

        assertThat(result.state.lastStatusCode).isEqualTo(304)
        assertThat(result.state.lastSuccess).isEqualTo(clock.now)
        // The bytes on disk are untouched, so the element file's own mtime is unchanged.
        assertThat(repository(FakeClient(FetchOutcome.NotModified), clock).current().fetchedAt)
            .isEqualTo(originalFetchedAt)
    }

    @Test
    fun `a truncated write cannot corrupt the cached elements`() {
        // Writes go to a temp file and are renamed, so a kill mid-write leaves the previous good
        // copy in place rather than half a TLE - which would parse as garbage or, worse, as a
        // plausible wrong orbit.
        val store = TleStore(tempDir)
        store.writeElements(issElements)
        assertThat(File(tempDir, "elements.tle.tmp").exists()).isFalse()
        assertThat(store.readElements()).isEqualTo(issElements)
    }

    @Test
    fun `an unreadable state file degrades to never-fetched rather than throwing`() {
        // Conservative direction: the worst case is one extra request after the minimum interval,
        // whereas an app that cannot start because its backoff bookkeeping is unparseable would
        // be worse than the problem it guards against.
        File(tempDir, "fetch-state.txt").writeText("this is not a state file ")
        assertThat(TleStore(tempDir).readState()).isEqualTo(SatelliteFetchState())
    }

    @Test
    fun `fetch state round-trips through disk`() {
        val store = TleStore(tempDir)
        val state =
            SatelliteFetchState(
                consecutiveFailures = 2,
                circuitOpenUntil = Instant.parse("2026-08-17T12:00:00Z"),
                lastAttempt = Instant.parse("2026-08-15T12:00:00Z"),
                lastSuccess = Instant.parse("2026-08-14T12:00:00Z"),
                lastModified = "Sat, 15 Aug 2026 02:00:00 GMT",
                lastStatusCode = 503,
            )
        store.writeState(state)
        assertThat(store.readState()).isEqualTo(state)
    }

    @Test
    fun `a failed write is reported, not thrown, and leaves the old elements intact`() {
        // This runs in a background worker. A full or failing filesystem must surface as an
        // outcome rather than an exception escaping into WorkManager - and because the write is a
        // temp-file rename, the previous good copy survives either way.
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        repository(FakeClient(FetchOutcome.Success(issElements, null)), clock).refresh()

        // Make the elements path un-writable by turning it into a directory the rename cannot
        // replace, which is the closest portable stand-in for a filesystem failure.
        File(tempDir, "elements.tle").delete()
        File(tempDir, "elements.tle").mkdirs()

        clock.now += 13.hours
        val result =
            repository(FakeClient(FetchOutcome.Success(issElements, null)), clock)
                .refresh() as RefreshResult.Completed

        assertThat(result.outcome).isInstanceOf(FetchOutcome.StorageFailure::class.java)
        // A disk problem is not CelesTrak refusing us, so the circuit stays shut.
        assertThat(result.state.circuitOpenUntil).isNull()
    }

    @Test
    fun `an unreadable element file degrades to the empty state rather than throwing`() {
        // current() is called from UI code on any thread, so it has to be total. A directory where
        // the elements file should be is the closest portable stand-in for an unreadable file.
        File(tempDir, "elements.tle").mkdirs()

        val elements =
            repository(
                FakeClient(FetchOutcome.NotModified),
                FakeClock(Instant.parse("2026-08-15T12:00:00Z")),
            )
                .current()

        assertThat(elements.freshness).isEqualTo(ElementFreshness.ABSENT)
        assertThat(elements.tles).isEmpty()
    }

    @Test
    fun `garbage in the element file yields no satellites rather than throwing`() {
        // A half-written or corrupted cache must not take the app down; the parser skips what it
        // cannot read, and an empty catalog is indistinguishable from "nothing up there" to the UI.
        File(tempDir, "elements.tle").writeText("\u0000\u0000 not a TLE at all\nnor is this")

        val elements =
            repository(
                FakeClient(FetchOutcome.NotModified),
                FakeClock(Instant.parse("2026-08-15T12:00:00Z")),
            )
                .current()

        assertThat(elements.tles).isEmpty()
    }

    @Test
    fun `an unchecked exception from the HTTP stack is reported, not propagated`() {
        // SecurityException with no INTERNET permission, and assorted unchecked exceptions from
        // deep in the platform stack, are not IOException. In a background worker they must become
        // an outcome rather than escaping.
        val clock = FakeClock(Instant.parse("2026-08-15T12:00:00Z"))
        val exploding =
            CelesTrakClient {
                    _,
                    _,
                ->
                throw SecurityException("Permission denied: missing INTERNET")
            }
        val repository =
            SatelliteElementsRepository(
                TleStore(tempDir),
                exploding,
                SatelliteGroup.STATIONS,
                clock,
            )

        // Does not throw.
        val result = repository.refresh() as RefreshResult.Completed
        assertThat(result.outcome).isInstanceOf(FetchOutcome.TransportFailure::class.java)
        // Our own bug is not CelesTrak refusing us, so the circuit stays shut.
        assertThat(result.state.circuitOpenUntil).isNull()
    }

    @Test
    fun `the real client turns an unchecked platform failure into a transport failure`() {
        // The other half of the same guard, on HttpUrlConnectionCelesTrakClient itself. A missing
        // INTERNET permission raises SecurityException, which is not an IOException and would
        // otherwise escape into the worker.
        val client =
            HttpUrlConnectionCelesTrakClient(
                openConnection = { throw SecurityException("Permission denied: missing INTERNET") },
            )
        val outcome = client.fetch(SatelliteGroup.STATIONS, ifModifiedSince = null)
        assertThat(outcome).isInstanceOf(FetchOutcome.TransportFailure::class.java)
    }
}
