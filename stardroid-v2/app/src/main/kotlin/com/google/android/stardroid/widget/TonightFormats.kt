/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.content.Context
import com.google.android.stardroid.R
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.notifications.notificationText
import com.google.android.stardroid.satellites.SatelliteIds
import com.google.android.stardroid.ui.map.HudFormats
import com.google.android.stardroid.ui.search.SolarSystemIds
import kotlinx.datetime.Instant
import java.util.Date

/**
 * One rendered highlight: the main sentence, the small trailing note, and the catalog id
 * the line deep-links to. Shared between the tonight widget's rows and the digest
 * notification's body lines (D77) so both surfaces speak identically.
 */
internal data class HighlightLine(
    val text: String,
    val trailing: String,
    val targetId: String,
)

internal fun highlightLine(
    event: SkyEvent,
    context: Context,
): HighlightLine {
    val words = notificationText(context).highlight(event)
    return HighlightLine(words.text, words.trailing, targetIdFor(event))
}

/** The card a row opens when tapped. */
private fun targetIdFor(event: SkyEvent): String =
    when (event) {
        is SkyEvent.WellPlacedPlanet -> SolarSystemIds.idFor(event.body).value
        is SkyEvent.SatellitePassTonight -> SatelliteIds.idFor(event.pass.noradId).value
        is SkyEvent.ShowerPeak -> event.shower.id.value
        is SkyEvent.MoonPhaseTonight,
        is SkyEvent.BrightMoon,
        is SkyEvent.LunarEclipseUpcoming,
        -> MoonWidget.MOON_OBJECT_ID
    }

/** The countdown target's display name, shared by the countdown and tonight widgets. */
internal fun countdownName(
    countdown: CountdownTarget,
    context: Context,
): String =
    when (countdown) {
        is CountdownTarget.ToShowerPeak ->
            context.getString(R.string.countdown_shower_peak, countdown.shower.name)
        is CountdownTarget.ToMoonPhase ->
            context.getString(
                if (countdown.phase == LunarPhase.NEW) {
                    R.string.countdown_new_moon
                } else {
                    R.string.countdown_full_moon
                },
            )
    }

/** The 16-point compass name for [azimuthDeg], from the HUD's shared rose. */
internal fun cardinal(
    context: Context,
    azimuthDeg: Double,
): String =
    context.resources
        .getStringArray(R.array.hud_cardinal_directions)[HudFormats.cardinalIndex(azimuthDeg)]

internal fun formatTime(
    context: Context,
    time: Instant,
): String =
    android.text.format.DateFormat
        .getTimeFormat(context)
        .format(Date(time.toEpochMilliseconds()))

internal fun dotRes(quality: Double): Int =
    when {
        quality >= 0.6 -> R.drawable.widget_dot_gold
        quality >= 0.4 -> R.drawable.widget_dot_blue
        else -> R.drawable.widget_dot_dim
    }
