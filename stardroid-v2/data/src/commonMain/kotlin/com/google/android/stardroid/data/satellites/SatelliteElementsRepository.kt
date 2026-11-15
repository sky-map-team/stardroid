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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** Nothing cached. The empty state, with an honest explanation. */
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
    private val targets: List<SatelliteTarget> = SatelliteTarget.entries,
    private val clock: Clock = Clock.System,
) {
    private val completedFetches = MutableStateFlow(0)

    /**
     * Counts fetches actually attempted (not ones the policy skipped), so observers re-read at
     * once instead of waiting for a poll. A StateFlow rather than an event stream: a collector
     * that subscribes late still sees the latest count, so a completion can never fall in the
     * gap before it starts listening. Read [current] / [freshness] for the data itself.
     */
    val fetchCount: StateFlow<Int> = completedFetches.asStateFlow()

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
            tles = Tle.parseOmmCsv(text),
            freshness = freshnessFor(ageDays),
            fetchedAt = writtenAt,
            ageDays = ageDays,
        )
    }

    /**
     * [current]'s [ElementFreshness] without reading or parsing the element-set file — what the
     * Layers sheet's status line actually needs. [current] does the same age computation, but
     * only after a `readElements()` + [Tle.parseOmmCsv] pass whose result callers of this method
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
     * Attempts a refresh **if [SatelliteFetchPolicy] allows one**, returning what happened.
     *
     * The safe path, and the only one anything scheduled may call (the WorkManager job and the iOS
     * background refresh); only the debug Diagnostics screen may force past it. It respects both
     * the circuit breaker and CelesTrak's two-hour minimum interval, reporting
     * [RefreshResult.Skipped] rather than making a request when either forbids it.
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
        val fetched = fetchTargets()
        val failure = fetched.failure
        val body = mergedBody(fetched.bodies)

        // A full or failing filesystem must not escape as an exception: this runs in a background
        // worker, and either way the previous good copy is left on disk.
        val written = body?.let { runCatching { store.writeElements(it.text) } }
        val outcome =
            when {
                failure != null -> failure
                written == null -> FetchOutcome.UnusableBody("no element sets fetched")
                written.isFailure -> FetchOutcome.StorageFailure(written.exceptionOrNull()!!)
                else -> FetchOutcome.Success(body.text)
            }

        var updated = SatelliteFetchPolicy.afterOutcome(state, outcome, now)
        // A partial refresh is not a Success, so the policy leaves the age alone. It is still
        // only as old as its oldest retained row, or brand new if nothing was retained.
        if (failure != null && written?.isSuccess == true) {
            // No recorded age for retained rows (a lost state write) counts as new, not absent.
            val writtenAt = if (body.retainedOld) state.elementsWrittenAt ?: now else now
            updated = updated.copy(elementsWrittenAt = writtenAt)
        }
        runCatching { store.writeState(updated) }
        completedFetches.update { it + 1 }
        return RefreshResult.Completed(outcome, updated, previousState = state)
    }

    private class Fetched(
        val bodies: Map<SatelliteTarget, String>,
        val failure: FetchOutcome?,
    )

    private class MergedBody(
        val text: String,
        /** Whether rows from the previous cache were kept, so the file is no fresher than they were. */
        val retainedOld: Boolean,
    )

    /**
     * One request per target, in order, stopping at the first that is not a success so a failing
     * CelesTrak is asked once, not once per object. A body counts only if it holds that target's
     * element set, so a captive portal's 200 is a failure, not a success.
     *
     * Requests are unconditional: each body is a few hundred bytes, so a 304 would save almost
     * nothing.
     */
    private fun fetchTargets(): Fetched {
        val bodies = LinkedHashMap<SatelliteTarget, String>()
        for (target in targets) {
            // An unchecked throw from a client becomes a transport failure, which does not open
            // the circuit.
            val outcome =
                runCatching { client.fetch(target) }
                    .getOrElse { FetchOutcome.TransportFailure(it) }
            val checked =
                if (outcome is FetchOutcome.Success && !holds(outcome.body, target)) {
                    FetchOutcome.UnusableBody(
                        "no ${target.name} element set in a ${outcome.body.length}-byte body",
                    )
                } else {
                    outcome
                }
            if (checked !is FetchOutcome.Success) return Fetched(bodies, checked)
            bodies[target] = checked.body
        }
        return Fetched(bodies, null)
    }

    private fun holds(
        body: String,
        target: SatelliteTarget,
    ) = Tle.parseOmmCsv(body).any { it.noradId == target.catalogNumber }

    /**
     * The fetched bodies as one CSV (one header, every data row), plus the previous cache's rows
     * for objects that were not fetched this time, so a failure for one object does not discard
     * the other's old elements. Null if nothing was fetched.
     */
    private fun mergedBody(bodies: Map<SatelliteTarget, String>): MergedBody? {
        if (bodies.isEmpty()) return null
        val fetchedIds = bodies.keys.map { it.catalogNumber }.toSet()
        val old = store.readElements()?.let { retainedRows(it, fetchedIds) }
        val parts = bodies.values.toList() + listOfNotNull(old)
        return MergedBody(mergeCsv(parts), retainedOld = old != null)
    }

    private val targetIds = targets.map { it.catalogNumber }.toSet()

    private fun retainedRows(
        oldCsv: String,
        fetchedIds: Set<Int>,
    ): String? {
        val lines = oldCsv.lines().filter { it.isNotBlank() }
        val header = lines.firstOrNull() ?: return null
        val rows =
            lines.drop(1).filter { row ->
                Tle.parseOmmCsv("$header\n$row").firstOrNull()?.noradId
                    ?.let { it !in fetchedIds && it in targetIds } == true
            }
        return if (rows.isEmpty()) null else (listOf(header) + rows).joinToString("\n")
    }

    private fun mergeCsv(bodies: List<String>): String {
        val lines = bodies.map { body -> body.lines().filter { it.isNotBlank() } }
        val header = lines.firstOrNull()?.firstOrNull() ?: return ""
        // A body whose header differs (or that has none) contributes nothing rather than rows the
        // parser would misread under the wrong column names.
        val rows =
            lines.flatMap { if (it.firstOrNull() == header) it.drop(1) else emptyList() }.distinct()
        return (listOf(header) + rows).joinToString("\n", postfix = "\n")
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
