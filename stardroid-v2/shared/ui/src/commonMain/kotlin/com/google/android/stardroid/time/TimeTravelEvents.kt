/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.time_travel_apollo_11
import com.google.android.stardroid.ui.resources.time_travel_geminids_2026
import com.google.android.stardroid.ui.resources.time_travel_jupiter_occultation_2026
import com.google.android.stardroid.ui.resources.time_travel_jupiter_saturn_2020
import com.google.android.stardroid.ui.resources.time_travel_lunar_eclipse_2026
import com.google.android.stardroid.ui.resources.time_travel_lunar_eclipse_aug_2026
import com.google.android.stardroid.ui.resources.time_travel_lyrids_2026
import com.google.android.stardroid.ui.resources.time_travel_mars_uranus_2026
import com.google.android.stardroid.ui.resources.time_travel_mercury_transit_2016
import com.google.android.stardroid.ui.resources.time_travel_next_full_moon
import com.google.android.stardroid.ui.resources.time_travel_next_new_moon
import com.google.android.stardroid.ui.resources.time_travel_next_sunrise
import com.google.android.stardroid.ui.resources.time_travel_next_sunset
import com.google.android.stardroid.ui.resources.time_travel_now
import com.google.android.stardroid.ui.resources.time_travel_perseids_2026
import com.google.android.stardroid.ui.resources.time_travel_six_planet_parade_2026
import com.google.android.stardroid.ui.resources.time_travel_solar_eclipse_2024
import com.google.android.stardroid.ui.resources.time_travel_solar_eclipse_2026
import com.google.android.stardroid.ui.resources.time_travel_supermoon_2026
import com.google.android.stardroid.ui.resources.time_travel_venus_jupiter_2026
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.StringResource

/**
 * A named destination in the time-travel picker (v1 `TimeTravelEvent`).
 *
 * @param timestamp the target for [TimeTravelEventType.FIXED] events; null for computed types.
 * @param searchTarget the object the map aims at once travel completes (v1's
 *   `searchTargetRes`, by stable catalog id instead of localized name); null does nothing.
 *   Meteor showers target their radiant, which unlike in v1 is a catalog object in its own
 *   right — so travelling to a shower lands where searching for it does.
 */
data class TimeTravelEvent(
    val displayNameRes: StringResource,
    val type: TimeTravelEventType,
    val timestamp: Instant? = null,
    val searchTarget: CelestialObjectId? = null,
) {
    /** A fixed event already in the past is shown greyed (but stays selectable), as in v1. */
    fun isPastAt(now: Instant): Boolean =
        type == TimeTravelEventType.FIXED && timestamp != null && timestamp < now
}

/**
 * The canonical picker contents, ported from v1 (same epoch timestamps, same order:
 * computed events first, then the fixed list). To add an event: a string in :shared:ui's
 * `strings.xml`, an entry here.
 */
object TimeTravelEvents {
    private val SUN = CelestialObjectId("planet/sun")
    private val MOON = CelestialObjectId("planet/moon")

    private fun fixed(
        displayNameRes: StringResource,
        epochMillis: Long,
        searchTarget: CelestialObjectId? = null,
    ) = TimeTravelEvent(
        displayNameRes,
        TimeTravelEventType.FIXED,
        Instant.fromEpochMilliseconds(epochMillis),
        searchTarget,
    )

    val ALL: List<TimeTravelEvent> =
        listOf(
            TimeTravelEvent(Res.string.time_travel_now, TimeTravelEventType.NOW),
            TimeTravelEvent(
                Res.string.time_travel_next_sunset,
                TimeTravelEventType.NEXT_SUNSET,
                searchTarget = SUN,
            ),
            TimeTravelEvent(
                Res.string.time_travel_next_sunrise,
                TimeTravelEventType.NEXT_SUNRISE,
                searchTarget = SUN,
            ),
            TimeTravelEvent(
                Res.string.time_travel_next_full_moon,
                TimeTravelEventType.NEXT_FULL_MOON,
                searchTarget = MOON,
            ),
            TimeTravelEvent(
                Res.string.time_travel_next_new_moon,
                TimeTravelEventType.NEXT_NEW_MOON,
                searchTarget = MOON,
            ),
            // 2026 events (chronological).
            fixed(
                Res.string.time_travel_six_planet_parade_2026,
                1772321400000L,
                CelestialObjectId("planet/saturn"),
            ),
            fixed(Res.string.time_travel_lunar_eclipse_2026, 1772537400000L, MOON),
            fixed(
                Res.string.time_travel_lyrids_2026,
                1776816000000L,
                CelestialObjectId("shower/lyrids"),
            ),
            fixed(
                Res.string.time_travel_venus_jupiter_2026,
                1781035200000L,
                CelestialObjectId("planet/venus"),
            ),
            fixed(
                Res.string.time_travel_mars_uranus_2026,
                1783206000000L,
                CelestialObjectId("planet/mars"),
            ),
            fixed(Res.string.time_travel_solar_eclipse_2026, 1786558200000L, SUN),
            fixed(
                Res.string.time_travel_perseids_2026,
                1786579200000L,
                CelestialObjectId("shower/perseids"),
            ),
            // Greatest eclipse, computed from core/astronomy's nextLunarEclipse (D106) rather
            // than hand-entered like the rest of this list — a deep partial (umbral magnitude
            // ~0.93), not quite total. Static for now; TimeTravelEvents is due a refactor to
            // compute events like this dynamically instead of pinning one year's calendar.
            fixed(Res.string.time_travel_lunar_eclipse_aug_2026, 1787890374633L, MOON),
            fixed(
                Res.string.time_travel_jupiter_occultation_2026,
                1791293400000L,
                CelestialObjectId("planet/jupiter"),
            ),
            fixed(
                Res.string.time_travel_geminids_2026,
                1797206400000L,
                CelestialObjectId("shower/geminids"),
            ),
            fixed(Res.string.time_travel_supermoon_2026, 1798149000000L, MOON),
            // Historical events.
            fixed(
                Res.string.time_travel_mercury_transit_2016,
                1462805846000L,
                CelestialObjectId("planet/mercury"),
            ),
            fixed(Res.string.time_travel_solar_eclipse_2024, 1712604000000L, SUN),
            fixed(Res.string.time_travel_apollo_11, -14182953622L, MOON),
            fixed(
                Res.string.time_travel_jupiter_saturn_2020,
                1608574800000L,
                CelestialObjectId("planet/jupiter"),
            ),
        )
}
