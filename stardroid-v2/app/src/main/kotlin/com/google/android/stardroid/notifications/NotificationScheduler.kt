/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.satellites.tonightSatellitePasses
import com.google.android.stardroid.startup.Experiment
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
import kotlin.time.Duration.Companion.minutes

/**
 * The D77 posting pipeline, enforcing the one-notification-per-day contract: a daily
 * early-afternoon planner computes tonight and, only when there is something to say, arms a
 * poster for 30 minutes after sunset. The shower alert takes the slot on peak nights; the
 * digest otherwise, and only when the night clears the announce threshold — a quiet night
 * posts nothing at all.
 */
object NotificationScheduler {
    private const val PLAN_WORK = "notification_plan"
    private const val PLAN_ONCE_WORK = "notification_plan_once"
    private const val POST_WORK = "notification_post"
    internal const val TAG = "SkyMapNotifications"

    /** Called whenever either notification preference turns on. */
    fun ensureScheduled(context: Context) {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        var nextPlan = now.toLocalDateTime(zone).date.atTime(LocalTime(13, 0)).toInstant(zone)
        if (nextPlan <= now) nextPlan = nextPlan.plus(1, DateTimeUnit.DAY, zone)
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PLAN_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NotificationPlannerWorker>(24, TimeUnit.HOURS)
                .setInitialDelay((nextPlan - now).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build(),
        )
        // Enabling mid-afternoon shouldn't wait for tomorrow's planner: plan once right now.
        workManager.enqueueUniqueWork(
            PLAN_ONCE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NotificationPlannerWorker>().build(),
        )
    }

    /** Called when both notification preferences are off. */
    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PLAN_WORK)
        workManager.cancelUniqueWork(PLAN_ONCE_WORK)
        workManager.cancelUniqueWork(POST_WORK)
    }

    internal fun armPoster(
        context: Context,
        delayMillis: Long,
    ) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            POST_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NotificationPosterWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}

/** Decides whether tonight earns a notification and, if so, arms the sunset+30 poster. */
class NotificationPlannerWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val entryPoint = widgetEntryPoint(applicationContext)
        if (!entryPoint.experimentConfig().isEnabled(Experiment.NOTIFICATIONS)) {
            return Result.success()
        }
        val settings = entryPoint.settings()
        val showerAlerts = settings.showerAlertsEnabled.first()
        val digest = settings.tonightDigestEnabled.first()
        if (!showerAlerts && !digest) return Result.success()
        val location = settings.savedLocation.first()
        val showers =
            entryPoint
                .catalogAccess()
                .repository()
                .meteorShowers(entryPoint.localeSource().current)
                .first()
        val now = Clock.System.now()
        val sky =
            tonightSky(
                now,
                location,
                showers,
                passes = tonightSatellitePasses(applicationContext, location, from = now),
            )
        val wanted =
            (showerAlerts && sky.showerPeakTonight != null) || (digest && sky.worthAnnouncing)
        if (!wanted) {
            Log.i(NotificationScheduler.TAG, "Quiet night — nothing to post")
            return Result.success()
        }
        val postAt = maxOf((sky.sunset ?: now) + 30.minutes, now + 1.minutes)
        Log.i(NotificationScheduler.TAG, "Arming poster for $postAt")
        NotificationScheduler.armPoster(
            applicationContext,
            (postAt - now).inWholeMilliseconds,
        )
        return Result.success()
    }
}

/** Recomputes at post time (data fresher than the plan) and posts tonight's one alert. */
class NotificationPosterWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val entryPoint = widgetEntryPoint(applicationContext)
        if (!entryPoint.experimentConfig().isEnabled(Experiment.NOTIFICATIONS)) {
            return Result.success()
        }
        val settings = entryPoint.settings()
        val location = settings.savedLocation.first()
        val showers =
            entryPoint
                .catalogAccess()
                .repository()
                .meteorShowers(entryPoint.localeSource().current)
                .first()
        val now = Clock.System.now()
        val sky =
            tonightSky(
                now,
                location,
                showers,
                passes = tonightSatellitePasses(applicationContext, location, from = now),
            )
        val peak = sky.showerPeakTonight
        if (settings.showerAlertsEnabled.first() && peak != null) {
            Log.i(NotificationScheduler.TAG, "Posting shower alert: ${peak.shower.name}")
            SkyNotifier.postShowerPeak(applicationContext, peak, location)
        } else if (settings.tonightDigestEnabled.first() && sky.worthAnnouncing) {
            Log.i(NotificationScheduler.TAG, "Posting tonight digest")
            SkyNotifier.postDigest(applicationContext, sky)
        }
        return Result.success()
    }
}
