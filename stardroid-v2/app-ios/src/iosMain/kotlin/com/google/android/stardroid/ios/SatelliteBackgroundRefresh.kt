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
import com.google.android.stardroid.startup.FlagSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private var sharedGraph: IosAppGraph? = null

/**
 * The process's one [IosAppGraph], built on first use: a second graph would open a second
 * DataStore on the same file. Main thread only.
 */
internal fun sharedAppGraph(
    analytics: Analytics,
    flags: FlagSource,
): IosAppGraph = sharedGraph ?: IosAppGraph(analytics, flags).also { sharedGraph = it }

/** A running background refresh, for the system's expiration handler to cancel. */
class SatelliteBackgroundRefresh internal constructor(
    private val job: Job,
) {
    fun cancel() = job.cancel()
}

/**
 * The body of the `BGAppRefreshTask` (Swift schedules it), run on the main thread once the flags
 * are readable. [onDone] gets true if the work ran to the end without being cancelled.
 */
fun refreshSatellitesInBackground(
    analytics: Analytics,
    flags: FlagSource,
    onDone: (completed: Boolean) -> Unit,
): SatelliteBackgroundRefresh {
    val graph = sharedAppGraph(analytics, flags)
    val job =
        graph.backgroundScope.launch {
            graph.refreshSatellitesInBackground()
        }
    job.invokeOnCompletion { onDone(it == null) }
    return SatelliteBackgroundRefresh(job)
}
