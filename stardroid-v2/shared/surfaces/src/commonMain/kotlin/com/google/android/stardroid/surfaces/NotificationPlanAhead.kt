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
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Which kinds of alert the user has turned on. All off until they opt in. */
data class AlertPreferences(
    val showers: Boolean = false,
    val digest: Boolean = false,
    val passes: Boolean = false,
    val eclipses: Boolean = false,
) {
    val any: Boolean get() = showers || digest || passes || eclipses
}

/**
 * One local notification, ready to hand to the platform.
 *
 * [id] is the kind plus the night it is for, so planning again replaces rather than duplicates.
 * [threadId] groups a kind's notifications. [objectId] is the object a tap should open, if any.
 */
data class ScheduledNotification(
    val id: String,
    val threadId: String,
    val fireAt: Instant,
    val title: String,
    val body: String,
    val objectId: String?,
)

/**
 * Plans the next few nights' notifications for a platform that cannot run code when one is due
 * (iOS): the same rules as [NotificationPlanner], run ahead, with the words fixed now.
 *
 * Planning runs often (every foreground), so an alert whose time has already passed is dropped
 * rather than fired a minute from now; otherwise each time the app opened after dusk it would
 * announce tonight again.
 */
class NotificationPlanAhead(
    private val words: NotificationText,
    /** The object id a tap on a pass alert opens. */
    private val satelliteObjectId: (noradId: Int) -> String,
    private val passLead: Duration = PASS_LEAD,
) {
    class Inputs(
        val now: Instant,
        val zone: TimeZone,
        val location: LatLong?,
        val showers: List<MeteorShower>,
        /** Visible passes over the pass horizon, empty when satellites are off. */
        val passes: List<SatellitePass>,
        val eclipse: LunarEclipseCircumstances?,
        val preferences: AlertPreferences,
    )

    fun plan(inputs: Inputs): List<ScheduledNotification> {
        val planned = mutableListOf<ScheduledNotification>()
        if (inputs.preferences.showers || inputs.preferences.digest) planned += nights(inputs)
        if (inputs.preferences.passes) planned += passAlerts(inputs)
        if (inputs.preferences.eclipses) planned += eclipseAlert(inputs)
        return planned
            .filter { it.fireAt > inputs.now }
            .sortedBy { it.fireAt }
            .take(MAX_NOTIFICATIONS)
    }

    private fun nights(inputs: Inputs): List<ScheduledNotification> =
        (0 until NIGHTS).mapNotNull { night ->
            val reference = reference(inputs.now, inputs.zone, night)
            val tonightsPasses =
                inputs.passes.filter { it.start >= reference && it.start - reference < EVENING }
            val sky =
                tonightSky(
                    reference,
                    inputs.location,
                    inputs.showers,
                    inputs.zone,
                    passes = tonightsPasses,
                )
            val plan =
                NotificationPlanner.plan(
                    reference,
                    sky,
                    inputs.preferences.showers,
                    inputs.preferences.digest,
                ) ?: return@mapNotNull null
            // Clamped to "a minute from now": its time has gone, so tonight's was already sent.
            if (plan.fireAt - reference <= NotificationPlanner.MINIMUM_LEAD) return@mapNotNull null
            // The sky's sunset is the next one, so after dark it is tomorrow's: the alert would
            // arrive a day late and still say "tonight".
            val date = reference.toLocalDateTime(inputs.zone).date
            if (plan.fireAt.toLocalDateTime(inputs.zone).date != date) return@mapNotNull null
            when (plan.kind) {
                NotificationKind.SHOWER_PEAK -> {
                    val peak = checkNotNull(plan.showerPeak)
                    ScheduledNotification(
                        id = "showers-$date",
                        threadId = THREAD_SHOWERS,
                        fireAt = plan.fireAt,
                        title = words.showerTitle(peak),
                        body = words.showerBody(peak, inputs.location),
                        objectId = peak.shower.id.value,
                    )
                }
                NotificationKind.TONIGHT_DIGEST ->
                    ScheduledNotification(
                        id = "digest-$date",
                        threadId = THREAD_DIGEST,
                        fireAt = plan.fireAt,
                        title = words.digestTitle(),
                        body = words.digestLines(sky).joinToString("\n"),
                        objectId = null,
                    )
            }
        }

    private fun passAlerts(inputs: Inputs): List<ScheduledNotification> =
        NotificationPlanner
            .planPassAlerts(inputs.now, inputs.passes, inputs.zone, passLead, PASS_HORIZON)
            .map { alert ->
                val pass = alert.pass
                ScheduledNotification(
                    id = "pass-${NotificationPlanner.nightOf(pass.start, inputs.zone)}",
                    threadId = THREAD_PASSES,
                    fireAt = alert.fireAt,
                    title = words.passTitle(pass.noradId, pass.satelliteName),
                    body = words.passBody(pass.start),
                    objectId = satelliteObjectId(pass.noradId),
                )
            }

    private fun eclipseAlert(inputs: Inputs): List<ScheduledNotification> {
        val alert =
            NotificationPlanner.planEclipseAlert(
                inputs.now,
                inputs.eclipse,
                inputs.location,
                ECLIPSE_HORIZON,
            ) ?: return emptyList()
        // Already under way, or its time has gone: the alert would be a minute from now.
        if (alert.fireAt - inputs.now <= NotificationPlanner.MINIMUM_LEAD) return emptyList()
        val date = alert.eclipse.greatestEclipse.toLocalDateTime(inputs.zone).date
        return listOf(
            ScheduledNotification(
                id = "eclipse-$date",
                threadId = THREAD_ECLIPSES,
                fireAt = alert.fireAt,
                title = words.eclipseTitle(alert.eclipse),
                body = words.eclipseBody(alert.eclipse),
                objectId = MOON_OBJECT_ID,
            ),
        )
    }

    /** Now for tonight, noon for each later night: the sky is judged as of the day it is for. */
    private fun reference(
        now: Instant,
        zone: TimeZone,
        night: Int,
    ): Instant {
        if (night == 0) return now
        val day: LocalDate = now.toLocalDateTime(zone).date.plus(night, DateTimeUnit.DAY)
        return day.atTime(LocalTime(12, 0)).toInstant(zone)
    }

    companion object {
        const val NIGHTS = 3
        val PASS_LEAD = 6.minutes
        val PASS_HORIZON = 3.days
        val ECLIPSE_HORIZON = 60.days
        const val THREAD_SHOWERS = "showers"
        const val THREAD_DIGEST = "digest"
        const val THREAD_PASSES = "passes"
        const val THREAD_ECLIPSES = "eclipses"

        /** The Moon's object id, which an eclipse alert opens. */
        const val MOON_OBJECT_ID = "planet/moon"

        // Far under the system's 64 pending notifications.
        private const val MAX_NOTIFICATIONS = 20
        private val EVENING = 18.hours
    }
}
