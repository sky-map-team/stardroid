/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.moonWidgetModel
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

class MoonWidgetTimelineTest {
    private val start = Instant.parse("2026-09-22T15:00:00Z")
    private val london = LatLong(51.5, -0.1)

    @Test
    fun `a day of hourly entries with both ends included`() {
        val entries = moonWidgetTimeline(start, london)
        assertThat(entries).hasSize(25)
        assertThat(entries.first().time).isEqualTo(start)
        assertThat(entries.last().time).isEqualTo(start + 24.hours)
    }

    @Test
    fun `each entry is the widget model at its own time`() {
        val entry = moonWidgetTimeline(start, london)[5]
        assertThat(entry.model).isEqualTo(moonWidgetModel(start + 5.hours, london))
    }

    @Test
    fun `without a location the entries have no crossings`() {
        val entries = moonWidgetTimeline(start, null, span = 2.hours)
        assertThat(entries).hasSize(3)
        assertThat(entries.all { it.model.riseTime == null && it.model.setTime == null }).isTrue()
    }
}
