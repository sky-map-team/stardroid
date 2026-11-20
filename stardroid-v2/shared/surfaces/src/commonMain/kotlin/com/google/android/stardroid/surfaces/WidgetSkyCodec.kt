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
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.math.RaDec
import kotlinx.datetime.Instant

/**
 * The tonight widget's inputs that only the app can read, as text for the App Group store: the
 * meteor showers from the catalog database and the satellite passes from the element sets
 * (ios-platform-surfaces.md §6.1). One record per line, tab-separated. A line that doesn't parse is
 * dropped, so a store written by another version degrades to fewer rows rather than none.
 */
object WidgetSkyCodec {
    private const val SEP = "\t"
    private val CONTROL_CHARS = Regex("[\t\n\r]")

    fun encodeShowers(showers: List<MeteorShower>): String =
        showers.joinToString("\n") {
            fields(
                it.id.value,
                it.name,
                it.radiant.raDeg,
                it.radiant.decDeg,
                monthDay(it.activeFrom),
                monthDay(it.peak),
                monthDay(it.activeTo),
                it.peakZhr,
            )
        }

    fun decodeShowers(text: String): List<MeteorShower> =
        records(text, SHOWER_FIELDS) {
            MeteorShower(
                id = CelestialObjectId(it[0]),
                name = it[1],
                radiant = RaDec(it[2].toDouble(), it[3].toDouble()),
                activeFrom = MonthDay.parse(it[4]),
                peak = MonthDay.parse(it[5]),
                activeTo = MonthDay.parse(it[6]),
                peakZhr = it[7].toInt(),
            )
        }

    fun encodePasses(passes: List<SatellitePass>): String =
        passes.joinToString("\n") {
            fields(
                it.satelliteName,
                it.noradId,
                it.start.toEpochMilliseconds(),
                it.culmination.toEpochMilliseconds(),
                it.end.toEpochMilliseconds(),
                it.maxAltitudeDeg,
                it.startAzimuthDeg,
                it.endAzimuthDeg,
                it.peakMagnitude,
                it.shadowEntry?.toEpochMilliseconds() ?: "",
            )
        }

    fun decodePasses(text: String): List<SatellitePass> =
        records(text, PASS_FIELDS) {
            SatellitePass(
                satelliteName = it[0],
                noradId = it[1].toInt(),
                start = instant(it[2]),
                culmination = instant(it[3]),
                end = instant(it[4]),
                maxAltitudeDeg = it[5].toDouble(),
                startAzimuthDeg = it[6].toDouble(),
                endAzimuthDeg = it[7].toDouble(),
                peakMagnitude = it[8].toDouble(),
                shadowEntry = it[9].takeIf { field -> field.isNotEmpty() }?.let(::instant),
            )
        }

    // A name can't hold a tab or a newline once it's a field; a space reads the same on a widget.
    private fun fields(vararg values: Any): String =
        values.joinToString(SEP) { it.toString().replace(CONTROL_CHARS, " ") }

    private fun <T> records(
        text: String,
        count: Int,
        parse: (List<String>) -> T,
    ): List<T> =
        text
            .lineSequence()
            .map { it.split(SEP) }
            .filter { it.size == count }
            .mapNotNull { runCatching { parse(it) }.getOrNull() }
            .toList()

    private fun monthDay(day: MonthDay): String =
        "${day.month.toString().padStart(2, '0')}-${day.day.toString().padStart(2, '0')}"

    private fun instant(millis: String): Instant = Instant.fromEpochMilliseconds(millis.toLong())

    private const val SHOWER_FIELDS = 8
    private const val PASS_FIELDS = 10
}
