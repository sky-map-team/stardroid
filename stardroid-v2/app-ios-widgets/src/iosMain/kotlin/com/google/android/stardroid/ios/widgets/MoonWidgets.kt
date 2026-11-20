/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.widgets

import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.dayOfCycle
import com.google.android.stardroid.astronomy.illuminationPercent
import com.google.android.stardroid.surfaces.MOON_OBJECT_ID
import com.google.android.stardroid.surfaces.MoonDisc
import com.google.android.stardroid.surfaces.MoonWidgetEntry
import com.google.android.stardroid.surfaces.WidgetColors
import com.google.android.stardroid.surfaces.crossingsInOrder
import com.google.android.stardroid.surfaces.dayOffset
import com.google.android.stardroid.surfaces.moonDisc
import com.google.android.stardroid.surfaces.moonWidgetTimeline
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours

/** One timeline entry as Swift draws it: plain values, with the strings as their resource keys. */
class MoonWidgetSnapshot(
    val epochSeconds: Double,
    val phaseNameKey: String,
    val illuminationPercent: Int,
    val dayOfCycle: Int,
    val discFlipped: Boolean,
    /** In units of the disc's radius; see [MoonDisc]. */
    val terminatorRx: Double,
    val terminatorBulgesLeft: Boolean,
    val crossings: List<MoonWidgetCrossing>,
)

/** A rise or set line; [dayOffset] is how many local days after the entry's day it falls. */
class MoonWidgetCrossing(
    val rise: Boolean,
    val epochSeconds: Double,
    val dayOffset: Int,
)

/** [MoonDisc.Mare] for Swift, in units of the disc's radius about its centre. */
class WidgetMare(
    val x: Double,
    val y: Double,
    val r: Double,
)

/** The Moon widgets' entry point for the Swift extension. */
object MoonWidgets {
    /** Hourly snapshots for [hours] hours from [startEpochSeconds], at the saved location. */
    fun snapshots(
        startEpochSeconds: Double,
        hours: Int,
    ): List<MoonWidgetSnapshot> {
        val start = Instant.fromEpochMilliseconds((startEpochSeconds * 1000).toLong())
        val zone = TimeZone.currentSystemDefault()
        return moonWidgetTimeline(start, WidgetSharedStore.savedLocation, span = hours.hours)
            .map { it.toSnapshot(zone) }
    }

    val link: String
        get() = objectLink(MOON_OBJECT_ID)

    val maria: List<WidgetMare> = MoonDisc.MARIA.map { WidgetMare(it.x, it.y, it.r) }
}

/** [WidgetColors] for Swift, as 0xAARRGGBB. */
object WidgetPalette {
    val starlight = WidgetColors.STARLIGHT
    val starGold = WidgetColors.STAR_GOLD
    val muted = WidgetColors.MUTED
    val surface = WidgetColors.SURFACE
    val outline = WidgetColors.OUTLINE
    val moonLit = WidgetColors.MOON_LIT
    val moonShadow = WidgetColors.MOON_SHADOW
    val moonMaria = WidgetColors.MOON_MARIA
    val planetGreen = WidgetColors.PLANET_GREEN
}

private fun MoonWidgetEntry.toSnapshot(zone: TimeZone): MoonWidgetSnapshot {
    val disc = moonDisc(model.illuminatedFraction, model.waxing, model.mirrored)
    return MoonWidgetSnapshot(
        epochSeconds = time.toEpochMilliseconds() / 1000.0,
        phaseNameKey = phaseNameKey(model.phase),
        illuminationPercent = model.illuminationPercent,
        dayOfCycle = model.dayOfCycle,
        discFlipped = disc.flipped,
        terminatorRx = disc.terminatorRx,
        terminatorBulgesLeft = disc.terminatorBulgesLeft,
        crossings =
            model.crossingsInOrder.map {
                MoonWidgetCrossing(
                    rise = it.rise,
                    epochSeconds = it.time.toEpochMilliseconds() / 1000.0,
                    dayOffset = dayOffset(time, it.time, zone),
                )
            },
    )
}

// The shared strings' names, which the generated Localizable.strings is keyed by.
internal fun phaseNameKey(phase: LunarPhase): String =
    when (phase) {
        LunarPhase.NEW -> "moon_phase_new"
        LunarPhase.WAXING_CRESCENT -> "moon_phase_waxing_crescent"
        LunarPhase.FIRST_QUARTER -> "moon_phase_first_quarter"
        LunarPhase.WAXING_GIBBOUS -> "moon_phase_waxing_gibbous"
        LunarPhase.FULL -> "moon_phase_full"
        LunarPhase.WANING_GIBBOUS -> "moon_phase_waning_gibbous"
        LunarPhase.LAST_QUARTER -> "moon_phase_last_quarter"
        LunarPhase.WANING_CRESCENT -> "moon_phase_waning_crescent"
    }
