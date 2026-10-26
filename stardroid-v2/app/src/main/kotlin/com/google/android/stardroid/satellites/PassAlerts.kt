/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.stardroid.astronomy.SatellitePass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The time-critical pass alert (D92 phase 4c) — deliberately not D77's nightly digest.
 *
 * ### Why this cannot reuse D77's scheduler
 *
 * `NotificationScheduler.armPoster` uses WorkManager's `setInitialDelay`, which makes **no
 * timeliness guarantee**: under Doze, delivery can slip well past the target. For a
 * "tonight's highlights" post at sunset + 30 min a 20-minute slip is invisible. For a
 * six-minute lead on a six-minute event, the same slip means the notification arrives *after the
 * pass is over* — strictly worse than sending nothing, because the user walks outside to an empty
 * sky. This is the single most important implementation note in the feature.
 *
 * So: [AlarmManager.setAndAllowWhileIdle], which wakes the device in Doze and needs no special
 * permission. It is not exact — it can fire a few minutes late — which is precisely why the lead
 * time absorbs the imprecision.
 *
 * `setExactAndAllowWhileIdle` was rejected: on Android 12+ it needs `SCHEDULE_EXACT_ALARM`, which
 * Play restricts to apps whose core function is alarms. Sky Map would not qualify, so most
 * installs would land on this fallback anyway and the guard would be pure cost. `setAlarmClock`
 * is exempt from Doze and needs no permission, but puts an alarm icon in the status bar — the
 * wrong signal entirely for a sky event.
 */
object PassAlerts {
    /**
     * How far ahead of the pass to fire.
     *
     * **Do not tighten this to the six minutes the design's copy talks about.**
     * [AlarmManager.setAndAllowWhileIdle] is inexact by design and can slip several minutes under
     * Doze; the extra lead is what keeps a late fire landing *before* the pass rather than during
     * or after it. Six minutes is the experience being aimed at; ten is what reliably delivers it.
     */
    val LEAD_TIME: Duration = 10.minutes

    /**
     * Passes closer than this are not worth arming — the alarm would fire at or after the pass.
     */
    private val MINIMUM_NOTICE: Duration = 2.minutes

    private const val ACTION_PASS_ALERT = "com.google.android.stardroid.PASS_ALERT"
    private const val EXTRA_NORAD_ID = "norad_id"
    private const val EXTRA_START_MILLIS = "start_millis"
    private const val EXTRA_END_MILLIS = "end_millis"
    private const val EXTRA_AT_RISE = "at_rise"
    private const val REQUEST_CODE = 4242
    private const val RISE_REQUEST_CODE = 4243
    private const val TAG = "PassAlerts"

    /**
     * Arms an alarm for the **best pass of the night**, replacing any already armed.
     *
     * At most one alert per night, chosen by peak magnitude. The ISS can make two or three visible
     * passes in a good evening, and alerting on each is exactly the pattern that gets an app
     * silenced; the brightest is the one worth going outside for. This is naturally
     * self-limiting anyway — visible passes cluster into multi-day seasons separated by multi-week
     * gaps, so even one per night is not a nightly notification.
     */
    fun armBestPass(
        context: Context,
        passes: List<SatellitePass>,
        now: Instant = Clock.System.now(),
    ) {
        cancel(context)
        val best =
            passes
                .filter { it.start - now > LEAD_TIME + MINIMUM_NOTICE }
                .minByOrNull { it.peakMagnitude }
                ?: return

        val fireAt = best.start - LEAD_TIME
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireAt.toEpochMilliseconds(),
            pendingIntent(context, best),
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            best.start.toEpochMilliseconds(),
            pendingIntent(context, best, atRise = true),
        )
        Log.i(TAG, "Armed pass alert for ${best.satelliteName} at $fireAt (pass at ${best.start})")
    }

    /** Disarms any pending alerts — the opt-in went off, or the layer did. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        existingIntent(context, atRise = false)?.let(alarmManager::cancel)
        existingIntent(context, atRise = true)?.let(alarmManager::cancel)
    }

    private fun pendingIntent(
        context: Context,
        pass: SatellitePass,
        atRise: Boolean = false,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (atRise) RISE_REQUEST_CODE else REQUEST_CODE,
            Intent(context, PassAlertReceiver::class.java).apply {
                action = ACTION_PASS_ALERT
                putExtra(EXTRA_NORAD_ID, pass.noradId)
                putExtra(EXTRA_START_MILLIS, pass.start.toEpochMilliseconds())
                putExtra(EXTRA_END_MILLIS, pass.end.toEpochMilliseconds())
                putExtra(EXTRA_AT_RISE, atRise)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun existingIntent(
        context: Context,
        atRise: Boolean,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            if (atRise) RISE_REQUEST_CODE else REQUEST_CODE,
            Intent(context, PassAlertReceiver::class.java).apply { action = ACTION_PASS_ALERT },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    /** True once the pass has begun; a late alert would send the user to an empty sky. */
    internal fun isStale(
        passStart: Instant,
        now: Instant,
    ): Boolean = now >= passStart

    /** True once the pass has ended; the at-rise alert is still worth posting until then. */
    internal fun isOver(
        passEnd: Instant,
        now: Instant,
    ): Boolean = now >= passEnd

    internal fun atRiseFrom(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_AT_RISE, false)

    internal fun endTimeFrom(intent: Intent): Instant? =
        intent
            .getLongExtra(EXTRA_END_MILLIS, 0L)
            .takeIf { it > 0L }
            ?.let(Instant::fromEpochMilliseconds)

    internal fun startTimeFrom(intent: Intent): Instant? =
        intent
            .getLongExtra(EXTRA_START_MILLIS, 0L)
            .takeIf { it > 0L }
            ?.let(Instant::fromEpochMilliseconds)

    internal fun noradIdFrom(intent: Intent): Int = intent.getIntExtra(EXTRA_NORAD_ID, 0)
}

/**
 * Posts the pass alert when its alarm fires.
 *
 * Re-checks the opt-in at fire time rather than trusting the armed alarm: a user who turned pass
 * alerts off after one was armed must not be interrupted by it. Also drops the alert if the pass
 * has already begun.
 */
class PassAlertReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val start = PassAlerts.startTimeFrom(intent) ?: return
        val noradId = PassAlerts.noradIdFrom(intent)
        val atRise = PassAlerts.atRiseFrom(intent)
        val end = PassAlerts.endTimeFrom(intent)
        // A BroadcastReceiver's process can be killed the moment onReceive returns, so the
        // opt-in re-check must finish before then — but reading DataStore (disk I/O) is not
        // safe to block the main thread on, especially from a Doze wake-up. goAsync() keeps the
        // process alive past onReceive without blocking it.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!passAlertsEnabled(context)) {
                    Log.i(TAG, "Pass alert fired but the opt-in is off; not posting")
                } else if (atRise) {
                    // Inexact alarms land a little late, so unlike the advance alert this one is
                    // still useful after the nominal start, until the pass is over.
                    if (end == null || PassAlerts.isOver(end, Clock.System.now())) {
                        Log.i(TAG, "Rise alert fired after the pass ended; not posting")
                    } else {
                        SatellitePassNotifier.postRising(context, noradId)
                    }
                } else if (PassAlerts.isStale(start, Clock.System.now())) {
                    // Checked after the DataStore read, which can be slow on a Doze wake-up. The OS
                    // may also have deferred the alarm itself (e.g. OEM battery managers).
                    Log.i(TAG, "Pass alert fired after the pass began ($start); not posting")
                } else {
                    SatellitePassNotifier.post(context, noradId, start)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PassAlerts"
    }
}
