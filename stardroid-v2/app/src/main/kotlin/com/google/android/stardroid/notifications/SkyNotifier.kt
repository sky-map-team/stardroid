/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.stardroid.R
import com.google.android.stardroid.astronomy.azimuthDeg
import com.google.android.stardroid.events.DARK_MOON_FRACTION
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.ui.MainActivity
import com.google.android.stardroid.widget.MoonWidget
import com.google.android.stardroid.widget.cardinal
import com.google.android.stardroid.widget.highlightLine
import kotlin.math.roundToInt

/**
 * Builds and posts the two D77 notifications. The frequency contract lives one level up
 * ([NotificationScheduler] posts at most one notification a day, shower alert first); this
 * object only renders and respects the system-level gates.
 */
object SkyNotifier {
    const val CHANNEL_SHOWERS = "meteor_showers"
    const val CHANNEL_DIGEST = "tonight_digest"

    // One id: tonight's notification, whichever kind it is — a repost replaces, never stacks.
    private const val NOTIFICATION_ID = 71

    /** Channels are cheap to re-create (no-op if present) and map 1:1 to the settings rows. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SHOWERS,
                context.getString(R.string.channel_showers_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_showers_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DIGEST,
                context.getString(R.string.channel_digest_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_digest_description) },
        )
    }

    /** The system-side gate: notification permission plus the channel not silenced. */
    fun canPost(
        context: Context,
        channelId: String,
    ): Boolean {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return false
        val channel = compat.getNotificationChannel(channelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * The shower-peak alert (mockup notification 1): when, which way to look, is the moon a
     * problem, what you'll see. "Mute showers" deep-links to the channel's system toggle —
     * every alert names its own off-switch.
     */
    fun postShowerPeak(
        context: Context,
        peak: SkyEvent.ShowerPeak,
        location: LatLong?,
    ) {
        if (!canPost(context, CHANNEL_SHOWERS)) return
        ensureChannels(context)
        val direction =
            location?.let {
                context.getString(
                    R.string.notif_shower_direction,
                    cardinal(context, azimuthDeg(peak.shower.radiant, peak.peakTime, it)),
                )
            }
        val moonLine =
            if (peak.moonIllumination < DARK_MOON_FRACTION) {
                context.getString(R.string.notif_shower_dark_moon, peak.shower.peakZhr)
            } else {
                context.getString(
                    R.string.notif_shower_bright_moon,
                    (peak.moonIllumination * 100).roundToInt(),
                    peak.shower.peakZhr,
                )
            }
        val body = listOfNotNull(direction, moonLine).joinToString(" ")
        val muteIntent =
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_SHOWERS)
        val notification =
            baseBuilder(context, CHANNEL_SHOWERS, peak.shower.id.value)
                .setContentTitle(
                    context.getString(R.string.notif_shower_title, peak.shower.name),
                ).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .addAction(
                    0,
                    context.getString(R.string.notif_action_mute_showers),
                    PendingIntent.getActivity(
                        context,
                        1,
                        muteIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()
        post(context, notification)
    }

    /** The tonight digest (mockup notification 3): the widget's highlight lines, verbatim. */
    fun postDigest(
        context: Context,
        sky: TonightSky,
    ) {
        if (!canPost(context, CHANNEL_DIGEST)) return
        ensureChannels(context)
        val lines = sky.highlights.map { highlightLine(it, context).text }
        if (lines.isEmpty()) return
        val notification =
            baseBuilder(context, CHANNEL_DIGEST, targetId = null)
                .setContentTitle(context.getString(R.string.notif_digest_title))
                .setContentText(lines.first())
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")),
                ).build()
        post(context, notification)
    }

    /** The one guarded `notify` call: permission re-checked at the moment of posting. */
    private fun post(
        context: Context,
        notification: Notification,
    ) {
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
            // Permission revoked between the check and the post; tonight stays silent.
        }
    }

    /** Small icon, brand color, tap-through to the map (optionally onto [targetId]'s card). */
    private fun baseBuilder(
        context: Context,
        channelId: String,
        targetId: String?,
    ): NotificationCompat.Builder {
        val contentIntent =
            Intent(context, MainActivity::class.java).apply {
                targetId?.let { putExtra(MoonWidget.EXTRA_SHOW_OBJECT_ID, it) }
            }
        return NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_skymap)
            .setColor(0xFFFFC107.toInt())
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
    }
}
