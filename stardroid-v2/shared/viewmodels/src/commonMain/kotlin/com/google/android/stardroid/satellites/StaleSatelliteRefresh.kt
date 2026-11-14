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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /**
     * Refreshes if stale and [mayDownload] agrees. Returns null if nothing was asked of CelesTrak.
     *
     * [mayDownload] can wait seconds for a flag fetch, so it runs outside [downloadLock] and
     * never holds up another download. Staleness is checked again under the lock, in case one
     * ran meanwhile.
     */
    suspend fun refreshIfStale(
        downloadLock: Mutex,
        mayDownload: suspend () -> Boolean,
    ): RefreshResult? {
        if (!isStale() || !mayDownload()) return null
        return downloadLock.withLock {
            if (!isStale()) return@withLock null
            // Cancelling the wait is fine; a download once started finishes and is recorded.
            withContext(NonCancellable) {
                val result = repository.refresh()
                if (result is RefreshResult.Completed) {
                    trackSatelliteFetchResult(result, analytics, clock.now())
                }
                result
            }
        }
    }

    private fun isStale(): Boolean {
        val state = repository.fetchState()
        val lastGood = state.lastSuccess ?: state.elementsWrittenAt ?: return true
        return clock.now() - lastGood >= STALE_AFTER + offset
    }

    private companion object {
        val STALE_AFTER = 3.days
    }
}
