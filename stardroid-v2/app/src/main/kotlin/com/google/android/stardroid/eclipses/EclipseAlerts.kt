/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.eclipses

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.stardroid.R
import com.google.android.stardroid.astronomy.LunarEclipseCircumstances
import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.nextLunarEclipse
import com.google.android.stardroid.events.moonAboveHorizonBetween
import com.google.android.stardroid.layers.LayerParameter
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.notifications.SkyNotifier
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.ui.MainActivity
import com.google.android.stardroid.widget.MoonWidget
import com.google.android.stardroid.widget.widgetEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The lunar-eclipse reminder (D106): a daily planner/one-time-poster pair over WorkManager,
 * following D77's shape rather than D103's `AlarmManager` one.
 *
 * Eclipse contact times are known days to months in advance and last hours once under way, so
 * none of D103's Doze-precision argument for `AlarmManager` applies here — a WorkManager job that
 * slips by a few minutes is a non-event. What *does* apply is D77's own reason for a daily planner
 * rather than one job armed on discovery: a job set months ahead is unreliable across reboots and
 * app updates, and a widely-spaced periodic check costs nothing while an eclipse isn't imminent.
 */
object EclipseAlertScheduler {
    private const val PLAN_WORK = "eclipse_alert_plan"
    private const val PLAN_ONCE_WORK = "eclipse_alert_plan_once"
    private const val POST_WORK = "eclipse_alert_post"
    internal const val TAG = "EclipseAlerts"

    /** How far ahead of the eclipse's visible start to notify. */
    internal val LEAD_TIME = 30.minutes

    /**
     * How far ahead the planner is willing to arm the poster. Comfortably wider than the
     * [PLAN_INTERVAL_HOURS] daily cadence, so a slightly late planner run (device asleep,
     * Doze-deferred) never misses the window entirely — it just arms on the very next run
     * instead of the theoretically-earliest one.
     */
    internal val PLAN_HORIZON = 30.hours

    private const val PLAN_INTERVAL_HOURS = 24L

    /** Called whenever the eclipse-alerts opt-in turns on. */
    fun ensureScheduled(context: Context) {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        // 09:00 local: well clear of the D77 13:00 planner, and any eclipse whose alert point
        // falls later that same day is still comfortably inside PLAN_HORIZON when this runs.
        var nextPlan = now.toLocalDateTime(zone).date.atTime(LocalTime(9, 0)).toInstant(zone)
        if (nextPlan <= now) nextPlan = nextPlan.plus(1, DateTimeUnit.DAY, zone)
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PLAN_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EclipseAlertPlannerWorker>(
                PLAN_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setInitialDelay((nextPlan - now).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build(),
        )
        // Turning the toggle on shouldn't wait until tomorrow's 09:00 if an eclipse is close.
        workManager.enqueueUniqueWork(
            PLAN_ONCE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EclipseAlertPlannerWorker>().build(),
        )
    }

    /** Called when the opt-in turns off. */
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
            OneTimeWorkRequestBuilder<EclipseAlertPosterWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /** [circumstances]' visible start: the umbral contact for a partial/total eclipse, else P1. */
    internal fun alertTime(circumstances: LunarEclipseCircumstances): Instant =
        circumstances.umbralBegin ?: circumstances.penumbralBegin ?: circumstances.greatestEclipse
}

/**
 * Checks whether the next eclipse's alert point falls within [EclipseAlertScheduler.PLAN_HORIZON]
 * and, if so and it is actually visible from the saved location, arms the poster.
 *
 * Deliberately does not compute or cache anything about *which* eclipse it armed for: the poster
 * recomputes fresh at fire time (below), the same "recompute at post time" shape
 * `NotificationPosterWorker` already uses, so a slow or delayed run never posts about stale
 * circumstances.
 */
class EclipseAlertPlannerWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val entryPoint = widgetEntryPoint(applicationContext)
        if (!entryPoint.experimentConfig().isEnabled(
                Experiment.NOTIFICATIONS,
            )
        ) {
            return Result.success()
        }
        val settings = entryPoint.settings()
        val wanted =
            settings
                .layerParameter(
                    SolarSystemLayer.LAYER_ID,
                    LayerParameter.ECLIPSE_ALERTS,
                    LayerParameter.ECLIPSE_ALERTS_PARAMETER.defaultValue,
                ).first()
                .toBoolean()
        if (!wanted) return Result.success()

