/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * A fetch on app open, only when the cached elements are stale, for platforms that cannot run a
 * reliable background job (iOS). Android never fetches on open: app opens are strongly diurnal,
 * so fetching on every one would turn user behaviour straight into request volume against
 * CelesTrak. Staleness is the trigger here instead, and [offset] (chosen once per install, within
 * a day) scatters installs across the day rather than all going stale at the same hour.
 */
class StaleSatelliteRefresh(
    private val repository: SatelliteElementsRepository,
    private val analytics: Analytics,
    private val offset: Duration,
    private val clock: Clock = Clock.System,
) {
    /** Refreshes if stale; returns what happened, or null if the cache was fresh enough. */
    fun refreshIfStale(): RefreshResult? {
        val state = repository.fetchState()
        val lastGood = state.lastSuccess ?: state.elementsWrittenAt
        val now = clock.now()
        if (lastGood != null && now - lastGood < STALE_AFTER + offset) return null
        val result = repository.refresh()
        if (result is RefreshResult.Completed) trackSatelliteFetchResult(result, analytics, now)
        return result
    }

    private companion object {
        val STALE_AFTER = 3.days
    }
}
