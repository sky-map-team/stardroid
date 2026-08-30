/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

import com.google.android.stardroid.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TimeTravelEventsTest {
    private val showerEvents =
        setOf(
            R.string.time_travel_lyrids_2026,
            R.string.time_travel_perseids_2026,
            R.string.time_travel_geminids_2026,
        )

    /**
     * Travelling to a shower must land where searching for it lands. Targeting the parent
     * constellation instead puts the map ~11 degrees off the radiant, at the wrong zoom.
     */
    @Test
    fun `shower events aim at the radiant, not the parent constellation`() {
        val targets =
            TimeTravelEvents.ALL
                .filter { it.displayNameRes in showerEvents }
                .map { it.searchTarget?.value }
        assertThat(targets)
            .containsExactly("shower/lyrids", "shower/perseids", "shower/geminids")
    }

    @Test
    fun `the 2026 lunar eclipse events are chronological and target the Moon`() {
        val eclipses =
            TimeTravelEvents.ALL.filter {
                it.displayNameRes == R.string.time_travel_lunar_eclipse_2026 ||
                    it.displayNameRes == R.string.time_travel_lunar_eclipse_aug_2026
            }
        assertThat(eclipses).hasSize(2)
        assertThat(
            eclipses.map { it.searchTarget?.value },
        ).containsExactly("planet/moon", "planet/moon")
        val timestamps = eclipses.map { it.timestamp }
        assertThat(timestamps).isEqualTo(timestamps.sortedBy { it })
    }
}
