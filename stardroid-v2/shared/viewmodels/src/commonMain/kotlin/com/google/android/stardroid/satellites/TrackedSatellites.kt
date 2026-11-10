/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.satellites

import com.google.android.stardroid.astronomy.PassSearch
import com.google.android.stardroid.astronomy.SatelliteEphemeris
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.Sgp4
import com.google.android.stardroid.astronomy.goodPasses
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The satellites the map draws, as tap and search targets with cards attached.
 *
 * Positions are J2000 topocentric, matching where [SatelliteLayer] draws them. Empty when no
 * element sets are cached; one that fails to propagate is skipped.
 */
fun trackedSatellites(
    elements: SatelliteElements,
    location: LatLong,
    description: String?,
    at: Instant,
): List<TrackedSatellite> {
    if (elements.freshness == ElementFreshness.ABSENT) return emptyList()
    return elements.tles
        .filter { it.noradId in SatelliteLayer.TRACKED_NORAD_IDS }
        .mapNotNull { tle ->
            val state =
                runCatching { Sgp4(tle).propagateAt(at) }.getOrNull() ?: return@mapNotNull null
            val position = SatelliteEphemeris.topocentricJ2000(state, at, location).raDec
            TrackedSatellite(
                tle = tle,
                info = SatelliteIds.cardFor(tle, position, description),
                position = position,
            )
        }
}

/** Tighter than the ten-day staleness gate: a list of times promises more than one time does. */
private val CARD_PASS_WINDOW: Duration = 5.days

private const val CARD_PASS_LIMIT = 5

/**
 * The next good passes for one satellite, for the info card's pass table.
 *
 * Past the staleness gate ([SatelliteElements.mayShowPassTimes]) the times would be
 * confidently wrong, so none.
 */
fun upcomingGoodPasses(
    elements: SatelliteElements,
    noradId: Int,
    location: LatLong,
    from: Instant,
): List<SatellitePass> {
    if (!elements.mayShowPassTimes) return emptyList()
    val tle = elements.tles.firstOrNull { it.noradId == noradId } ?: return emptyList()
    return runCatching { PassSearch(tle, location).visiblePasses(from, CARD_PASS_WINDOW) }
        .getOrDefault(emptyList())
        .goodPasses(CARD_PASS_LIMIT)
}
