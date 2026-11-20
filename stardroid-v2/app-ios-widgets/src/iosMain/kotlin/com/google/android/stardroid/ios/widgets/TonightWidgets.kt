/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.widgets

import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.surfaces.CountdownWidgetEntry
import com.google.android.stardroid.surfaces.HighlightDot
import com.google.android.stardroid.surfaces.TonightWidgetEntry
import com.google.android.stardroid.surfaces.WidgetColors
import com.google.android.stardroid.surfaces.countdownRingProgress
import com.google.android.stardroid.surfaces.countdownWidgetTimeline
import com.google.android.stardroid.surfaces.dayOffset
import com.google.android.stardroid.surfaces.highlightDot
import com.google.android.stardroid.surfaces.targetObjectId
import com.google.android.stardroid.surfaces.tonightWidgetTimeline
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours

/** One highlight row, worded as Android's widget and the digest word it. */
class TonightWidgetRow(
    val text: String,
    val trailing: String,
    val link: String,
    /** 0xAARRGGBB. */
    val dotColor: Long,
)

/** One timeline entry of the tonight widget, every string ready to draw. */
class TonightWidgetSnapshot(
    val epochSeconds: Double,
    /** "Tonight · Wed 30 Jul". */
    val header: String,
    /** "sun sets 20:14 · dark 21:30"; null without a location. */
    val sunTimes: String?,
    val rows: List<TonightWidgetRow>,
    /** With no rows: "Next: Perseids peak in 12 days", when there is a countdown target. */
    val quietNext: String?,
)

/** One timeline entry of the countdown widget; [hasTarget] false draws an empty surface. */
class CountdownWidgetSnapshot(
    val epochSeconds: Double,
    val hasTarget: Boolean,
    /** The day count, or "now" on the night. */
    val figure: String,
    val isTonight: Boolean,
    val unit: String,
    val name: String,
    /** "New-moon peak", or null. */
    val note: String?,
    /** How much of the ring is filled, 0 to 1. */
    val progress: Double,
    val link: String?,
)

/** The tonight and countdown widgets' entry point for the Swift extension. */
object TonightWidgets {
    /** Hourly snapshots for [hours] hours from [startEpochSeconds], at the saved location. */
    fun snapshots(
        startEpochSeconds: Double,
        hours: Int,
    ): List<TonightWidgetSnapshot> {
        val zone = TimeZone.currentSystemDefault()
        return tonightWidgetTimeline(
            start = instant(startEpochSeconds),
            location = WidgetSharedStore.savedLocation,
            showers = WidgetSharedStore.showers,
            passes = WidgetSharedStore.passes,
            zone = zone,
            span = hours.hours,
        ).map { it.toSnapshot(zone) }
    }

    /** "Quiet sky tonight", the line shown when no row is worth a look. */
    val quiet: String get() = WidgetText.text("tonight_quiet")

    /** A snapshot from [startEpochSeconds], then one at each local midnight for [days] days. */
    fun countdownSnapshots(
        startEpochSeconds: Double,
        days: Int,
    ): List<CountdownWidgetSnapshot> {
        val zone = TimeZone.currentSystemDefault()
        return countdownWidgetTimeline(
            instant(startEpochSeconds),
            WidgetSharedStore.showers,
            zone,
            days,
        ).map { it.toSnapshot() }
    }
}

private fun TonightWidgetEntry.toSnapshot(zone: TimeZone): TonightWidgetSnapshot {
    val words = WidgetText.words
    val sunset = sky.sunset?.let(WidgetText::time)
    val dark = sky.darknessStart?.let(WidgetText::time)
    val countdown = sky.countdown
    return TonightWidgetSnapshot(
        epochSeconds = epochSeconds(time),
        header = WidgetText.text("tonight_widget_header", WidgetText.shortDate(time)),
        sunTimes =
            when {
                sunset == null -> null
                dark == null -> WidgetText.text("tonight_widget_sunset_only", sunset)
                else -> WidgetText.text("tonight_widget_sun_times", sunset, dark)
            },
        rows =
            sky.highlights.map { event ->
                val line = words.highlight(event)
                TonightWidgetRow(
                    line.text,
                    line.trailing,
                    objectLink(event.targetObjectId),
                    dotColor(highlightDot(event.quality)),
                )
            },
        quietNext =
            countdown?.let {
                WidgetText.text(
                    "tonight_quiet_next",
                    WidgetText.countdownName(it),
                    dayOffset(time, it.time, zone),
                )
            },
    )
}

private fun CountdownWidgetEntry.toSnapshot(): CountdownWidgetSnapshot {
    val target = target
    val tonight = daysRemaining == 0
    return CountdownWidgetSnapshot(
        epochSeconds = epochSeconds(time),
        hasTarget = target != null,
        figure = if (tonight) WidgetText.text("countdown_tonight") else daysRemaining.toString(),
        isTonight = tonight,
        unit = WidgetText.text("countdown_days"),
        name = target?.let(WidgetText::countdownName).orEmpty(),
        note =
            WidgetText
                .text("countdown_new_moon_peak")
                .takeIf { target is CountdownTarget.ToShowerPeak && target.darkPeak },
        progress = countdownRingProgress(daysRemaining).toDouble(),
        link = target?.let { objectLink(it.targetObjectId) },
    )
}

private fun dotColor(dot: HighlightDot): Long =
    when (dot) {
        HighlightDot.GOLD -> WidgetColors.STAR_GOLD
        HighlightDot.BLUE -> WidgetColors.DOT_BLUE
        HighlightDot.DIM -> WidgetColors.DOT_DIM
    }

private fun instant(epochSeconds: Double) =
    Instant.fromEpochMilliseconds(
        (epochSeconds * 1000).toLong(),
    )

private fun epochSeconds(time: Instant) = time.toEpochMilliseconds() / 1000.0
