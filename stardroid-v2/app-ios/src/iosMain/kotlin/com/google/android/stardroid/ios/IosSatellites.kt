/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.data.satellites.CelesTrakClient
import com.google.android.stardroid.data.satellites.DirectoryTextFiles
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import com.google.android.stardroid.data.satellites.TextFiles
import com.google.android.stardroid.data.satellites.TleStore
import com.google.android.stardroid.data.satellites.UrlSessionCelesTrakClient
import com.google.android.stardroid.satellites.StaleSatelliteRefresh
import com.google.android.stardroid.satellites.trackSatelliteFetchResult
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The satellite element sets on iOS: the shared repository over a [UrlSessionCelesTrakClient],
 * refreshed on a user's Refresh tap and, when the cache has gone stale, on app open
 * ([StaleSatelliteRefresh]). iOS can't promise a 12-hour background job the way Android's
 * WorkManager does, so staleness is the trigger rather than a schedule.
 */
internal class IosSatellites(
    directory: String,
    private val analytics: Analytics,
    client: CelesTrakClient = UrlSessionCelesTrakClient(),
) {
    private val files = DirectoryTextFiles(directory)

    val repository = SatelliteElementsRepository(TleStore(files), client)

    private val staleRefresh = StaleSatelliteRefresh(repository, analytics, installOffset(files))

    /** Blocks on the network: call off the main thread. */
    fun refreshIfStale() {
        staleRefresh.refreshIfStale()
    }

    /**
     * The Refresh button: the ordinary policy-respecting refresh, never the debug force path.
     * Returns how long the user must wait when policy refused, or [Duration.ZERO] if it ran.
     */
    fun refreshNow(): Duration =
        when (val result = repository.refresh()) {
            is RefreshResult.Skipped -> result.retryAfter
            is RefreshResult.Completed -> {
                trackSatelliteFetchResult(result, analytics, Clock.System.now())
                Duration.ZERO
            }
        }

    private companion object {
        const val OFFSET_FILE = "fetch_offset_minutes"
        const val MINUTES_PER_DAY = 24 * 60

        /** Chosen once per install and kept, so it scatters installs without wandering. */
        fun installOffset(files: TextFiles): Duration {
            val saved = files.read(OFFSET_FILE)?.trim()?.toIntOrNull()
            val minutes =
                saved ?: Random.nextInt(MINUTES_PER_DAY).also {
                    runCatching { files.replace(OFFSET_FILE, it.toString()) }
                }
            return (minutes / 60.0).hours
        }
    }
}
