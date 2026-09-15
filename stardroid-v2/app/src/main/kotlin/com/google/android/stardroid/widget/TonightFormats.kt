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
import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.DARK_MOON_FRACTION
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.layers.ResourceLayerStrings
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.ui.map.HudFormats
import com.google.android.stardroid.ui.search.SolarSystemIds
import kotlinx.datetime.Instant
import java.util.Date
import kotlin.math.roundToInt

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
): HighlightLine =
    when (event) {
        is SkyEvent.WellPlacedPlanet ->
            HighlightLine(
                context.getString(
                    R.string.tonight_well_placed,
                    ResourceLayerStrings(context.resources).bodyName(event.body),
                    formatTime(context, event.from),
                ),
                context.getString(
                    R.string.tonight_trailing_direction,
                    cardinal(context, event.azimuthDeg),
                    event.bestAltitudeDeg.roundToInt(),
                ),
                SolarSystemIds.idFor(event.body).value,
            )
        is SkyEvent.SatellitePassTonight ->
            HighlightLine(
                context.getString(
                    R.string.tonight_satellite_pass,
                    event.pass.satelliteName,
                    formatTime(context, event.pass.start),
                ),
                // Direction and peak altitude, the two things you need to point yourself; the
                // magnitude is shown to one decimal and no more, because the model is only good
                // to about half a magnitude (D94).
                context.getString(
                    R.string.tonight_trailing_pass,
                    cardinal(context, event.pass.startAzimuthDeg),
                    event.pass.maxAltitudeDeg.roundToInt(),
                    event.pass.peakMagnitude,
                ),
                SatelliteLayer.LAYER_ID.id,
            )
        is SkyEvent.ShowerPeak ->
            HighlightLine(
                if (event.daysUntilPeak == 0) {
                    context.getString(R.string.tonight_shower_peak, event.shower.name)
                } else {
                    context.getString(
                        R.string.tonight_shower_days,
                        event.shower.name,
                        event.daysUntilPeak,
                    )
                },
                if (event.moonIllumination < DARK_MOON_FRACTION) {
                    context.getString(R.string.tonight_dark_skies)
                } else {
                    context.getString(
                        R.string.tonight_trailing_moon,
                        (event.moonIllumination * 100).roundToInt(),
                    )
                },
                event.shower.id.value,
            )
        is SkyEvent.MoonPhaseTonight ->
            HighlightLine(
                context.getString(
                    if (event.phase == LunarPhase.NEW) {
                        R.string.tonight_new_moon
                    } else {
                        R.string.tonight_full_moon
                    },
                ),
                formatTime(context, event.time),
                MoonWidget.MOON_OBJECT_ID,
            )
        is SkyEvent.BrightMoon ->
            HighlightLine(
                context.getString(R.string.tonight_bright_moon),
                event.moonSet?.let {
                    context.getString(R.string.moon_widget_set_time, formatTime(context, it))
                } ?: "",
                MoonWidget.MOON_OBJECT_ID,
            )
        is SkyEvent.LunarEclipseUpcoming ->
            HighlightLine(
                context.getString(
                    when (event.circumstances.type) {
                        LunarEclipseType.TOTAL -> R.string.tonight_lunar_eclipse_total
                        LunarEclipseType.PARTIAL -> R.string.tonight_lunar_eclipse_partial
                        LunarEclipseType.PENUMBRAL -> R.string.tonight_lunar_eclipse_penumbral
                        LunarEclipseType.NONE ->
                            error("LunarEclipseUpcoming must carry a real eclipse type")
                    },
                ),
                formatTime(context, event.circumstances.greatestEclipse),
                MoonWidget.MOON_OBJECT_ID,
            )
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
