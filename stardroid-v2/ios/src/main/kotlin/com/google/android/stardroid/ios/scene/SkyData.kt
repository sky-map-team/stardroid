/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.scene

import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3

/** A catalog star: its primary name (if it has one), J2000 direction and visual magnitude. */
data class CatalogStar(val name: String?, val position: Vector3, val magnitude: Double)

/** One constellation figure: each stroke is a polyline of J2000 directions. */
data class ConstellationFigure(val id: String, val strokes: List<List<Vector3>>)

/**
 * Parsers for the sky data the `bundleSkyData` Gradle task ships in the app bundle.
 *
 * The Android app reads the same `source-data/` through its Room catalog; the iOS shell has no
 * SQLite layer yet, so it reads the flat files directly. Both formats are plain comma-separated
 * values with a header row and no quoting (the build fails fast if that ever changes, because a
 * quoted field would shift the column count).
 */
object SkyData {
    /** `id,ra_deg,dec_deg,magnitude,names` — `names` is `|`-separated, primary name first. */
    fun parseStars(lines: Sequence<String>): List<CatalogStar> =
        lines
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val cols = line.split(',')
                require(cols.size == STAR_COLUMNS) { "Malformed star row: $line" }
                CatalogStar(
                    name = cols[4].substringBefore('|').ifBlank { null },
                    position = RaDec(cols[1].toDouble(), cols[2].toDouble()).toGeocentricVector(),
                    magnitude = cols[3].toDouble(),
                )
            }.toList()

    /** `id,stroke,ra_deg,dec_deg` — one row per vertex, consecutive rows per stroke. */
    fun parseConstellations(lines: Sequence<String>): List<ConstellationFigure> {
        val figures = LinkedHashMap<String, LinkedHashMap<Int, MutableList<Vector3>>>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val cols = line.split(',')
            require(cols.size == CONSTELLATION_COLUMNS) { "Malformed constellation row: $line" }
            val vertex = RaDec(cols[2].toDouble(), cols[3].toDouble()).toGeocentricVector()
            figures
                .getOrPut(cols[0]) { LinkedHashMap() }
                .getOrPut(cols[1].toInt()) { mutableListOf() }
                .add(vertex)
        }
        return figures.map { (id, strokes) -> ConstellationFigure(id, strokes.values.toList()) }
    }

    private const val STAR_COLUMNS = 5
    private const val CONSTELLATION_COLUMNS = 4
}
