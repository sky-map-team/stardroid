/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import com.google.android.stardroid.astronomy.Tle
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * How stale the cached element sets are, and therefore what may honestly be shown.
 *
 * TLE degradation is smooth and well characterised, so the UX can be graded rather than binary.
 * See docs/design/satellite-tracking.md §"Staleness and offline behaviour".
 */
enum class ElementFreshness {
    /** Under 3 days old, error under ~2 km. Say nothing. */
    FRESH,

    /** 3–10 days, error growing to ~15 km. Show passes with a quiet note on the card. */
    AGEING,

    /**
     * Past 10 days. Keep the map layer — roughly right is still pleasant — but **suppress
     * precise pass times**: a confidently wrong "21:47" is worse than admitting the data is old.
     */
    STALE,

    /** Nothing cached. The empty state, with an honest explanation and a Refresh button. */
    ABSENT,
}

/** What the repository knows right now: the parsed elements and how much to trust them. */
data class SatelliteElements(
    val tles: List<Tle>,
    val freshness: ElementFreshness,
    val fetchedAt: Instant?,
    val ageDays: Double?,
) {
    /**
     * True when pass *times* may be shown. The map layer is happy at any freshness but
     * [ElementFreshness.ABSENT].
     */
    val mayShowPassTimes: Boolean
        get() = freshness == ElementFreshness.FRESH || freshness == ElementFreshness.AGEING
}

/**
 * The satellite element sets: cached on disk, refreshed from CelesTrak when policy allows.
 *
 * Reading never touches the network. Two methods do, with deliberately opposite contracts:
 * [refresh] always consults [SatelliteFetchPolicy] first and is what everything scheduled or
 * user-facing calls, while [forceRefreshIgnoringPolicy] bypasses it entirely and must never be
 * reachable outside a debug build.
 */