        val now = Clock.System.now()
        val eclipse = nextLunarEclipse(now, MeeusEphemeris) ?: return Result.success()
        val endsAt = eclipse.penumbralEnd ?: eclipse.greatestEclipse
        if (now >= endsAt) return Result.success()

        val location = settings.savedLocation.first()
        if (location != null && !visibleFrom(eclipse, location)) {
            Log.i(EclipseAlertScheduler.TAG, "Next eclipse not visible from saved location")
            return Result.success()
        }

        // Never earlier than now: an eclipse already under way when the planner runs still gets
        // an (immediate) alert rather than none, as long as it hasn't fully ended (checked above).
        val fireAt =
            maxOf(EclipseAlertScheduler.alertTime(eclipse) - EclipseAlertScheduler.LEAD_TIME, now)
        if (fireAt - now > EclipseAlertScheduler.PLAN_HORIZON) {
            Log.i(EclipseAlertScheduler.TAG, "Next eclipse too far out to arm yet ($fireAt)")
            return Result.success()
        }
        val postAt = maxOf(fireAt, now + 1.minutes)
        Log.i(EclipseAlertScheduler.TAG, "Arming eclipse alert for $postAt")
        EclipseAlertScheduler.armPoster(applicationContext, (postAt - now).inWholeMilliseconds)
        return Result.success()
    }
}

/** Recomputes and re-checks the opt-in at fire time, then posts if the eclipse hasn't ended. */
class EclipseAlertPosterWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val entryPoint = widgetEntryPoint(applicationContext)
        if (!entryPoint.experimentConfig().isEnabled(
                Experiment.NOTIFICATIONS,
            )
        ) {
            return Result.success()
        }
        val settings = entryPoint.settings()
        val wanted =
            settings
                .layerParameter(
                    SolarSystemLayer.LAYER_ID,
                    LayerParameter.ECLIPSE_ALERTS,
                    LayerParameter.ECLIPSE_ALERTS_PARAMETER.defaultValue,
                ).first()
                .toBoolean()
        if (!wanted) return Result.success()

        val now = Clock.System.now()
        val eclipse = nextLunarEclipse(now, MeeusEphemeris) ?: return Result.success()
        val endsAt = eclipse.penumbralEnd ?: eclipse.greatestEclipse
        if (now >= endsAt) {
            Log.i(EclipseAlertScheduler.TAG, "Eclipse already ended by fire time; staying quiet")
            return Result.success()
        }
        EclipseNotifier.post(applicationContext, eclipse)
        return Result.success()
    }
}

private fun visibleFrom(
    circumstances: LunarEclipseCircumstances,
    location: LatLong,
): Boolean {
    val start = circumstances.penumbralBegin ?: circumstances.greatestEclipse
    val end = circumstances.penumbralEnd ?: circumstances.greatestEclipse
    return moonAboveHorizonBetween(start, end, location)
}

/** The lunar-eclipse notification, on its own channel (D106). */
object EclipseNotifier {
    const val CHANNEL_ECLIPSES = "lunar_eclipses"
    private const val NOTIFICATION_ID = 73

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ECLIPSES,
                context.getString(R.string.channel_eclipses_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_eclipses_description) },
        )
    }

    fun post(
        context: Context,
        circumstances: LunarEclipseCircumstances,
    ) {
        if (!SkyNotifier.canPost(context, CHANNEL_ECLIPSES)) return
        ensureChannel(context)
        val typeRes =
            when (circumstances.type) {
                LunarEclipseType.TOTAL -> R.string.tonight_lunar_eclipse_total
                LunarEclipseType.PARTIAL -> R.string.tonight_lunar_eclipse_partial
                LunarEclipseType.PENUMBRAL -> R.string.tonight_lunar_eclipse_penumbral
                // nextLunarEclipse never returns a NONE-type result; nothing to post for one.
                LunarEclipseType.NONE -> return
            }
        val time =
            DateFormat
                .getTimeInstance(DateFormat.SHORT)
                .format(Date(circumstances.greatestEclipse.toEpochMilliseconds()))
        val contentIntent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(MoonWidget.EXTRA_SHOW_OBJECT_ID, MoonWidget.MOON_OBJECT_ID)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ECLIPSES)
                .setSmallIcon(R.drawable.ic_stat_skymap)
                .setColor(0xFFFFC107.toInt())
                .setContentTitle(
                    context.getString(R.string.notif_eclipse_title, context.getString(typeRes)),
                ).setContentText(context.getString(R.string.notif_eclipse_text, time))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        2,
                        contentIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post; the alert stays silent.
        }
    }
}
