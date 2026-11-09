/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

enum class NotificationKind { SHOWER_PEAK, TONIGHT_DIGEST }

/** One alert due tonight. The text is built by each platform from [showerPeak] or [sky]. */
data class PlannedNotification(
    val kind: NotificationKind,
    val fireAt: Instant,
    val showerPeak: SkyEvent.ShowerPeak?,
    val sky: TonightSky,
)

/**
 * Tonight's one notification (D77): a shower peak takes the slot when one is tonight, otherwise
 * the digest, and a night with nothing worth announcing yields nothing. Both platforms apply
 * these rules, so a quiet night is quiet everywhere.
 */
object NotificationPlanner {
    /** Dark enough to look up, and the notification arrives as the evening begins. */
    val AFTER_SUNSET = 30.minutes

    /** Enabled mid-evening, the alert still fires, a moment from now rather than in the past. */
    val MINIMUM_LEAD = 1.minutes

    fun plan(
        now: Instant,
        sky: TonightSky,
        showerAlerts: Boolean,
        digest: Boolean,
    ): PlannedNotification? {
        val peak = sky.showerPeakTonight
        val kind =
            when {
                showerAlerts && peak != null -> NotificationKind.SHOWER_PEAK
                digest && sky.worthAnnouncing -> NotificationKind.TONIGHT_DIGEST
                else -> return null
            }
        val fireAt = maxOf((sky.sunset ?: now) + AFTER_SUNSET, now + MINIMUM_LEAD)
        return PlannedNotification(
            kind = kind,
            fireAt = fireAt,
            showerPeak = peak.takeIf { kind == NotificationKind.SHOWER_PEAK },
            sky = sky,
        )
    }
}
