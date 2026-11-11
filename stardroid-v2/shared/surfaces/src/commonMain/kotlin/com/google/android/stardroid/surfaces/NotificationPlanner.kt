/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.LunarEclipseCircumstances
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.events.moonAboveHorizonBetween
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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

    /** A pass closer than this to its alert time is not worth arming: it would fire too late. */
    val PASS_MINIMUM_NOTICE = 2.minutes

    /** How far ahead of a lunar eclipse's visible start to notify. */
    val ECLIPSE_LEAD = 30.minutes

    /**
     * The pass worth an alert: the brightest of those that start far enough ahead for an alert
     * [lead] before them to still land in the future. At most one, because the ISS can make two
     * or three visible passes in an evening and alerting on each gets an app silenced.
     */
    fun alertablePass(
        now: Instant,
        passes: List<SatellitePass>,
        lead: Duration,
    ): SatellitePass? =
        passes
            .filter { it.start - now > lead + PASS_MINIMUM_NOTICE }
            .minByOrNull { it.peakMagnitude }

    /**
     * One pass alert per night over the next [horizon], each for that night's brightest pass.
     * A night runs noon to noon in [zone], so a pass just after midnight belongs to the evening
     * before. The plan-ahead model for platforms that cannot run code when an alert fires.
     */
    fun planPassAlerts(
        now: Instant,
        passes: List<SatellitePass>,
        zone: TimeZone,
        lead: Duration,
        horizon: Duration,
    ): List<PlannedPassAlert> =
        passes
            .filter { it.start - now <= horizon }
            .groupBy { nightOf(it.start, zone) }
            .mapNotNull { (_, tonight) -> alertablePass(now, tonight, lead) }
            .sortedBy { it.start }
            .map { PlannedPassAlert(it, fireAt = it.start - lead) }

    /** The local date of the evening a pass belongs to; a night runs noon to noon. */
    fun nightOf(
        time: Instant,
        zone: TimeZone,
    ): LocalDate = (time - 12.hours).toLocalDateTime(zone).date

    /**
     * The alert for the next lunar [eclipse], or null when there is none worth sending: it is
     * over, or the Moon is not above the horizon at [location] while it lasts. With no known
     * location the alert is sent anyway. [horizon] limits how far ahead to plan, for platforms
     * that arm a job near the time; null plans however far off it is.
     */
    fun planEclipseAlert(
        now: Instant,
        eclipse: LunarEclipseCircumstances?,
        location: LatLong?,
        horizon: Duration? = null,
    ): PlannedEclipseAlert? {
        if (eclipse == null) return null
        val end = eclipse.penumbralEnd ?: eclipse.greatestEclipse
        if (now >= end) return null
        if (location != null &&
            !moonAboveHorizonBetween(
                eclipse.penumbralBegin ?: eclipse.greatestEclipse,
                end,
                location,
            )
        ) {
            return null
        }
        // Under way already: still alert, a moment from now, as long as it has not ended.
        val fireAt = maxOf(eclipseAlertPoint(eclipse) - ECLIPSE_LEAD, now + MINIMUM_LEAD)
        if (horizon != null && fireAt - now > horizon) return null
        return PlannedEclipseAlert(eclipse, fireAt)
    }

    /** The eclipse's visible start: the umbral contact for a partial or total one, else P1. */
    fun eclipseAlertPoint(eclipse: LunarEclipseCircumstances): Instant =
        eclipse.umbralBegin ?: eclipse.penumbralBegin ?: eclipse.greatestEclipse
}

/** A satellite pass alert to arm: fires [fireAt], before [pass] begins. */
data class PlannedPassAlert(
    val pass: SatellitePass,
    val fireAt: Instant,
)

/** A lunar eclipse alert to arm. */
data class PlannedEclipseAlert(
    val eclipse: LunarEclipseCircumstances,
    val fireAt: Instant,
)
