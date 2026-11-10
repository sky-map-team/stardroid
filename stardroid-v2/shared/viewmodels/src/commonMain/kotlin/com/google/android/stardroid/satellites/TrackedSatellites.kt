/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.satellites

import com.google.android.stardroid.astronomy.SatelliteEphemeris
import com.google.android.stardroid.astronomy.Sgp4
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Instant

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
