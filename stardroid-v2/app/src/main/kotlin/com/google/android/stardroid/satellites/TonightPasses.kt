/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.content.Context
import com.google.android.stardroid.astronomy.PassSearch
import com.google.android.stardroid.astronomy.SatelliteEphemeris
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.Sgp4
import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.startup.Experiment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Tonight's visible satellite passes, for the surfaces that render [SkyEvent]s (D92 phase 4b).
 *
 * **This is the gate.** `:core:events` is pure and cannot read a DataStore preference, so it takes
 * passes as a parameter and this function decides whether there are any to give it. One gate
 * covers the tonight widget and the D77 digest line consistently, and the purity of the events
 * engine survives intact.
 *
 * Returns empty — never throws, never blocks on the network — when any of the following holds:
 * the satellite experiment is off, the user turned the layer off, there is no location, or the
 * cached element sets are missing or too stale to time a pass honestly.
 */
suspend fun tonightSatellitePasses(
    context: Context,
    location: LatLong?,
    from: Instant = Clock.System.now(),
    within: Duration = TONIGHT_HORIZON,
): List<SatellitePass> {
    if (location == null) return emptyList()
    val entryPoint = satelliteEntryPoint(context)
    if (!entryPoint.experimentConfig().isEnabled(Experiment.SATELLITES)) return emptyList()
    if (!entryPoint.settings().layerEnabled(SatelliteLayer.LAYER_ID).first()) return emptyList()

    val elements =
        withContext(Dispatchers.IO) { entryPoint.satelliteElementsRepository().current() }
    return withContext(Dispatchers.Default) {
        // Past ten days a confidently wrong "21:47" is worse than saying nothing, so stale
        // elements keep the map layer but lose the pass times (D92 §"Staleness").
        if (!elements.mayShowPassTimes || elements.freshness == ElementFreshness.ABSENT) {
            return@withContext emptyList()
        }

        elements.tles
            .filter { it.noradId in SatelliteLayer.TRACKED_NORAD_IDS }
            .flatMap { tle ->
                runCatching {
                    PassSearch(tle, location).visiblePasses(from, within)
                }.getOrDefault(emptyList())
            }.sortedBy { it.start }
    }
}

/**
 * How far ahead "tonight" looks for the widget and digest.
 *
 * Shorter than [PassSearch.DEFAULT_SEARCH_WINDOW]'s 48 hours: these surfaces answer "what is worth
 * going outside for tonight", and a pass 40 hours away is not tonight's business.
 */
val TONIGHT_HORIZON: Duration = 16.hours

/** A satellite currently drawn: its synthesized card and where it is right now. */
data class TrackedSatellite(
    val tle: Tle,
    val info: ObjectInfo,
    val position: RaDec,
)

/**
 * The satellites the map is drawing, as tap targets with cards attached.
 *
 * Positions are **J2000 topocentric**, matching where [SatelliteLayer] actually draws them, so a
 * tap lands on the thing the user is pointing at rather than a few degrees off it.
 */
suspend fun trackedSatellites(
    context: Context,
    location: LatLong?,
    description: String?,
    at: Instant = Clock.System.now(),
): List<TrackedSatellite> {
    if (location == null) return emptyList()
    val entryPoint = satelliteEntryPoint(context)
    if (!entryPoint.experimentConfig().isEnabled(Experiment.SATELLITES)) return emptyList()
    if (!entryPoint.settings().layerEnabled(SatelliteLayer.LAYER_ID).first()) return emptyList()

    val elements =
        withContext(Dispatchers.IO) { entryPoint.satelliteElementsRepository().current() }
    return withContext(Dispatchers.Default) {
        if (elements.freshness == ElementFreshness.ABSENT) return@withContext emptyList()

        elements.tles
            .filter { it.noradId in SatelliteLayer.TRACKED_NORAD_IDS }
            .mapNotNull { tle ->
                val state =
                    runCatching { Sgp4(tle).propagateAt(at) }.getOrNull()
                        ?: return@mapNotNull null
                val position =
                    SatelliteEphemeris.topocentricJ2000(state, at, location).raDec
                TrackedSatellite(
                    tle = tle,
                    info = SatelliteIds.cardFor(tle, position, description),
                    position = position,
                )
            }
    }
}

/** The next visible pass for one satellite, for the info card's pass row. */
suspend fun nextVisiblePass(
    context: Context,
    noradId: Int,
    location: LatLong?,
    from: Instant = Clock.System.now(),
): SatellitePass? {
    if (location == null) return null
    val elements =
        withContext(Dispatchers.IO) {
            satelliteEntryPoint(context).satelliteElementsRepository().current()
        }
    return withContext(Dispatchers.Default) {
        // Past ten days the times would be confidently wrong, which is worse than absent.
        if (!elements.mayShowPassTimes) return@withContext null
        elements.tles
            .firstOrNull { it.noradId == noradId }
            ?.let { tle ->
                runCatching { PassSearch(tle, location).visiblePasses(from) }
                    .getOrDefault(emptyList())
                    .minByOrNull { it.start }
            }
    }
}
