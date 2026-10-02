/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

/** The expected text is what Android's `Html.fromHtml` gives in compact mode. */
class SimpleHtmlTest {
    private fun parse(html: String): AnnotatedString = simpleHtmlToAnnotatedString(html, null, null)

    @Test
    fun `paragraphs are one newline apart and keep the last one`() {
        assertEquals("A\nB\n", parse("<p>A</p><p>B</p>").text)
    }

    @Test
    fun `whitespace collapses and never starts a line`() {
        assertEquals("Hello world \n", parse("<p>\n  Hello\n  world  </p>").text)
    }

    @Test
    fun `a break is a newline`() {
        assertEquals("A\nB", parse("A<br/>B").text)
        assertEquals("A\nB", parse("A<br>B").text)
    }

    @Test
    fun `a break before a paragraph does not double up`() {
        assertEquals(
            "\nBy clicking \"Accept\" you agree.\n",
            parse("<br/> <p><i>By clicking \"Accept\" you agree.</i></p>").text,
        )
    }

    @Test
    fun `bold and italic become spans over their text`() {
        val parsed = parse("<p><b>We</b> and <em>you</em></p>")

        val styles = parsed.spanStyles.map { parsed.text.substring(it.start, it.end) to it.item }
        assertEquals("We", styles[0].first)
        assertEquals(FontWeight.Bold, styles[0].second.fontWeight)
        assertEquals("you", styles[1].first)
        assertEquals(FontStyle.Italic, styles[1].second.fontStyle)
    }

    @Test
    fun `a link carries its href`() {
        val parsed = parse("Read <a href=\"https://example.test/terms\">the terms</a>.")

        val link = parsed.getLinkAnnotations(0, parsed.length).single()
        assertEquals("the terms", parsed.text.substring(link.start, link.end))
        assertEquals("https://example.test/terms", (link.item as LinkAnnotation.Url).url)
    }

    @Test
    fun `an unquoted href is read too`() {
        val parsed = parse("<a href=skymap://settings>Settings</a>")

        val link = parsed.getLinkAnnotations(0, parsed.length).single().item
        assertEquals("skymap://settings", (link as LinkAnnotation.Url).url)
    }

    @Test
    fun `entities decode`() {
        assertEquals(
            "Safety & Liability <3 \"x\"",
            parse("Safety &amp; Liability &lt;3 &quot;x&quot;").text,
        )
        assertEquals("A\u00A0B é", parse("A&nbsp;B &#233;").text)
    }

    @Test
    fun `list items are bullet lines`() {
        assertEquals("• One\n• Two\n", parse("<ul><li>One</li><li>Two</li></ul>").text)
    }

    @Test
    fun `unknown tags keep their text`() {
        assertEquals("plain", parse("<span class=\"x\">plain</span>").text)
    }

    @Test
    fun `a stray closing tag is ignored`() {
        assertEquals("text", parse("text</b>").text)
    }
}
