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

private var sharedGraph: IosAppGraph? = null

/**
 * The process's one [IosAppGraph], built on first use: a second graph would open a second
 * DataStore on the same file. Main thread only.
 */
internal fun sharedAppGraph(
    analytics: Analytics,
    flags: FlagSource,
    notificationCenter: LocalNotificationCenter,
): IosAppGraph =
    sharedGraph ?: IosAppGraph(analytics, flags, notificationCenter).also { sharedGraph = it }

/** A running background refresh, for the system's expiration handler to cancel. */
class SatelliteBackgroundRefresh internal constructor(
    private val job: Job,
) {
    fun cancel() = job.cancel()
}

/**
 * The body of the `BGAppRefreshTask` (Swift schedules it), run on the main thread once the flags
 * are readable. `scheduleNext`: ask for another slot only while downloads or notifications need
 * one (#1272). [onDone] may be called on any thread.
 */
fun refreshSatellitesInBackground(
    analytics: Analytics,
    flags: FlagSource,
    notificationCenter: LocalNotificationCenter,
    onDone: (completed: Boolean, scheduleNext: Boolean) -> Unit,
): SatelliteBackgroundRefresh {
    val job = sharedAppGraph(analytics, flags, notificationCenter).startBackgroundRefresh(onDone)
    return SatelliteBackgroundRefresh(job)
}

/**
 * Whether a background refresh has anything to do: satellites may download, or notifications need
 * replanning. Asked on leaving the foreground so Swift schedules only then (#1278 finding 5).
 * [onResult] runs on the main thread, and never if the app has not built its graph yet, as when
 * it is left before the flags were read.
 */
fun backgroundRefreshWanted(onResult: (wanted: Boolean) -> Unit) {
    sharedGraph?.checkBackgroundRefreshWanted(onResult)
}
