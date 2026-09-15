/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
 * One WorkManager job refreshes every Sky Map widget (D75): rise/set and "tonight" content
 * are per-day figures, so a 12-hour period keeps them fresh across midnight and the
 * tonight-rollover without exact alarms. Placement/removal drive scheduling from the
 * receivers; [WidgetGate] re-applies the experiment flags on app start.
 */
object WidgetScheduler {
    private const val PERIODIC_WORK_NAME = "widget_refresh"
    private const val MIDNIGHT_WORK_NAME = "widget_midnight_refresh"

    /** All widget receivers, for instance counting; gating is per-flag in [SkyMapApplication]. */
    private val RECEIVERS =
        listOf(
            MoonWidgetReceiver::class.java,
            TonightWidgetReceiver::class.java,
            CountdownWidgetReceiver::class.java,
        )

    fun ensureScheduled(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(12, TimeUnit.HOURS).build(),
        )
        // A second daily job aligned to shortly after local midnight, so the tonight
        // widget's date and the moon's per-day times roll over promptly — the 12-hour
        // job lands wherever Doze drops it.
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val nextMidnight =
            now
                .toLocalDateTime(zone)
                .date
                .plus(1, DateTimeUnit.DAY)
                .atTime(LocalTime(0, 5))
                .toInstant(zone)
        workManager.enqueueUniquePeriodicWork(
            MIDNIGHT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(24, TimeUnit.HOURS)
                .setInitialDelay((nextMidnight - now).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /** Keeps the jobs iff any widget instance is placed; receivers call this on removal. */
    fun syncSchedule(context: Context) {
        if (RECEIVERS.any { hasInstances(context, it) }) {
            ensureScheduled(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(MIDNIGHT_WORK_NAME)
        }
    }

    /** One immediate refresh if anything is placed — app start's catch-up sweep. */
    fun refreshIfPlaced(context: Context) {
        if (RECEIVERS.any { hasInstances(context, it) }) refreshNow(context)
    }

    /** An immediate one-shot refresh, for clock/date/zone changes. */
    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget_refresh_once",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
        )
    }

    fun hasInstances(
        context: Context,
        receiver: Class<*>,
    ): Boolean =
        AppWidgetManager
            .getInstance(context)
            .getAppWidgetIds(ComponentName(context, receiver))
            .isNotEmpty()
}

/**
 * Every widget shows clock-derived content ("tonight", rise/set, days-until). Of the three
 * clock broadcasts only TIMEZONE_CHANGED still reaches a manifest receiver on recent
 * Android (verified on 16 — it even starts a dead process), and that's the one real users
 * hit: travel and DST. TIME_SET/DATE_CHANGED are kept for older Androids where they still
 * deliver; elsewhere a manual clock change is covered by the app-start catch-up sweep and
 * bounded by the midnight/12-hour jobs.
 */
class WidgetClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        WidgetScheduler.refreshNow(context)
    }
}

/** Recomputes and redraws every placed instance; the flag checks live in `provideGlance`. */
class WidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        MoonWidget().updateAll(applicationContext)
        TonightWidget().updateAll(applicationContext)
        CountdownWidget().updateAll(applicationContext)
        return Result.success()
    }
}

/**
 * Applies a widget experiment flag at app start (D75): disabled removes the receiver from
 * the picker and from pin requests. Already-placed instances stay but render their quiet
 * empty state on the next update. A Remote Config flip lands on the next app start after
 * the fetch.
 */
object WidgetGate {
    fun apply(
        context: Context,
        enabled: Boolean,
        vararg receivers: Class<*>,
    ) {
        for (receiver in receivers) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, receiver),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
