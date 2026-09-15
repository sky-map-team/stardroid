/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * The outcome → WorkManager `Result` mapping, pinned as a table.
 *
 * [SatelliteRefreshWorker] cannot be unit-tested directly without a WorkManager test harness and
 * an Android context, so the decision it makes is factored into [satelliteWorkAction] — a pure
 * function — and tested here. That is deliberate rather than a workaround: **this mapping is the
 * one place where an innocuous-looking change re-creates exactly the retry storm the whole circuit
 * breaker exists to prevent**, so it should be verifiable without a device.
 */
class SatelliteWorkerMappingTest {
    @Test
    fun `a server refusal never asks WorkManager to retry`() {
        // The single most important assertion in the satellite feature. WorkManager's retry
        // machinery uses exponential backoff measured in seconds; pointing it at a source that
        // firewalls clients for querying after a non-200 is precisely what CelesTrak's usage
        // policy forbids. The circuit breaker is already holding the door shut, and the 12-hour
        // job will fire again on schedule and no-op against it.
        for (status in listOf(301, 403, 404, 429, 500, 503)) {
            assertThat(satelliteWorkAction(FetchOutcome.HttpError(status)))
                .isEqualTo(SatelliteWorkAction.SUCCEED)
        }
    }

    @Test
    fun `a transport failure is retried, because it never reached CelesTrak`() {
        // No connectivity, DNS, timeout: says nothing about the server's willingness to serve us,
        // and retrying is what any well-behaved client does.
        assertThat(satelliteWorkAction(FetchOutcome.TransportFailure(IOException("offline"))))
            .isEqualTo(SatelliteWorkAction.RETRY)
    }

    @Test
    fun `a storage failure is retried, because it is our disk and not their server`() {
        assertThat(satelliteWorkAction(FetchOutcome.StorageFailure(IOException("no space"))))
            .isEqualTo(SatelliteWorkAction.RETRY)
    }

    @Test
    fun `a captive portal is not retried`() {
        // Retrying would hammer the portal, not CelesTrak, and would not get element sets either
        // way. The next scheduled run picks it up once the user is through the login page.
        assertThat(satelliteWorkAction(FetchOutcome.UnusableBody("login page")))
            .isEqualTo(SatelliteWorkAction.SUCCEED)
    }

    @Test
    fun `success and not-modified both succeed`() {
        assertThat(satelliteWorkAction(FetchOutcome.Success("body", lastModified = null)))
            .isEqualTo(SatelliteWorkAction.SUCCEED)
        assertThat(satelliteWorkAction(FetchOutcome.NotModified))
            .isEqualTo(SatelliteWorkAction.SUCCEED)
    }

    @Test
    fun `nothing ever maps to permanent failure`() {
        // A failed periodic job stops running. That would silently kill the feature with no way
        // back short of a reinstall — and "stop asking" is already expressed by the circuit
        // breaker, in a form that recovers on its own.
        val everyOutcome =
            listOf(
                FetchOutcome.Success("b", null),
                FetchOutcome.NotModified,
                FetchOutcome.HttpError(500),
                FetchOutcome.TransportFailure(IOException()),
                FetchOutcome.UnusableBody("x"),
                FetchOutcome.StorageFailure(IOException()),
            )
        assertThat(everyOutcome.map(::satelliteWorkAction))
            .doesNotContain(SatelliteWorkAction.FAIL)
    }
}
