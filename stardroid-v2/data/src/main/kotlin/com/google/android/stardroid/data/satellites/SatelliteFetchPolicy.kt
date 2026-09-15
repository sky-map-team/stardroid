/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The outcome of one attempt to fetch element sets, in the terms the breaker cares about.
 *
 * The distinction that matters is [FetchOutcome.TransportFailure] versus
 * [FetchOutcome.HttpError]: one never reached CelesTrak, and the other is CelesTrak telling us
 * something. Collapsing them — the natural thing for a generic HTTP layer to do — is what turns a
 * server problem into a retry storm.
 */
sealed interface FetchOutcome {
    /** Element sets were returned. */
    data class Success(
        val body: String,
        /** The response's `Last-Modified`, to send back as `If-Modified-Since` next time. */
        val lastModified: String?,
    ) : FetchOutcome

    /** `304`: our cached copy is current. A success, not an error. */
    data object NotModified : FetchOutcome

    /**
     * The server responded, and not with 200 or 304. **A policy event, never a transient one.**
     */
    data class HttpError(val statusCode: Int) : FetchOutcome

    /**
     * The request never reached CelesTrak — no connectivity, DNS failure, timeout, TLS error.
     * Legitimately retryable, because it says nothing about the server's willingness to serve us.
     */
    data class TransportFailure(val cause: Throwable) : FetchOutcome

    /**
     * A `200` whose body contained no usable element sets.
     *
     * Almost always a captive portal or an intercepting proxy answering on CelesTrak's behalf,
     * not CelesTrak serving rubbish — so it is deliberately **not** an [HttpError] and does not
     * open the circuit. It is still not a success: the cache must not be overwritten with it.
     */
    data class UnusableBody(val reason: String) : FetchOutcome

    /**
     * Element sets arrived but could not be written to disk — a full or failing filesystem.
     *
     * Nothing to do with CelesTrak, so like [TransportFailure] it must not open the circuit. It is
     * a separate case only so the failure is visible rather than crashing a background worker.
     */
    data class StorageFailure(val cause: Throwable) : FetchOutcome
}

/**
 * Everything the fetcher remembers between runs: enough to honour CelesTrak's minimum interval,
 * to keep a circuit open across process death, and to send a conditional request.
 */
data class SatelliteFetchState(
    val consecutiveFailures: Int = 0,
    /** While this is in the future, no request may be made. */
    val circuitOpenUntil: Instant? = null,
    val lastAttempt: Instant? = null,
    val lastSuccess: Instant? = null,
    /**
     * When the cached element bytes were last actually replaced — a [FetchOutcome.Success] only,
     * unlike [lastSuccess] which also advances on a [FetchOutcome.NotModified]. This is what
     * element-set age must be measured against: a 304 confirms the cache is still current but
     * writes nothing, so it must not reset the age clock.
     */
    val elementsWrittenAt: Instant? = null,
    /** The last successful response's `Last-Modified` header, verbatim. */
    val lastModified: String? = null,
    /** The most recent non-200 status, retained so a human can be shown it in Diagnostics. */
    val lastStatusCode: Int? = null,
) {
    val circuitIsOpen: Boolean get() = circuitOpenUntil != null
}

/**
 * When a fetch is allowed, and what a response does to that permission.
 *
 * **This is the part of the feature that protects the whole install base, and it deliberately
 * inverts normal retry instincts.** CelesTrak's usage policy is binding, not advisory: clients
 * must stop querying on any non-200 and report to a human, and the stated consequence of ignoring
 * that is the IP going to a firewall.
 *
 * Users do not share one IP, so a single misbehaving device would mostly firewall itself. Two
 * things make a bug here collective rather than individual anyway:
 *
 *  - **Carrier-grade NAT.** Mobile users leave through a small number of carrier egress addresses,
 *    so per-device traffic aggregates into a handful of IPs. A retry loop shipped to production
 *    would make one carrier's whole subscriber base look like a single abusive client, and
 *    firewalling it takes out every Sky Map user on that network at once.
 *  - **The User-Agent names us.** It has to, so their operator can make contact before resorting
 *    to a firewall — but it also means aggregate misbehaviour is attributable to Sky Map as a
 *    project, and can be blocked as such, whatever the source addresses are.
 *
 * Either way the failure is one no client-side fix can reach: already-shipped versions keep
 * behaving the way they were built to, and only a new release changes that.
 *
 * That asymmetry — cheap to be too conservative, catastrophic to be too eager — is why the backoff
 * below is measured in days rather than seconds, and why this is pure, side-effect-free logic that
 * can be exhaustively tested rather than behaviour tangled into a network call.
 *
 * See docs/design/satellite-tracking.md §"The circuit breaker".
 */
object SatelliteFetchPolicy {
    /**
     * CelesTrak's stated minimum interval between GP queries. Binding.
     *
     * Our normal schedule is 12 hours, comfortably clear of it and matching the real re-issue
     * cadence, but this floor is enforced independently so that no scheduling bug — or a
     * developer's force-fetch — can breach the policy.
     */
    val MINIMUM_QUERY_INTERVAL: Duration = 2.hours

