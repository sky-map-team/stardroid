/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.ios

import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.nextLunarEclipse
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.satellites.SatelliteIds
import com.google.android.stardroid.satellites.passesWithin
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.settings.eclipseAlertsEnabled
import com.google.android.stardroid.settings.passAlertsEnabled
import com.google.android.stardroid.settings.showerAlertsEnabled
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.surfaces.AlertPreferences
import com.google.android.stardroid.surfaces.NotificationPlanAhead
import com.google.android.stardroid.surfaces.NotificationText
import com.google.android.stardroid.surfaces.ScheduledNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.coroutines.resume

/**
 * Keeps the system's pending local notifications in step with the user's settings. iOS cannot run
 * code when an alert is due, so this plans the next few nights ahead ([NotificationPlanAhead])
 * and has [center] replace what was pending. Planning is idempotent: replan whenever an input
 * changes. It never shows the permission prompt itself.
 */
internal class IosNotificationScheduler(
    private val center: LocalNotificationCenter,
    private val settings: Settings,
    private val experiments: ExperimentConfig,
    private val catalog: suspend () -> CatalogRepository,
    private val locale: StateFlow<LocaleSpec>,
    private val satellites: IosSatellites?,
) {
    // Also what makes Swift's remove-then-add in replaceAll safe: replans never overlap.
    private val lock = Mutex()

    val preferences: Flow<AlertPreferences> =
        combine(
            settings.showerAlertsEnabled,
            settings.tonightDigestEnabled,
            settings.passAlertsEnabled,
            settings.eclipseAlertsEnabled,
        ) { showers, digest, passes, eclipses ->
            AlertPreferences(showers, digest, passes && satellites != null, eclipses)
        }

    suspend fun replan() =
        lock.withLock {
            val preferences = preferences.first()
            val allowed =
                experiments.isEnabled(Experiment.NOTIFICATIONS) &&
                    preferences.any &&
                    center.currentAuthorization() == NotificationAuthorization.AUTHORIZED
            if (!allowed) {
                log("nothing scheduled (flag, a kind on and permission are all needed)")
                center.replaceAllNotifications(emptyList())
                return@withLock
            }
            val now = Clock.System.now()
            val location = settings.savedLocation.first()
            val passes = passesFor(preferences, location, now)
            // Off the main thread: the eclipse search and three nights' sky are not cheap.
            val plan =
                withContext(Dispatchers.Default) {
                    val formatter = timeFormatter()
                    NotificationPlanAhead(
                        words = NotificationText(loadIosTextSource()) { formatTime(formatter, it) },
                        satelliteObjectId = { SatelliteIds.idFor(it).value },
                    ).plan(
                        NotificationPlanAhead.Inputs(
                            now = now,
                            zone = TimeZone.currentSystemDefault(),
                            location = location,
                            showers = catalog().meteorShowers(locale.value).first(),
                            passes = passes,
                            eclipse =
                                if (preferences.eclipses) {
                                    nextLunarEclipse(
                                        now,
                                        MeeusEphemeris,
                                    )
                                } else {
                                    null
                                },
                            preferences = preferences,
                        ),
                    )
                }
            log("scheduled ${plan.size}: " + plan.joinToString { "${it.id}@${it.fireAt}" })
            center.replaceAllNotifications(plan.map { it.toLocal() })
        }

    /** The passes the pass alerts and the digest's pass row draw on; none with satellites off. */
    private suspend fun passesFor(
        preferences: AlertPreferences,
        location: LatLong?,
        now: Instant,
    ): List<SatellitePass> {
        if (satellites == null || location == null) return emptyList()
        if (!preferences.passes && !preferences.digest) return emptyList()
        if (!LayerRegistry.layerEnabled(
                settings,
                SatelliteLayer.LAYER_ID,
            ).first()
        ) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            passesWithin(
                satellites.repository.current(),
                location,
                now,
                NotificationPlanAhead.PASS_HORIZON,
            )
        }
    }

    // Stdout reaches the device console (devicectl --console), as the search log does.
    private fun log(message: String) = println("NotificationScheduler: $message")

    // One per replan: formatters are costly to make, and the locale can change between replans.
    private fun timeFormatter() =
        NSDateFormatter().apply {
            timeStyle = NSDateFormatterShortStyle
            dateStyle = NSDateFormatterNoStyle
        }

    private fun formatTime(
        formatter: NSDateFormatter,
        time: Instant,
    ): String {
        return formatter.stringFromDate(
            NSDate.dateWithTimeIntervalSince1970(time.toEpochMilliseconds() / MILLIS_PER_SECOND),
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}

private fun ScheduledNotification.toLocal() =
    LocalNotification(id, threadId, fireAt.toEpochMilliseconds(), title, body, objectId)

internal suspend fun LocalNotificationCenter.currentAuthorization(): NotificationAuthorization =
    suspendCancellableCoroutine { continuation -> authorization { continuation.resume(it) } }

internal suspend fun LocalNotificationCenter.replaceAllNotifications(
    notifications: List<LocalNotification>,
) = suspendCancellableCoroutine { continuation ->
    replaceAll(notifications) { continuation.resume(Unit) }
}
