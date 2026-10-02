/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class AnnouncementPolicyTest {
    private val start = Instant.parse("2026-10-03T16:00:00Z")
    private val end = Instant.parse("2026-10-04T10:00:00Z")
    private val during = Instant.parse("2026-10-03T20:00:00Z")

    private fun ann(
        id: String = "a",
        surfaces: Set<Surface> = Surface.entries.toSet(),
        minVersion: Long = 0,
        start: Instant = this.start,
    ) = Announcement(
        id,
        start,
        end,
        surfaces,
        minVersion,
        mapOf("en" to AnnouncementText("t", "b")),
        AnnouncementAction.OpenSky,
    )

    private fun next(
        surface: Surface,
        active: List<Announcement>,
        seen: Map<String, SeenRecord> = emptyMap(),
        now: Instant = during,
        version: Long = 2000,
    ) = AnnouncementPolicy.next(surface, now, active, seen, version)

    @Test
    fun `fresh message is offered on each of its surfaces`() {
        for (s in Surface.entries) assertThat(next(s, listOf(ann()))?.id).isEqualTo("a")
    }

    @Test
    fun `surface not in the message is skipped`() {
        val a = ann(surfaces = setOf(Surface.WIDGET))
        assertThat(next(Surface.NOTIFICATION, listOf(a))).isNull()
        assertThat(next(Surface.INTERSTITIAL, listOf(a))).isNull()
    }

    @Test
    fun `notification shown suppresses the interstitial`() {
        val seen = mapOf("a" to SeenRecord(shownOn = setOf(Surface.NOTIFICATION)))
        assertThat(next(Surface.INTERSTITIAL, listOf(ann()), seen)).isNull()
        assertThat(next(Surface.NOTIFICATION, listOf(ann()), seen)).isNull()
    }

    @Test
    fun `interstitial shown suppresses a later notification`() {
        val seen = mapOf("a" to SeenRecord(shownOn = setOf(Surface.INTERSTITIAL)))
        assertThat(next(Surface.NOTIFICATION, listOf(ann()), seen)).isNull()
    }

    @Test
    fun `widget is exempt from interruptive dedup`() {
        val seen =
            mapOf("a" to SeenRecord(shownOn = setOf(Surface.NOTIFICATION, Surface.INTERSTITIAL)))
        assertThat(next(Surface.WIDGET, listOf(ann()), seen)?.id).isEqualTo("a")
    }

    @Test
    fun `dismissal hides every surface including the widget`() {
        val seen = mapOf("a" to SeenRecord(dismissed = true))
        for (s in Surface.entries) assertThat(next(s, listOf(ann()), seen)).isNull()
    }

    @Test
    fun `outside the window nothing shows`() {
        assertThat(next(Surface.WIDGET, listOf(ann()), now = start.minusSecond())).isNull()
        assertThat(next(Surface.WIDGET, listOf(ann()), now = end)).isNull()
    }

    @Test
    fun `min version gates older builds`() {
        assertThat(next(Surface.WIDGET, listOf(ann(minVersion = 2001)))).isNull()
        assertThat(next(Surface.WIDGET, listOf(ann(minVersion = 2000)))?.id).isEqualTo("a")
    }

    @Test
    fun `newest eligible message wins and an already-seen one is skipped`() {
        val older = ann(id = "older", start = Instant.parse("2026-10-03T00:00:00Z"))
        val newer = ann(id = "newer")
        assertThat(next(Surface.NOTIFICATION, listOf(older, newer))?.id).isEqualTo("newer")
        val seen = mapOf("newer" to SeenRecord(shownOn = setOf(Surface.NOTIFICATION)))
        assertThat(next(Surface.NOTIFICATION, listOf(older, newer), seen)?.id).isEqualTo("older")
    }

    private fun Instant.minusSecond() = Instant.fromEpochSeconds(epochSeconds - 1)
}
