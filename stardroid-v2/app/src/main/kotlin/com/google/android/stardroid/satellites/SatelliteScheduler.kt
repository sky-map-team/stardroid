/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Schedules the periodic CelesTrak fetch (D92).
 *
 * Mirrors [com.google.android.stardroid.widget.WidgetScheduler] — `enqueueUniquePeriodicWork`,
 * `KEEP`, a 12-hour period — with three deliberate departures, each of which exists to keep Sky
 * Map's aggregate traffic away from CelesTrak's firewall:
 *
 *  - **A network constraint.** Nothing else in the repo sets constraints, and this job must, so it
 *    waits for connectivity rather than failing and burning a scheduled run.
 *  - **Up to an hour of random initial delay.** Installs are already scattered by install time,
 *    but a staged Play rollout reaching millions of devices in a coordinated window is a real herd
 *    source. The jitter costs nothing — the data is re-issued daily and we poll twice a day.
 *  - **Scheduled only while the feature is on and consented to.** Cancelled otherwise, so a user
 *    who turns satellites off stops making requests immediately rather than at the next flag read.
 *
 * **Nothing here fetches on app open**, and nothing should. That is the most important of the
 * three herd defences: app opens are strongly diurnally correlated across a timezone, so opening
 * the app converts user behaviour directly into request volume.
 */
object SatelliteScheduler {
    private const val WORK_NAME = "satellite_element_refresh"

    /** The 12-hour period from D92: comfortably inside the 2-hour policy minimum, and matching
     * the roughly daily cadence at which element sets are actually re-issued. */
    private const val PERIOD_HOURS = 12L

    /** Upper bound on the random initial delay. */
    private const val MAX_JITTER_MINUTES = 60L

    /**
     * Ensures the periodic fetch is scheduled.
     *
     * `KEEP` means an existing schedule — including its already-drawn jitter — survives, so
     * repeated calls across app starts do not reset the clock and turn every launch into a fetch.
     */
    fun ensureScheduled(
        context: Context,
        random: Random = Random.Default,
    ) {
        val request =
            PeriodicWorkRequestBuilder<SatelliteRefreshWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).setInitialDelay(
                    random.nextLong(MAX_JITTER_MINUTES + 1),
                    TimeUnit.MINUTES,
                ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Stops fetching — the experiment flag went off, or the user withdrew network consent. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Applies the two gates: the feature must be enabled *and* the user must have consented to
     * the network request. Either off means no scheduled traffic at all.
     */
    fun syncSchedule(
        context: Context,
        featureEnabled: Boolean,
        dataConsented: Boolean,
    ) {
        if (featureEnabled && dataConsented) ensureScheduled(context) else cancel(context)
    }
}
