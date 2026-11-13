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
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The circuit breaker, exhaustively.
 *
 * This is the highest-consequence logic in the satellite feature and the cheapest to test, because
 * it is pure. CelesTrak firewalls by IP, and at scale the whole install base looks like one client
 * to them — so a retry bug here would break the feature for every user at once, with no
 * client-side fix. Every branch below is a rule from their published usage policy, not a
 * preference.
 */
class SatelliteFetchPolicyTest {
    private val t0 = Instant.parse("2026-08-15T12:00:00Z")

    @Test
    fun `a fresh install may fetch immediately`() {
        assertThat(SatelliteFetchPolicy.mayFetch(SatelliteFetchState(), t0)).isTrue()
    }

    @Test
    fun `the two-hour minimum query interval is enforced after any attempt`() {
        // CelesTrak's stated minimum, and it applies to *queries*, not to failures — so a
        // successful fetch is just as bound by it as a refused one.
        val afterSuccess =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.Success("body", lastModified = null),
                t0,
            )
        assertThat(SatelliteFetchPolicy.mayFetch(afterSuccess, t0 + 119.minutes)).isFalse()
        assertThat(SatelliteFetchPolicy.mayFetch(afterSuccess, t0 + 121.minutes)).isTrue()
    }

    @Test
    fun `a server refusal opens the circuit for a day, then two, then a week`() {
        // The ladder, climbed one refusal at a time. Deliberately measured in days: being too
        // conservative costs a stale element set, being too eager costs the feature entirely.
        var state = SatelliteFetchState()
        var now = t0

        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.HttpError(503), now)
        assertThat(state.circuitOpenUntil).isEqualTo(now + 24.hours)
        assertThat(state.consecutiveFailures).isEqualTo(1)

        now += 25.hours
        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.HttpError(503), now)
        assertThat(state.circuitOpenUntil).isEqualTo(now + 48.hours)

        now += 49.hours
        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.HttpError(503), now)
        assertThat(state.circuitOpenUntil).isEqualTo(now + 7.days)

        // Capped: a fourth and fiftieth refusal stay at a week rather than growing without bound.
        now += 8.days
        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.HttpError(503), now)
        assertThat(state.circuitOpenUntil).isEqualTo(now + 7.days)
        assertThat(state.consecutiveFailures).isEqualTo(4)
    }

    @Test
    fun `no request is made while the circuit is open`() {
        val open =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.HttpError(500),
                t0,
            )
        assertThat(SatelliteFetchPolicy.mayFetch(open, t0 + 23.hours)).isFalse()
        assertThat(SatelliteFetchPolicy.mayFetch(open, t0 + 25.hours)).isTrue()
    }

    @Test
    fun `301 goes straight to the longest backoff and is reported terminal`() {
        // The policy says 301 means our URL is outdated. Nothing a client can do fixes that, so
        // climbing a ladder towards a week would just be a week of pointless requests first.
        val moved =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.HttpError(301),
                t0,
            )
        assertThat(moved.circuitOpenUntil).isEqualTo(t0 + 7.days)
        assertThat(moved.consecutiveFailures).isEqualTo(1)
        assertThat(SatelliteFetchPolicy.isTerminal(FetchOutcome.HttpError(301))).isTrue()
        assertThat(SatelliteFetchPolicy.isTerminal(FetchOutcome.HttpError(503))).isFalse()
    }

    @Test
    fun `304 Not Modified is a success, not an error`() {
        // The feed is re-issued roughly daily and we poll twice a day, so "nothing changed" is the
        // *common* response. Treating it as an error would open the circuit almost every time.
        val open =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.HttpError(500),
                t0,
            )
        val recovered =
            SatelliteFetchPolicy.afterOutcome(
                open,
                FetchOutcome.NotModified,
                t0 + 25.hours,
            )

        assertThat(recovered.circuitOpenUntil).isNull()
        assertThat(recovered.consecutiveFailures).isEqualTo(0)
        assertThat(recovered.lastSuccess).isEqualTo(t0 + 25.hours)
        assertThat(recovered.lastStatusCode).isEqualTo(304)
    }

    @Test
    fun `a success closes an open circuit and clears the failure count`() {
        var state = SatelliteFetchState()
        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.HttpError(500), t0)
        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.HttpError(500), t0 + 25.hours)
        assertThat(state.consecutiveFailures).isEqualTo(2)

        val recovered =
            SatelliteFetchPolicy.afterOutcome(
                state,
                FetchOutcome.Success("body", lastModified = "Sat, 15 Aug 2026 02:00:00 GMT"),
                t0 + 4.days,
            )
        assertThat(recovered.circuitOpenUntil).isNull()
        assertThat(recovered.consecutiveFailures).isEqualTo(0)
        assertThat(recovered.lastModified).isEqualTo("Sat, 15 Aug 2026 02:00:00 GMT")
    }

    @Test
    fun `being offline does not open the circuit`() {
        // The single most important asymmetry in this file. A transport failure never reached
        // CelesTrak, so it is not evidence about them. Punishing it would mean a user who spent a
        // week in a tent could not fetch for a day after getting signal back — while their element
        // sets are at their most stale.
        val offline =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.TransportFailure(IOException("no route to host")),
                t0,
            )
        assertThat(offline.circuitOpenUntil).isNull()
        assertThat(offline.consecutiveFailures).isEqualTo(0)
        // But it still counts as an attempt, so nothing can hammer in a reconnect loop.
        assertThat(offline.lastAttempt).isEqualTo(t0)
        assertThat(SatelliteFetchPolicy.mayFetch(offline, t0 + 1.hours)).isFalse()
    }

    private fun timeout() =
        FetchOutcome.ServerUnreachable(
            SocketTimeoutException("connect timed out"),
        )

    @Test
    fun `one timeout on a working network leaves the circuit shut`() {
        // One flaky connection must not cost a day. It is still an attempt, so the 2-hour floor
        // holds, and it is remembered as the first strike.
        val once = SatelliteFetchPolicy.afterOutcome(SatelliteFetchState(), timeout(), t0)

        assertThat(once.circuitOpenUntil).isNull()
        assertThat(once.consecutiveFailures).isEqualTo(0)
        assertThat(once.consecutiveTimeouts).isEqualTo(1)
        assertThat(SatelliteFetchPolicy.mayFetch(once, t0 + 1.hours)).isFalse()
    }

    @Test
    fun `a second timeout in a row opens the circuit and climbs the same ladder as a refusal`() {
        // CelesTrak's firewall drops us without answering, so this is what being blocked looks
        // like. Without it, a firewalled install retried every 12 hours forever (#1277).
        var now = t0
        var state = SatelliteFetchPolicy.afterOutcome(SatelliteFetchState(), timeout(), now)

        now += 12.hours
        state = SatelliteFetchPolicy.afterOutcome(state, timeout(), now)
        assertThat(state.circuitOpenUntil).isEqualTo(now + 24.hours)
        assertThat(state.consecutiveFailures).isEqualTo(1)

        // From here every timeout climbs at once: the benefit of the doubt is given once.
        now += 25.hours
        state = SatelliteFetchPolicy.afterOutcome(state, timeout(), now)
        assertThat(state.circuitOpenUntil).isEqualTo(now + 48.hours)

        now += 49.hours
        state = SatelliteFetchPolicy.afterOutcome(state, timeout(), now)
        assertThat(state.circuitOpenUntil).isEqualTo(now + 7.days)
    }

    @Test
    fun `a timeout after a refusal climbs the ladder at once`() {
        // A refusal then silence is a firewall going up, not a flaky connection.
        var state =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.HttpError(429),
                t0,
            )
        state = SatelliteFetchPolicy.afterOutcome(state, timeout(), t0 + 25.hours)

        assertThat(state.circuitOpenUntil).isEqualTo(t0 + 25.hours + 48.hours)
        assertThat(state.consecutiveFailures).isEqualTo(2)
        // Nothing answered, so the last real status stays on the Diagnostics screen.
        assertThat(state.lastStatusCode).isEqualTo(429)
    }

    @Test
    fun `being offline between two timeouts neither counts nor clears the first`() {
        // Offline says nothing about CelesTrak either way. If it cleared the strike, an install
        // that is firewalled and often offline would never back off.
        var state = SatelliteFetchPolicy.afterOutcome(SatelliteFetchState(), timeout(), t0)
        state =
            SatelliteFetchPolicy.afterOutcome(
                state,
                FetchOutcome.TransportFailure(IOException("no route to host")),
                t0 + 12.hours,
            )
        assertThat(state.consecutiveTimeouts).isEqualTo(1)
        assertThat(state.circuitOpenUntil).isNull()

        state = SatelliteFetchPolicy.afterOutcome(state, timeout(), t0 + 24.hours)
        assertThat(state.circuitOpenUntil).isEqualTo(t0 + 24.hours + 24.hours)
    }

    @Test
    fun `a lone strike is forgotten after 72 hours`() {
        // Unrelated timeouts days apart are two flaky evenings, not a firewall.
        val strike = SatelliteFetchPolicy.afterOutcome(SatelliteFetchState(), timeout(), t0)
        assertThat(strike.lastTimeout).isEqualTo(t0)

        val later = SatelliteFetchPolicy.afterOutcome(strike, timeout(), t0 + 73.hours)
        assertThat(later.circuitOpenUntil).isNull()
        // The new timeout is a fresh first strike, dated now.
        assertThat(later.consecutiveTimeouts).isEqualTo(1)
        assertThat(later.lastTimeout).isEqualTo(t0 + 73.hours)

        // And it is live: the next one within the window opens the circuit.
        val opened = SatelliteFetchPolicy.afterOutcome(later, timeout(), t0 + 85.hours)
        assertThat(opened.circuitOpenUntil).isEqualTo(t0 + 85.hours + 24.hours)
    }

    @Test
    fun `a strike exactly 72 hours old still counts`() {
        // Two 12-hour runs that Doze or a standby bucket pushed far apart are still consecutive.
        val strike = SatelliteFetchPolicy.afterOutcome(SatelliteFetchState(), timeout(), t0)
        val second = SatelliteFetchPolicy.afterOutcome(strike, timeout(), t0 + 72.hours)

        assertThat(second.circuitOpenUntil).isEqualTo(t0 + 72.hours + 24.hours)
    }

    @Test
    fun `the expiry never stops a climbing breaker from climbing`() {
        // The week-long rung means timeouts more than 72 hours apart; each must still climb.
        var state =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.HttpError(503),
                t0,
            )
        state = SatelliteFetchPolicy.afterOutcome(state, timeout(), t0 + 8.days)

        assertThat(state.consecutiveFailures).isEqualTo(2)
        assertThat(state.circuitOpenUntil).isEqualTo(t0 + 8.days + 48.hours)
    }

    @Test
    fun `a success between two timeouts starts the count again`() {
        var state = SatelliteFetchPolicy.afterOutcome(SatelliteFetchState(), timeout(), t0)
        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.NotModified, t0 + 12.hours)
        assertThat(state.consecutiveTimeouts).isEqualTo(0)
        assertThat(state.lastTimeout).isNull()

        state = SatelliteFetchPolicy.afterOutcome(state, timeout(), t0 + 24.hours)
        assertThat(state.circuitOpenUntil).isNull()
    }

    @Test
    fun `a firewalled install backs off to a week instead of trying twice a day`() {
        // The 12-hour job against a CelesTrak that never answers, for 30 days. Before #1277 every
        // run reached the network: 60 attempts.
        var state = SatelliteFetchState()
        var attempts = 0
        var now = t0
        while (now < t0 + 30.days) {
            if (SatelliteFetchPolicy.mayFetch(state, now)) {
                attempts++
                state = SatelliteFetchPolicy.afterOutcome(state, timeout(), now)
            }
            now += 12.hours
        }
        assertThat(attempts).isEqualTo(7)
        assertThat(state.circuitOpenUntil).isEqualTo(state.lastAttempt!! + 7.days)
    }

    @Test
    fun `a captive portal's 200 does not open the circuit but is not cached either`() {
        // A hotel login page arrives with a perfectly valid 200. That is not CelesTrak refusing
        // us, so the breaker stays shut; the repository is what declines to cache the body.
        val portal =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.UnusableBody("login page"),
                t0,
            )
        assertThat(portal.circuitOpenUntil).isNull()
        assertThat(portal.consecutiveFailures).isEqualTo(0)
        assertThat(portal.lastSuccess).isNull()
        assertThat(portal.lastAttempt).isEqualTo(t0)
    }

    @Test
    fun `only server refusals are worth reporting to a human`() {
        assertThat(SatelliteFetchPolicy.warrantsReporting(FetchOutcome.HttpError(503))).isTrue()
        assertThat(
            SatelliteFetchPolicy.warrantsReporting(FetchOutcome.TransportFailure(IOException())),
        ).isFalse()
        // A timeout is not a response, so there is no status for anyone to act on.
        assertThat(SatelliteFetchPolicy.warrantsReporting(timeout())).isFalse()
        assertThat(SatelliteFetchPolicy.warrantsReporting(FetchOutcome.NotModified)).isFalse()
        assertThat(
            SatelliteFetchPolicy.warrantsReporting(FetchOutcome.Success("b", null)),
        ).isFalse()
    }

    @Test
    fun `timeUntilFetchAllowed reports the longer of the two constraints`() {
        val refused =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.HttpError(500),
                t0,
            )
        // The circuit dominates the 2-hour interval.
        assertThat(SatelliteFetchPolicy.timeUntilFetchAllowed(refused, t0)).isEqualTo(24.hours)
        assertThat(SatelliteFetchPolicy.timeUntilFetchAllowed(refused, t0 + 25.hours))
            .isEqualTo(kotlin.time.Duration.ZERO)

        val succeeded =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.Success("b", null),
                t0,
            )
        assertThat(SatelliteFetchPolicy.timeUntilFetchAllowed(succeeded, t0)).isEqualTo(2.hours)
    }

    @Test
    fun `a repeated refusal after recovery starts the ladder again`() {
        // Failure count resets on success, so an unrelated outage months later is not treated as
        // the continuation of an old one.
        var state =
            SatelliteFetchPolicy.afterOutcome(
                SatelliteFetchState(),
                FetchOutcome.HttpError(500),
                t0,
            )
        state =
            SatelliteFetchPolicy.afterOutcome(
                state,
                FetchOutcome.Success("b", null),
                t0 + 25.hours,
            )
        state = SatelliteFetchPolicy.afterOutcome(state, FetchOutcome.HttpError(500), t0 + 40.days)
        assertThat(state.circuitOpenUntil).isEqualTo(t0 + 40.days + 24.hours)
    }
}
