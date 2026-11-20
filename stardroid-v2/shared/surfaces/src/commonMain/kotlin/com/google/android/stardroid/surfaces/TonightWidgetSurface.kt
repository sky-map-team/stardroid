/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.SatelliteIds
import com.google.android.stardroid.catalog.SolarSystemIds
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * How far ahead "tonight" looks for satellite passes, in the tonight widget and the digest.
 * Shorter than the pass search's 48 hours: these answer "what is worth going outside for tonight",
 * and a pass 40 hours away is not tonight's business.
 */
val TONIGHT_PASS_HORIZON: Duration = 16.hours

/**
 * The passes beginning within [TONIGHT_PASS_HORIZON] of [at]. iOS searches once, further ahead
 * (so it has tomorrow's when the app isn't opened for a day), and narrows to each timeline entry.
 */
fun List<SatellitePass>.tonightFrom(at: Instant): List<SatellitePass> =
    filter { it.start >= at && it.start < at + TONIGHT_PASS_HORIZON }

/** The card a tonight row opens when tapped. */
val SkyEvent.targetObjectId: String
    get() =
        when (this) {
            is SkyEvent.WellPlacedPlanet -> SolarSystemIds.idFor(body).value
            is SkyEvent.SatellitePassTonight -> SatelliteIds.idFor(pass.noradId).value
            is SkyEvent.ShowerPeak -> shower.id.value
            is SkyEvent.MoonPhaseTonight,
            is SkyEvent.BrightMoon,
            is SkyEvent.LunarEclipseUpcoming,
            -> MOON_OBJECT_ID
        }

/** The card the countdown widget opens when tapped. */
val CountdownTarget.targetObjectId: String
    get() =
        when (this) {
            is CountdownTarget.ToShowerPeak -> shower.id.value
            is CountdownTarget.ToMoonPhase -> MOON_OBJECT_ID
        }

/** The countdown ring's span: empty this many days out, full on the night. */
const val COUNTDOWN_RING_DAYS = 30

/** How much of the countdown ring is filled, 0 to 1, with [daysRemaining] to go. */
fun countdownRingProgress(daysRemaining: Int): Float =
    1f - daysRemaining.coerceIn(0, COUNTDOWN_RING_DAYS) / COUNTDOWN_RING_DAYS.toFloat()

/** How a highlight's dot is coloured: gold for the best, blue for good, dim for the rest. */
enum class HighlightDot { GOLD, BLUE, DIM }

fun highlightDot(quality: Double): HighlightDot =
    when {
        quality >= 0.6 -> HighlightDot.GOLD
        quality >= 0.4 -> HighlightDot.BLUE
        else -> HighlightDot.DIM
    }

/** One moment of the tonight widget's timeline and what it shows from then on. */
data class TonightWidgetEntry(
    val time: Instant,
    val sky: TonightSky,
)

/**
 * The tonight widget's timeline from [start], one entry per [step] over [span], each the events
 * engine's answer at that moment: a passed sunset gives way to tomorrow's, a pass drops out once it
 * has begun (ios-platform-surfaces.md §6.2). [passes] may run further ahead than tonight; each
 * entry takes [tonightFrom] its own time.
 */
fun tonightWidgetTimeline(
    start: Instant,
    location: LatLong?,
    showers: List<MeteorShower>,
    passes: List<SatellitePass>,
    zone: TimeZone,
    span: Duration = 12.hours,
    step: Duration = 1.hours,
): List<TonightWidgetEntry> {
    require(step.isPositive()) { "step must be positive" }
    val count = (span / step).toInt() + 1
    return List(count) { i ->
        val time = start + step * i
        TonightWidgetEntry(
            time,
            tonightSky(time, location, showers, zone, passes = passes.tonightFrom(time)),
        )
    }
}

/** One moment of the countdown widget's timeline: its target and the whole days left to it. */
data class CountdownWidgetEntry(
    val time: Instant,
    val target: CountdownTarget?,
    val daysRemaining: Int,
)

/**
 * The countdown widget's timeline: [start], then each local midnight for [days] days, when the
 * day count changes. The target is worked out afresh at each, so it moves on once an event has
 * passed. The countdown is about dates, not local geometry, so it needs no location.
 */
fun countdownWidgetTimeline(
    start: Instant,
    showers: List<MeteorShower>,
    zone: TimeZone,
    days: Int = 7,
): List<CountdownWidgetEntry> {
    val today = start.toLocalDateTime(zone).date
    val midnights = (1..days).map { today.plus(it, DateTimeUnit.DAY).atStartOfDayIn(zone) }
    return (listOf(start) + midnights).map { time ->
        val target = tonightSky(time, location = null, showers = showers, zone = zone).countdown
        CountdownWidgetEntry(
            time,
            target,
            target?.let { dayOffset(time, it.time, zone).coerceAtLeast(0) } ?: 0,
        )
    }
}
