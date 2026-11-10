/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.azimuthDeg
import com.google.android.stardroid.events.DARK_MOON_FRACTION
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.compassPointIndex
import com.google.android.stardroid.text.TextId
import com.google.android.stardroid.text.TextSource
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

/** A tonight row's two halves: the sentence and the small note after it. */
data class HighlightText(
    val text: String,
    val trailing: String,
)

/**
 * The words of the notifications and the tonight rows, built the same way on both platforms.
 *
 * [formatTime] is the platform's own clock format (12 or 24 hour follows the device).
 */
class NotificationText(
    private val texts: TextSource,
    private val formatTime: (Instant) -> String,
) {
    fun showerTitle(peak: SkyEvent.ShowerPeak): String =
        texts.text(TextId.NOTIF_SHOWER_TITLE, peak.shower.name)

    /** Which way to look, then whether the moon is a problem. */
    fun showerBody(
        peak: SkyEvent.ShowerPeak,
        location: LatLong?,
    ): String {
        val direction =
            location?.let {
                texts.text(
                    TextId.NOTIF_SHOWER_DIRECTION,
                    cardinal(azimuthDeg(peak.shower.radiant, peak.peakTime, it)),
                )
            }
        val moonLine =
            if (peak.moonIllumination < DARK_MOON_FRACTION) {
                texts.text(TextId.NOTIF_SHOWER_DARK_MOON, peak.shower.peakZhr)
            } else {
                texts.text(
                    TextId.NOTIF_SHOWER_BRIGHT_MOON,
                    (peak.moonIllumination * 100).roundToInt(),
                    peak.shower.peakZhr,
                )
            }
        return listOfNotNull(direction, moonLine).joinToString(" ")
    }

    fun digestTitle(): String = texts.text(TextId.NOTIF_DIGEST_TITLE)

    /** The widget's highlight sentences, verbatim; empty when there is nothing worth a look. */
    fun digestLines(sky: TonightSky): List<String> = sky.highlights.map { highlight(it).text }

    fun highlight(event: SkyEvent): HighlightText =
        when (event) {
            is SkyEvent.WellPlacedPlanet ->
                HighlightText(
                    texts.text(
                        TextId.TONIGHT_WELL_PLACED,
                        bodyName(event.body),
                        formatTime(event.from),
                    ),
                    texts.text(
                        TextId.TONIGHT_TRAILING_DIRECTION,
                        cardinal(event.azimuthDeg),
                        event.bestAltitudeDeg.roundToInt(),
                    ),
                )
            is SkyEvent.SatellitePassTonight ->
                HighlightText(
                    texts.text(
                        TextId.TONIGHT_SATELLITE_PASS,
                        event.pass.satelliteName,
                        formatTime(event.pass.start),
                    ),
                    // One decimal and no more: the magnitude model is good to about half a
                    // magnitude (D94).
                    texts.text(
                        TextId.TONIGHT_TRAILING_PASS_PEAK,
                        event.pass.maxAltitudeDeg.roundToInt(),
                        event.pass.peakMagnitude,
                    ),
                )
            is SkyEvent.ShowerPeak ->
                HighlightText(
                    if (event.daysUntilPeak == 0) {
                        texts.text(TextId.TONIGHT_SHOWER_PEAK, event.shower.name)
                    } else {
                        texts.text(
                            TextId.TONIGHT_SHOWER_DAYS,
                            event.shower.name,
                            event.daysUntilPeak,
                        )
                    },
                    if (event.moonIllumination < DARK_MOON_FRACTION) {
                        texts.text(TextId.TONIGHT_DARK_SKIES)
                    } else {
                        texts.text(
                            TextId.TONIGHT_TRAILING_MOON,
                            (event.moonIllumination * 100).roundToInt(),
                        )
                    },
                )
            is SkyEvent.MoonPhaseTonight ->
                HighlightText(
                    texts.text(
                        if (event.phase == LunarPhase.NEW) {
                            TextId.TONIGHT_NEW_MOON
                        } else {
                            TextId.TONIGHT_FULL_MOON
                        },
                    ),
                    formatTime(event.time),
                )
            is SkyEvent.BrightMoon ->
                HighlightText(
                    texts.text(TextId.TONIGHT_BRIGHT_MOON),
                    event.moonSet?.let { texts.text(TextId.MOON_SET_TIME, formatTime(it)) }
                        ?: "",
                )
            is SkyEvent.LunarEclipseUpcoming ->
                HighlightText(
                    texts.text(
                        when (event.circumstances.type) {
                            LunarEclipseType.TOTAL -> TextId.TONIGHT_LUNAR_ECLIPSE_TOTAL
                            LunarEclipseType.PARTIAL -> TextId.TONIGHT_LUNAR_ECLIPSE_PARTIAL
                            LunarEclipseType.PENUMBRAL -> TextId.TONIGHT_LUNAR_ECLIPSE_PENUMBRAL
                            LunarEclipseType.NONE ->
                                error("LunarEclipseUpcoming must carry a real eclipse type")
                        },
                    ),
                    formatTime(event.circumstances.greatestEclipse),
                )
        }

    private fun cardinal(azimuthDeg: Double): String =
        texts.text(TextId.compass(compassPointIndex(azimuthDeg)))

    private fun bodyName(body: SolarSystemBody): String =
        texts.text(
            when (body) {
                SolarSystemBody.SUN -> TextId.BODY_SUN
                SolarSystemBody.MOON -> TextId.BODY_MOON
                SolarSystemBody.MERCURY -> TextId.BODY_MERCURY
                SolarSystemBody.VENUS -> TextId.BODY_VENUS
                SolarSystemBody.EARTH -> TextId.BODY_EARTH
                SolarSystemBody.MARS -> TextId.BODY_MARS
                SolarSystemBody.JUPITER -> TextId.BODY_JUPITER
                SolarSystemBody.SATURN -> TextId.BODY_SATURN
                SolarSystemBody.URANUS -> TextId.BODY_URANUS
                SolarSystemBody.NEPTUNE -> TextId.BODY_NEPTUNE
                SolarSystemBody.PLUTO -> TextId.BODY_PLUTO
            },
        )
}