    /**
     * Backoff after consecutive refusals: a day, then two, then a week, capped.
     *
     * Far more conservative than typical retry policy and intentionally so; see the object KDoc.
     */
    private val BACKOFF_LADDER = listOf(24.hours, 48.hours, 7.days)

    /**
     * `301` means the URL we are using is outdated, which the policy says explicitly. No amount of
     * retrying fixes a moved endpoint, so it goes straight to the longest backoff instead of
     * climbing the ladder — and it is the one case that warrants shouting in the log, because only
     * a new release can actually resolve it.
     */
    private val MOVED_PERMANENTLY_BACKOFF: Duration = 7.days

    /**
     * Whether a request may be made at [now].
     *
     * False while the circuit is open, and false again inside [MINIMUM_QUERY_INTERVAL] of the last
     * attempt — the second condition applying even after a success, since the policy limit is on
     * queries, not on failures.
     */
    fun mayFetch(
        state: SatelliteFetchState,
        now: Instant,
    ): Boolean {
        state.circuitOpenUntil?.let { if (now < it) return false }
        state.lastAttempt?.let { if (now < it + MINIMUM_QUERY_INTERVAL) return false }
        return true
    }

    /** How long until [mayFetch] would return true, or [Duration.ZERO] if it already does. */
    fun timeUntilFetchAllowed(
        state: SatelliteFetchState,
        now: Instant,
    ): Duration {
        val untilCircuitCloses = state.circuitOpenUntil?.minus(now) ?: Duration.ZERO
        val untilIntervalElapses =
            state.lastAttempt?.plus(MINIMUM_QUERY_INTERVAL)?.minus(now) ?: Duration.ZERO
        return maxOf(untilCircuitCloses, untilIntervalElapses, Duration.ZERO)
    }

    /**
     * The state after [outcome] arrives at [now].
     *
     * A success or a 304 closes the circuit and clears the failure count. An
     * [FetchOutcome.HttpError] opens it. A [FetchOutcome.TransportFailure] does **neither**: it
     * never reached CelesTrak, so it is not evidence about them, and punishing it would leave a
     * user who spent a week offline unable to fetch for a week after reconnecting.
     */
    fun afterOutcome(
        state: SatelliteFetchState,
        outcome: FetchOutcome,
        now: Instant,
    ): SatelliteFetchState {
        val attempted = state.copy(lastAttempt = now)
        return when (outcome) {
            is FetchOutcome.Success ->
                attempted.copy(
                    consecutiveFailures = 0,
                    circuitOpenUntil = null,
                    lastSuccess = now,
                    elementsWrittenAt = now,
                    lastModified = outcome.lastModified,
                    lastStatusCode = 200,
                )

            FetchOutcome.NotModified ->
                // Our copy is current, so the cache is untouched but the timestamps advance:
                // treating this as an error would open the circuit every time nothing changed,
                // which is the common case for a feed re-issued only daily.
                attempted.copy(
                    consecutiveFailures = 0,
                    circuitOpenUntil = null,
                    lastSuccess = now,
                    lastStatusCode = 304,
                )

            is FetchOutcome.HttpError -> {
                val failures = state.consecutiveFailures + 1
                val backoff =
                    if (outcome.statusCode == HTTP_MOVED_PERMANENTLY) {
                        MOVED_PERMANENTLY_BACKOFF
                    } else {
                        BACKOFF_LADDER[(failures - 1).coerceAtMost(BACKOFF_LADDER.lastIndex)]
                    }
                attempted.copy(
                    consecutiveFailures = failures,
                    circuitOpenUntil = now + backoff,
                    lastStatusCode = outcome.statusCode,
                )
            }

            // Both record the attempt — so the 2-hour floor still applies and nothing can hammer —
            // but neither opens the circuit, because neither is CelesTrak refusing us. Punishing
            // them would mean a user who spent a week offline, or an evening behind a hotel
            // captive portal, could not fetch for a day after getting real connectivity back.
            is FetchOutcome.TransportFailure -> attempted
            is FetchOutcome.UnusableBody -> attempted
            is FetchOutcome.StorageFailure -> attempted
        }
    }

    /**
     * Whether [outcome] should be reported to a human, as CelesTrak's policy requires of us.
     *
     * Only server refusals qualify. Being offline is not something anyone needs to hear about.
     */
    fun warrantsReporting(outcome: FetchOutcome): Boolean = outcome is FetchOutcome.HttpError

    /** True for the one status that no retry can ever fix and that needs a new release. */
    fun isTerminal(outcome: FetchOutcome): Boolean =
        outcome is FetchOutcome.HttpError && outcome.statusCode == HTTP_MOVED_PERMANENTLY

    private const val HTTP_MOVED_PERMANENTLY = 301
}
