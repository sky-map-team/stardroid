/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.time_travel_geminids_2026
import com.google.android.stardroid.ui.resources.time_travel_lunar_eclipse_2026
import com.google.android.stardroid.ui.resources.time_travel_lunar_eclipse_aug_2026
import com.google.android.stardroid.ui.resources.time_travel_lyrids_2026
import com.google.android.stardroid.ui.resources.time_travel_now
import com.google.android.stardroid.ui.resources.time_travel_perseids_2026
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class TimeTravelEventsTest {
    private val showerEvents =
        setOf(
            Res.string.time_travel_lyrids_2026,
            Res.string.time_travel_perseids_2026,
            Res.string.time_travel_geminids_2026,
        )

    /**
     * Travelling to a shower must land where searching for it lands. Targeting the parent
     * constellation instead puts the map ~11 degrees off the radiant, at the wrong zoom.
     */
    @Test
    fun `shower events aim at the radiant - not the parent constellation`() {
        val targets =
            TimeTravelEvents.ALL
                .filter { it.displayNameRes in showerEvents }
                .map { it.searchTarget?.value }
        assertEquals(
            setOf("shower/lyrids", "shower/perseids", "shower/geminids"),
            targets.toSet(),
        )
        assertEquals(3, targets.size)
    }

    @Test
    fun `the 2026 lunar eclipse events are chronological and target the Moon`() {
        val eclipses =
            TimeTravelEvents.ALL.filter {
                it.displayNameRes == Res.string.time_travel_lunar_eclipse_2026 ||
                    it.displayNameRes == Res.string.time_travel_lunar_eclipse_aug_2026
            }
        assertEquals(listOf("planet/moon", "planet/moon"), eclipses.map { it.searchTarget?.value })
        val timestamps = eclipses.map { it.timestamp }
        assertEquals(timestamps.sortedBy { it }, timestamps)
    }

    @Test
    fun `past fixed events know they are past`() {
        val now = Instant.fromEpochMilliseconds(1_780_000_000_000L)
        val past =
            TimeTravelEvent(
                Res.string.time_travel_now,
                TimeTravelEventType.FIXED,
                Instant.fromEpochMilliseconds(0),
            )
        val future =
            TimeTravelEvent(Res.string.time_travel_now, TimeTravelEventType.FIXED, now + 3650.days)
        assertTrue(past.isPastAt(now))
        assertFalse(future.isPastAt(now))
    }
}
