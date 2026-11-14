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
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.stardroid.settings.passAlertsEnabled
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.widget.TonightWidgetReceiver
import com.google.android.stardroid.widget.WidgetScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** What a change in the [SatelliteFetchConditions] does to the periodic fetch. */
enum class SatelliteScheduleAction {
    SCHEDULE,
    CANCEL,
    LEAVE,
}

/**
 * Only a session the user sees schedules; any other process start may only cancel (#1272).
 *
 * A background start ignores recent use: a cold start may be about to show the map, and a cancel
 * computed from the previous visit could land after `MainActivity` schedules. The worker cancels
 * once use lapses. See "Who downloads" in satellite-tracking.md.
 */
fun satelliteScheduleAction(
    conditions: SatelliteFetchConditions,
    now: Instant,
    userVisible: Boolean,
): SatelliteScheduleAction =
    when {
        userVisible && conditions.allowFetch(now) -> SatelliteScheduleAction.SCHEDULE
        userVisible || !conditions.switchedOn -> SatelliteScheduleAction.CANCEL
        else -> SatelliteScheduleAction.LEAVE
    }

/**
 * Schedules the periodic CelesTrak fetch (D92).
 *
 * Mirrors [com.google.android.stardroid.widget.WidgetScheduler] — `enqueueUniquePeriodicWork`,
 * `KEEP`, a 12-hour period — with three deliberate departures, each of which exists to keep Sky
 * Map's aggregate traffic away from CelesTrak's firewall:
 *
 *  - **A network constraint.** Nothing else in the repo sets constraints, and this job must, so it
 *    waits for connectivity rather than failing and burning a scheduled run.
 *  - **Up to five minutes of random initial delay.** Installs are already scattered by install
 *    time, but a staged Play rollout or a Remote Config flip reaching many devices in a coordinated
 *    window is a real herd source. Five minutes spreads that without leaving a new user staring at
 *    an empty layer for an hour; the delay also fixes the device's phase for the 12-hour period.
 *  - **Scheduled only for people using the feature**, and only from a session they can see: see
 *    [SatelliteFetchConditions] and [satelliteScheduleAction]. Cancelled as soon as a setting
 *    turns off, and by the worker once use lapses.
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
    private const val MAX_JITTER_MINUTES = 5L

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

    /** Stops fetching: a gate closed (see [SatelliteFetchConditions]). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** While the map is visible. */
    suspend fun followInVisibleSession(context: Context) = follow(context, userVisible = true)

    /** On every process start, including boot and widget updates: never schedules. */
    suspend fun followInAnyProcess(context: Context) = follow(context, userVisible = false)

    private suspend fun follow(
        context: Context,
        userVisible: Boolean,
    ) {
        conditions(context).distinctUntilChanged().collect { conditions ->
            when (satelliteScheduleAction(conditions, Clock.System.now(), userVisible)) {
                SatelliteScheduleAction.SCHEDULE -> ensureScheduled(context)
                SatelliteScheduleAction.CANCEL -> cancel(context)
                SatelliteScheduleAction.LEAVE -> Unit
            }
        }
    }

    /**
     * The gates as this install stands. Widget placement and notification permission are read
     * only when the alert or digest setting changes, so a long-lived collector can miss them; the
     * worker reads fresh ones on every run.
     */
    fun conditions(context: Context): Flow<SatelliteFetchConditions> {
        val entryPoint = satelliteEntryPoint(context)
        val experiments = entryPoint.experimentConfig()
        val settings = entryPoint.settings()
        return satelliteFetchConditions(
            experiments = experiments,
            settings = settings,
            startupRouter = entryPoint.startupRouter(),
            startupState = entryPoint.startupState(),
            backgroundUse =
                combine(
                    settings.passAlertsEnabled,
                    settings.tonightDigestEnabled,
                ) { alerts, digest ->
                    satelliteBackgroundUse(
                        passAlertsOn = alerts,
                        digestOn = digest,
                        notificationsFlagOn = experiments.isEnabled(Experiment.NOTIFICATIONS),
                        notificationsAllowed =
                            NotificationManagerCompat.from(context).areNotificationsEnabled(),
                        tonightWidgetPlaced =
                            WidgetScheduler.hasInstances(
                                context,
                                TonightWidgetReceiver::class.java,
                            ),
                    )
                },
        )
    }
}

/**
 * Whether something shows satellite data while the app is closed: the Tonight widget (the only
 * widget with passes), or pass alerts or the tonight digest. A notification that can't be posted
 * needs no data.
 */
internal fun satelliteBackgroundUse(
    passAlertsOn: Boolean,
    digestOn: Boolean,
    notificationsFlagOn: Boolean,
    notificationsAllowed: Boolean,
    tonightWidgetPlaced: Boolean,
): Boolean =
    tonightWidgetPlaced ||
        ((passAlertsOn || digestOn) && notificationsFlagOn && notificationsAllowed)
