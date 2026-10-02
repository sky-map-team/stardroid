/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.notifications.SkyNotifier
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.widget.TonightWidget
import com.google.android.stardroid.widget.widgetEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the announcement refresh. The period is 12 hours *because that is the Remote
 * Config client throttle*: the project has ~5M installs, and polling faster would only multiply
 * request volume without delivering anything sooner. Messages are authored a day ahead.
 */
object AnnouncementScheduler {
    private const val REFRESH_WORK = "announcement_refresh"
    private const val DEFERRED_WORK = "announcement_deferred"
    internal const val TAG = "SkyMapAnnouncements"
    internal const val KEY_REFRESH = "refresh"

    // Notifications are held back overnight: a message that becomes due at 3am waits for morning.
    private val QUIET_START = LocalTime(22, 0)
    private val QUIET_END = LocalTime(8, 0)

    /** Follows the experiment flag and the user's opt-out for the process lifetime. */
    fun syncSchedule(
        context: Context,
        enabled: Boolean,
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(REFRESH_WORK)
            workManager.cancelUniqueWork(DEFERRED_WORK)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AnnouncementWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                ).setInputData(Data.Builder().putBoolean(KEY_REFRESH, true).build())
                .build(),
        )
    }

    /** True during the overnight hold-back window. */
    internal fun isQuietHour(local: LocalTime): Boolean = local >= QUIET_START || local < QUIET_END

    /** Re-run (without refetching) once the quiet window ends. */
    internal fun deferUntilMorning(context: Context) {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val local = now.toLocalDateTime(zone)
        var morning = local.date.atTime(QUIET_END).toInstant(zone)
        if (morning <= now) morning = morning.plus(1, DateTimeUnit.DAY, zone)
        WorkManager.getInstance(context).enqueueUniqueWork(
            DEFERRED_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AnnouncementWorker>()
                .setInitialDelay((morning - now).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}

/**
 * Refreshes the payload (periodic runs only), posts any due notification through
 * [AnnouncementPolicy], and repaints the widget so its banner follows the new state.
 */
class AnnouncementWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val ep = widgetEntryPoint(applicationContext)
        if (!ep.experimentConfig().isEnabled(Experiment.ANNOUNCEMENTS) ||
            !ep.settings().announcementsEnabled.first()
        ) {
            return Result.success()
        }
        val source = ep.announcementSource()
        val state = ep.announcementState()
        if (inputData.getBoolean(AnnouncementScheduler.KEY_REFRESH, false)) source.refresh()

        val now = Clock.System.now()
        val active = source.current()
        state.prune(active.map { it.id }.toSet(), now)
        val version = appVersionCode(applicationContext)
        val due =
            AnnouncementPolicy.next(Surface.NOTIFICATION, now, active, state.seen.first(), version)
        if (due != null) {
            if (AnnouncementScheduler.isQuietHour(
                    now.toLocalDateTime(TimeZone.currentSystemDefault()).time,
                )
            ) {
                AnnouncementScheduler.deferUntilMorning(applicationContext)
            } else {
                val text = due.localized(ep.localeSource().current)
                if (text != null &&
                    SkyNotifier.postAnnouncement(applicationContext, text, due.action)
                ) {
                    Log.i(AnnouncementScheduler.TAG, "Posted announcement ${due.id}")
                    state.markShown(due.id, Surface.NOTIFICATION, now)
                    ep.analytics().trackEvent(
                        AnalyticsEvents.ANNOUNCEMENT_SHOWN_EVENT,
                        mapOf(
                            AnalyticsEvents.ANNOUNCEMENT_ID to due.id,
                            AnalyticsEvents.ANNOUNCEMENT_SURFACE to Surface.NOTIFICATION.wire,
                        ),
                    )
                }
            }
        }
        TonightWidget().updateAll(applicationContext)
        return Result.success()
    }
}

/** The running build's versionCode, for each message's `min_version` gate. */
internal fun appVersionCode(context: Context): Long =
    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
