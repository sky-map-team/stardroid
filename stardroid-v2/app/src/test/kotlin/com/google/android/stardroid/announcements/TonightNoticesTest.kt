/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import com.google.android.stardroid.catalog.LocaleSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class TonightNoticesTest {
    private val start = Instant.parse("2026-10-03T16:00:00Z")
    private val end = Instant.parse("2026-10-04T10:00:00Z")
    private val during = Instant.parse("2026-10-03T20:00:00Z")

    private fun ann(
        id: String,
        action: AnnouncementAction,
        text: Map<String, AnnouncementText> = mapOf("en" to AnnouncementText("Title", "Body")),
    ) = Announcement(id, start, end, setOf(Surface.WIDGET), 0, text, action)

    private fun notices(
        vararg messages: Announcement,
        seen: Map<String, SeenRecord> = emptyMap(),
    ) = tonightNotices(
        during,
        object : AnnouncementSource {
            override suspend fun refresh() = Unit

            override fun current() = messages.toList()
        },
        seen,
        appVersion = 2000,
        locale = LocaleSpec("en"),
    )

    @Test
    fun `a search action becomes the notice's search query`() {
        val result = notices(ann("a", AnnouncementAction.Search("Voyager 1")))

        assertThat(result.map { it.searchQuery }).containsExactly("Voyager 1")
        assertThat(result.single().title).isEqualTo("Title")
    }

    @Test
    fun `a message with no text for the locale is dropped`() {
        val noText =
            ann("a", AnnouncementAction.OpenSky, text = mapOf("fr" to AnnouncementText("T", "B")))

        assertThat(notices(noText)).isEmpty()
    }

    @Test
    fun `a dismissed message is not shown`() {
        val result =
            notices(
                ann("a", AnnouncementAction.OpenSky),
                seen = mapOf("a" to SeenRecord(dismissed = true)),
            )

        assertThat(result).isEmpty()
    }
}