class SatelliteElementsRepository(
    private val store: TleStore,
    private val client: CelesTrakClient,
    private val group: SatelliteGroup = SatelliteGroup.STATIONS,
    private val clock: Clock = Clock.System,
) {
    /**
     * The cached elements, parsed, with their freshness. Never hits the network, so it is safe on
     * any thread and at any time — including app open, which must *never* trigger a fetch (user
     * behaviour is strongly diurnally correlated, and turning it into request volume is the
     * textbook thundering herd).
     */
    fun current(): SatelliteElements {
        val text = store.readElements()
        // The file's own mtime is not usable here: it is wall-clock time from the OS, not the
        // injected [clock], so it cannot be exercised in a test and would be wrong if the two ever
        // diverge. [SatelliteFetchState.elementsWrittenAt] is stamped from the same clock and, unlike
        // [SatelliteFetchState.lastSuccess], only advances when the bytes actually changed.
        val writtenAt = store.readState().elementsWrittenAt
        if (text == null || writtenAt == null) {
            return SatelliteElements(emptyList(), ElementFreshness.ABSENT, null, null)
        }
        val ageDays = (clock.now() - writtenAt).inWholeSeconds / SECONDS_PER_DAY
        return SatelliteElements(
            tles = Tle.parseCatalog(text),
            freshness = freshnessFor(ageDays),
            fetchedAt = writtenAt,
            ageDays = ageDays,
        )
    }

    /**
     * [current]'s [ElementFreshness] without reading or parsing the element-set file — what the
     * Layers sheet's status line actually needs. [current] does the same age computation, but
     * only after a `readElements()` + [Tle.parseCatalog] pass whose result callers of this method
     * throw away.
     */
    fun freshness(): ElementFreshness {
        val writtenAt = store.readState().elementsWrittenAt ?: return ElementFreshness.ABSENT
        val ageDays = (clock.now() - writtenAt).inWholeSeconds / SECONDS_PER_DAY
        return freshnessFor(ageDays)
    }

    /**
     * The persisted fetch bookkeeping — last status, last success, circuit state.
     *
     * Exposed for the Diagnostics screen, which is the human-readable half of the "report to a
     * human" CelesTrak's usage policy requires: a user who says satellites are not updating can
     * read the status code straight off the screen.
     */
    fun fetchState(): SatelliteFetchState = store.readState()

    /**
     * How long until [refresh] would actually make a request, or [Duration.ZERO] if it would go
     * now.
     *
     * Lets a user-facing Refresh affordance show the wait rather than offering a button that
     * silently does nothing. The policy is not the user's fault and should not look like a bug.
     */
    fun refreshAllowedIn(): Duration =
        SatelliteFetchPolicy.timeUntilFetchAllowed(store.readState(), clock.now())

    /**
     * Attempts a refresh **if [SatelliteFetchPolicy] allows one**, returning what happened.
     *
     * The safe path, and the only one anything scheduled or user-facing may call: the WorkManager
     * job, and the Refresh button on the empty state. It respects both the circuit breaker and
     * CelesTrak's two-hour minimum interval, reporting [RefreshResult.Skipped] rather than making
     * a request when either forbids it.
     *
     * Returns [FetchOutcome] rather than throwing so the caller can act on the distinction that
     * matters: a WorkManager job must return `Result.retry()` only for
     * [FetchOutcome.TransportFailure], and **`Result.success()` for every
     * [FetchOutcome.HttpError]** — letting WorkManager's retry machinery drive requests at
     * CelesTrak is exactly the storm the policy forbids. The 12-hour job simply fires again on
     * schedule and no-ops against the breaker.
     */
    fun refresh(): RefreshResult {
        val now = clock.now()
        val state = store.readState()

        if (!SatelliteFetchPolicy.mayFetch(state, now)) {
            return RefreshResult.Skipped(
                state = state,
                retryAfter = SatelliteFetchPolicy.timeUntilFetchAllowed(state, now),
            )
        }
        return fetchAndStore(state, now)
    }

    /**
     * Fetches **immediately, ignoring the circuit breaker and the minimum query interval**.
     *
     * ### This must never be reachable in a release build.
     *
     * It exists only so a developer can exercise the client, cache and breaker without waiting up
     * to twelve hours or fiddling with the device clock. Callers must gate it on
     * `BuildConfig.DEBUG` — not on a hidden gesture, a long-press easter egg, or anything an
     * ordinary user could find. A discoverable "fetch now" in production is precisely the retry
     * storm the circuit breaker exists to prevent: at scale it converts curiosity into request
     * volume against a source that firewalls.
     *
     * **CelesTrak's two-hour minimum applies to a developer's IP as much as to a user's.** Someone
     * hammering this can get the office address firewalled, which breaks testing for everyone.
     *
     * Deliberately a **separate method** rather than a `force` flag on [refresh]. The two have
     * opposite contracts, and the design (§"Forcing a fetch") is explicit that they must not share
     * an implementation, or the safe one will inherit the unsafe one's behaviour in a later edit.
     */
    fun forceRefreshIgnoringPolicy(): RefreshResult = fetchAndStore(store.readState(), clock.now())

    private fun fetchAndStore(
        state: SatelliteFetchState,
        now: Instant,
    ): RefreshResult {
        // Only accept the payload if it actually parses into something usable: a captive portal's
        // login page arrives with a perfectly good 200, and caching it would replace working
        // elements with nothing.
        // Defence in depth: HttpUrlConnectionCelesTrakClient already converts its own failures into
        // outcomes, but [CelesTrakClient] is an interface and this is the composition point that
        // runs inside a background worker. Nothing an implementation does should be able to take
        // the process down, so an unchecked throw becomes the same transport failure a dropped
        // connection would — which notably does not open the circuit, since our bug is not
        // CelesTrak refusing us.
        val fetched =
            runCatching { client.fetch(group, state.lastModified) }
                .getOrElse { FetchOutcome.TransportFailure(it) }
                .let { raw ->
                    if (raw is FetchOutcome.Success && Tle.parseCatalog(raw.body).isEmpty()) {
                        FetchOutcome.UnusableBody(
                            "no parseable element sets in a ${raw.body.length}-byte body",
                        )
                    } else {
                        raw
                    }
                }

        // A full or failing filesystem must not escape as an exception: this runs in a background
        // worker, and either way the previous good copy is left on disk.
        val outcome =
            if (fetched is FetchOutcome.Success) {
                runCatching { store.writeElements(fetched.body) }
                    .fold(onSuccess = { fetched }, onFailure = { FetchOutcome.StorageFailure(it) })
            } else {
                fetched
            }

        val updated = SatelliteFetchPolicy.afterOutcome(state, outcome, now)
        runCatching { store.writeState(updated) }
        return RefreshResult.Completed(outcome, updated, previousState = state)
    }

    private fun freshnessFor(ageDays: Double): ElementFreshness =
        when {
            ageDays < AGEING_AFTER_DAYS -> ElementFreshness.FRESH
            ageDays < STALE_AFTER_DAYS -> ElementFreshness.AGEING
            else -> ElementFreshness.STALE
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400.0
        val AGEING_AFTER_DAYS = 3.days.inWholeSeconds / SECONDS_PER_DAY
        val STALE_AFTER_DAYS = 10.days.inWholeSeconds / SECONDS_PER_DAY
    }
}

/** What [SatelliteElementsRepository.refresh] did. */
sealed interface RefreshResult {
    /** A request was made; [outcome] says how it went. */
    data class Completed(
        val outcome: FetchOutcome,
        /** The state after this attempt. */
        val state: SatelliteFetchState,
        /**
         * The state this attempt started from.
         *
         * Carried because a success *clears* the circuit, so [state] alone cannot tell a caller
         * that a circuit had been open — which is exactly what the recovery analytics event needs
         * to know.
         */
        val previousState: SatelliteFetchState,
    ) : RefreshResult

    /** No request was made, because the breaker is open or the minimum interval has not elapsed. */
    data class Skipped(
        val state: SatelliteFetchState,
        val retryAfter: kotlin.time.Duration,
    ) : RefreshResult
}
