/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

/** [dayOffset]: the moon widget's local-day arithmetic behind its "+1"-style suffix (#1067). */
class MoonWidgetTest {
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
        // 23:59 London one night vs. 00:01 London the next: 2 minutes apart in real time,
        // but a different calendar day either side of the boundary.
        val now = Instant.parse("2026-09-22T22:59:00Z")
        val time = Instant.parse("2026-09-22T23:01:00Z")
        assertThat(dayOffset(now, time, london)).isEqualTo(1)
    }
}
