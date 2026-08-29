/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.stardroid.R
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.notifications.SkyNotifier
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import java.text.DateFormat
import java.util.Date

/**
 * The pass alert notification, on its **own channel** (D92 phase 4c).
 *
 * `CHANNEL_PASSES` rather than sharing `CHANNEL_SHOWERS`: Android's per-channel controls then let
 * someone keep shower alerts while muting pass alerts, which the existing `canPost` check already
 * respects. Sharing a channel would make muting one mute the other, and these are different
 * enough — one is "something is happening in six weeks", the other "go outside now" — that a user
 * may well want one and not the other.
 *
 * **It does not consume D77's one-notification-per-night slot, and that slot does not suppress
 * it.** A shower alert at sunset + 30 and a pass alert at 21:41 are different messages, at
 * different times, about different things. A deliberate divergence from D77's invariant, justified
 * by time-criticality and recorded so it does not read as an oversight.
 */
object SatellitePassNotifier {
    const val CHANNEL_PASSES = "satellite_passes"

    /** Its own id, so a pass alert never replaces tonight's digest or a shower alert. */
    private const val NOTIFICATION_ID = 72

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PASSES,
                context.getString(R.string.channel_passes_name),
                // HIGH, unlike the other two: this one is time-critical and useless if unnoticed
                // for twenty minutes. It is also rare — at most one a night, in seasons.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_passes_description) },
        )
    }

    fun post(
        context: Context,
        noradId: Int,
        passStart: Instant,
    ) {
        if (!SkyNotifier.canPost(context, CHANNEL_PASSES)) return
        ensureChannel(context)
        val name =
            if (noradId == SatelliteLayer.TIANGONG_NORAD_ID) {
                context.getString(R.string.satellite_name_tiangong)
            } else {
                context.getString(R.string.satellite_name_iss)
            }
        val time =
            DateFormat
                .getTimeInstance(DateFormat.SHORT)
                .format(Date(passStart.toEpochMilliseconds()))

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_PASSES)
                .setSmallIcon(R.drawable.ic_layer_satellites)
                .setContentTitle(context.getString(R.string.notif_pass_title, name))
                .setContentText(context.getString(R.string.notif_pass_text, time))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
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

/** Whether the user has opted in to pass alerts — the layer parameter, not the layer's own state. */
suspend fun passAlertsEnabled(context: Context): Boolean {
    val entryPoint = satelliteEntryPoint(context)
    val settings: Settings = entryPoint.settings()
    if (!settings.layerEnabled(SatelliteLayer.LAYER_ID).first()) return false
    return settings
        .layerParameter(
            SatelliteLayer.LAYER_ID,
            com.google.android.stardroid.layers.LayerParameter.PASS_ALERTS,
            false.toString(),
        ).first()
        .toBoolean()
}
