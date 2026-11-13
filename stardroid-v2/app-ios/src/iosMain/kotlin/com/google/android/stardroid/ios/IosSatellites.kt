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
import com.google.android.stardroid.satellites.SatelliteDiagnosticsState
import com.google.android.stardroid.satellites.StaleSatelliteRefresh
import com.google.android.stardroid.satellites.diagnostics
import com.google.android.stardroid.satellites.trackSatelliteFetchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * iOS can't promise a 12-hour background job the way Android's WorkManager does, so staleness on
 * app open ([StaleSatelliteRefresh]) is the trigger rather than a schedule. Refreshes are
 * serialised: WorkManager does that on Android, here a background refresh could overlap an
 * app-open fetch.
 */
internal class IosSatellites(
    directory: String,
    private val analytics: Analytics,
    client: CelesTrakClient = UrlSessionCelesTrakClient(),
) {
    private val files = DirectoryTextFiles(directory)

    val repository = SatelliteElementsRepository(TleStore(files), client)

    private val staleRefresh = StaleSatelliteRefresh(repository, analytics, installOffset(files))

    private val refreshLock = Mutex()

    /** Reads disk: call off the main thread. */
    fun diagnostics(): SatelliteDiagnosticsState = repository.diagnostics()

    /** Blocks on the network: call off the main thread. */
    suspend fun refreshIfStale() {
        refreshLock.withLock { staleRefresh.refreshIfStale() }
    }

    /** Policy-respecting; the debug force path lives in Diagnostics. */
    suspend fun refreshNow() {
        refreshLock.withLock {
            val result = repository.refresh()
            if (result is RefreshResult.Completed) {
                trackSatelliteFetchResult(result, analytics, Clock.System.now())
            }
        }
    }

    private companion object {
        const val OFFSET_FILE = "fetch_offset_minutes"
        const val MINUTES_PER_DAY = 24 * 60

        /** Chosen once per install and kept, so it scatters installs without wandering. */
        fun installOffset(files: TextFiles): Duration {
            val saved = files.read(OFFSET_FILE)?.trim()?.toIntOrNull()
            val valid = saved?.takeIf { it in 0 until MINUTES_PER_DAY }
            val minutes =
                valid ?: Random.nextInt(MINUTES_PER_DAY).also {
                    runCatching { files.replace(OFFSET_FILE, it.toString()) }
                }
            return minutes.minutes
        }
    }
}
