/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.MoonWidgetModel
import com.google.android.stardroid.astronomy.moonWidgetModel
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** The catalog id the Moon widgets open when tapped. */
const val MOON_OBJECT_ID = "planet/moon"

/** One "rises 17:42" / "sets 03:12" line of the moon widget. */
data class MoonCrossing(
    val rise: Boolean,
    val time: Instant,
)

/**
 * Rise and set in the order they happen. Each is independently "next after now" (#1067), so
 * listing rise first would put "rises 6:36PM" above a "sets 5:56AM" that comes earlier tonight.
 * Either is missing when it didn't converge (D51), and both without a location.
 */
val MoonWidgetModel.crossingsInOrder: List<MoonCrossing>
    get() =
        listOfNotNull(
            riseTime?.let { MoonCrossing(rise = true, time = it) },
            setTime?.let { MoonCrossing(rise = false, time = it) },
        ).sortedBy { it.time }

/**
 * How many local calendar days after [now]'s day [time] falls on; negative if before. A widget
 * shows the next crossing, often tomorrow's, and marks it with a compact "+1" rather than a date.
 */
fun dayOffset(
    now: Instant,
    time: Instant,
    zone: TimeZone,
): Int {
    val nowDay = now.toLocalDateTime(zone).date.toEpochDays()
    val timeDay = time.toLocalDateTime(zone).date.toEpochDays()
    return timeDay - nowDay
}

/** One moment of a widget timeline and what the moon widget shows from then on. */
data class MoonWidgetEntry(
    val time: Instant,
    val model: MoonWidgetModel,
)

/**
 * The moon widget's timeline from [start], one entry per [step] over [span], for an iOS
 * WidgetKit provider to hand over in one go (ios-platform-surfaces.md §6.2). The phase and the
 * rise and set times change slowly, so hourly entries stay accurate, and every entry is computed
 * on the device: precomputed results would go stale while the app goes unopened.
 */
fun moonWidgetTimeline(
    start: Instant,
    location: LatLong?,
    span: Duration = 24.hours,
    step: Duration = 1.hours,
): List<MoonWidgetEntry> {
    require(step.isPositive()) { "step must be positive" }
    val count = (span / step).toInt() + 1
    return List(count) { i ->
        val time = start + step * i
        MoonWidgetEntry(time, moonWidgetModel(time, location))
    }
}
