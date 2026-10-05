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
import com.google.android.stardroid.catalog.LocaleSpec
import org.junit.jupiter.api.Test

class AnnouncementParserTest {
    private fun doc(vararg messages: String) =
        """{"v":1,"messages":[${messages.joinToString(",")}]}"""

    private fun message(
        id: String = "a",
        start: String = "2026-10-03T16:00:00Z",
        end: String = "2026-10-04T10:00:00Z",
        surfaces: String = """["notification","widget"]""",
        text: String = """{"en":{"title":"Aurora","body":"Look north"}}""",
        extra: String = "",
    ) = """{"id":"$id","start":"$start","end":"$end","surfaces":$surfaces,"text":$text$extra}"""

    @Test
    fun `parses a full message`() {
        val result = AnnouncementParser.parse(doc(message(extra = ""","min_version":1760""")))
        assertThat(result).hasSize(1)
        val a = result.single()
        assertThat(a.id).isEqualTo("a")
        assertThat(a.surfaces).containsExactly(Surface.NOTIFICATION, Surface.WIDGET)
        assertThat(a.minVersion).isEqualTo(1760L)
        assertThat(a.action).isEqualTo(AnnouncementAction.OpenSky)
    }

    @Test
    fun `garbage and wrong schema yield nothing`() {
        assertThat(AnnouncementParser.parse("")).isEmpty()
        assertThat(AnnouncementParser.parse("not json")).isEmpty()
        assertThat(AnnouncementParser.parse("[1,2]")).isEmpty()
        assertThat(AnnouncementParser.parse("""{"v":2,"messages":[${message()}]}""")).isEmpty()
    }

    @Test
    fun `a bad message is dropped without losing its neighbours`() {
        val result =
            AnnouncementParser.parse(
                doc(
                    message(id = "good"),
                    message(id = "no-surface", surfaces = """["pigeon"]"""),
                    message(id = "backwards", start = "2026-10-05T00:00:00Z"),
                    message(id = "no-text", text = "{}"),
                    """{"id":"bad-date","start":"yesterday"}""",
                    "42",
                ),
            )
        assertThat(result.map { it.id }).containsExactly("good")
    }

    @Test
    fun `unknown surfaces are ignored but known ones kept`() {
        val a = AnnouncementParser.parse(doc(message(surfaces = """["pigeon","widget"]"""))).single()
        assertThat(a.surfaces).containsExactly(Surface.WIDGET)
    }

    @Test
    fun `newest first, capped, duplicate ids collapsed`() {
        val result =
            AnnouncementParser.parse(
                doc(
                    message(id = "old", start = "2026-10-01T00:00:00Z"),
                    message(id = "new", start = "2026-10-03T00:00:00Z"),
                    message(id = "new", start = "2026-10-03T00:00:00Z"),
                    message(id = "mid", start = "2026-10-02T00:00:00Z"),
                    message(id = "oldest", start = "2026-09-30T00:00:00Z"),
                ),
            )
        assertThat(result.map { it.id }).containsExactly("new", "mid", "old").inOrder()
    }

    @Test
    fun `search action needs an argument`() {
        val ok = AnnouncementParser.parse(doc(message(extra = ""","action":{"type":"search","arg":"Saturn"}""")))
        assertThat(ok.single().action).isEqualTo(AnnouncementAction.Search("Saturn"))
        val bare = AnnouncementParser.parse(doc(message(extra = ""","action":{"type":"search"}""")))
        assertThat(bare.single().action).isEqualTo(AnnouncementAction.OpenSky)
    }

    @Test
    fun `localization walks the fallback chain`() {
        val text =
            """{"en":{"title":"Aurora"},"pt":{"title":"Aurora PT"},"zh-Hans":{"title":"极光"}}"""
        val a = AnnouncementParser.parse(doc(message(text = text))).single()
        assertThat(a.localized(LocaleSpec("pt-BR"))?.title).isEqualTo("Aurora PT")
        assertThat(a.localized(LocaleSpec("zh-Hans-CN"))?.title).isEqualTo("极光")
        assertThat(a.localized(LocaleSpec("de"))?.title).isEqualTo("Aurora")
    }

    @Test
    fun `no english and no match yields null`() {
        val a =
            AnnouncementParser.parse(doc(message(text = """{"de":{"title":"Nordlicht"}}"""))).single()
        assertThat(a.localized(LocaleSpec("fr"))).isNull()
    }
}
