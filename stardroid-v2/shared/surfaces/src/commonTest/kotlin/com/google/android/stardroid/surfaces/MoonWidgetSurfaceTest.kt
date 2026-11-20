/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.MoonWidgetModel
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test

class MoonWidgetSurfaceTest {
    private val london = TimeZone.of("Europe/London")

    @Test
    fun `same local day is zero`() {
        // 15:35 and 22:45 UTC are both 16:35/23:45 BST on the same September day.
        val now = Instant.parse("2026-09-22T15:35:00Z")
        val time = Instant.parse("2026-09-22T22:45:00Z")
        assertThat(dayOffset(now, time, london)).isEqualTo(0)
    }

    @Test
    fun `next local day is one`() {
        val now = Instant.parse("2026-09-22T21:00:00Z")
        val time = Instant.parse("2026-09-23T02:45:00Z")
        assertThat(dayOffset(now, time, london)).isEqualTo(1)
    }

    @Test
    fun `several local days ahead`() {
        val now = Instant.parse("2026-09-22T12:00:00Z")
        val time = Instant.parse("2026-09-25T12:00:00Z")
        assertThat(dayOffset(now, time, london)).isEqualTo(3)
    }

    @Test
    fun `crossing just after local midnight still counts as the next day`() {
        // 2 minutes apart in real time, but either side of London's midnight.
        val now = Instant.parse("2026-09-22T22:59:00Z")
        val time = Instant.parse("2026-09-22T23:01:00Z")
        assertThat(dayOffset(now, time, london)).isEqualTo(1)
    }

    @Test
    fun `crossings come in the order they happen`() {
        val rise = Instant.parse("2026-09-23T18:36:00Z")
        val set = Instant.parse("2026-09-23T05:56:00Z")
        assertThat(model(rise, set).crossingsInOrder)
            .containsExactly(MoonCrossing(rise = false, time = set), MoonCrossing(true, rise))
            .inOrder()
    }

    @Test
    fun `a crossing that did not converge is left out`() {
        val rise = Instant.parse("2026-09-23T18:36:00Z")
        assertThat(model(rise, null).crossingsInOrder).containsExactly(MoonCrossing(true, rise))
        assertThat(model(null, null).crossingsInOrder).isEmpty()
    }

    private fun model(
        rise: Instant?,
        set: Instant?,
    ) = MoonWidgetModel(
        phase = LunarPhase.WAXING_GIBBOUS,
        illuminatedFraction = 0.8,
        waxing = true,
        ageDays = 10.0,
        mirrored = false,
        riseTime = rise,
        setTime = set,
    )
}
