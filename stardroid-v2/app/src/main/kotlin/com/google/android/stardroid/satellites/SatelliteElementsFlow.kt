/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The cached element sets, re-read from disk on a slow tick and whenever a fetch completes.
 *
 * Polling rather than observing the file because the thing being watched changes at most twice a
 * day: the fetch job runs on a 12-hour period, so a five-minute poll is already far finer-grained
 * than the data it is watching, and a `FileObserver` would be machinery earning nothing. The
 * repository's `updates` covers the one case the poll is too slow for: the first fetch landing,
 * or a manual refresh, while the app is open.
 *
 * Deliberately **not** driven by the map clock. The clock ticks for every frame of satellite
 * motion, and re-reading and re-parsing the cache at that rate would turn a once-a-day change into
 * per-frame disk traffic. [SatelliteLayer] combines this slow flow with the fast clock instead.
 */
fun satelliteElementsFlow(
    repository: SatelliteElementsRepository,
    pollInterval: Duration = POLL_INTERVAL,
): Flow<SatelliteElements> =
    refreshTriggers(repository, pollInterval)
        .map { repository.current() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

/** Emits at once, then on every poll tick and every completed fetch. */
private fun refreshTriggers(
    repository: SatelliteElementsRepository,
    pollInterval: Duration,
): Flow<Unit> =
    merge(
        flow {
            while (true) {
                emit(Unit)
                delay(pollInterval)
            }
        },
        repository.updates,
    )

private val POLL_INTERVAL = 5.minutes

/**
 * What the Layers sheet needs to know about satellites: how good the cached data is, and whether
 * asking for more would currently do anything.
 */
data class SatelliteUiStatus(
    val freshness: ElementFreshness,
    /**
     * How long until a user-triggered refresh would actually make a request, or [Duration.ZERO] if
     * it would go now.
     *
     * Carried so the UI can show the wait instead of offering a button that silently no-ops. It is
     * only as fresh as the poll below, which is immaterial: the shortest possible wait is
     * CelesTrak's two-hour minimum, so being a few minutes stale on it never changes what the user
     * is told.
     */
    val refreshAllowedIn: Duration,
)

/** [satelliteElementsFlow]'s data, plus the refresh-availability the Refresh affordance needs. */
fun satelliteUiStatusFlow(
    repository: SatelliteElementsRepository,
    pollInterval: Duration = POLL_INTERVAL,
): Flow<SatelliteUiStatus> =
    refreshTriggers(repository, pollInterval)
        .map {
            SatelliteUiStatus(
                freshness = repository.freshness(),
                refreshAllowedIn = repository.refreshAllowedIn(),
            )
        }.distinctUntilChanged()
        .flowOn(Dispatchers.IO)
