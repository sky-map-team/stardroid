/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StyledHtmlTest {
    @Test
    fun `document without headings is a single body block`() {
        val blocks = splitHtmlBlocks("<p>Just a paragraph.</p>")

        assertThat(blocks).containsExactly(HtmlBlock.Body("<p>Just a paragraph.</p>"))
    }

    @Test
    fun `headings split the document preserving order`() {
        val blocks =
            splitHtmlBlocks(
                "<h1>Title</h1><p>Intro</p><h2>Section</h2><p>Body</p>",
            )

        assertThat(blocks)
            .containsExactly(
                HtmlBlock.Heading(1, "Title"),
                HtmlBlock.Body("<p>Intro</p>"),
                HtmlBlock.Heading(2, "Section"),
                HtmlBlock.Body("<p>Body</p>"),
            ).inOrder()
    }

    @Test
    fun `all three heading levels are recognized`() {
        val blocks = splitHtmlBlocks("<h1>A</h1><h2>B</h2><h3>C</h3>")

        assertThat(blocks.filterIsInstance<HtmlBlock.Heading>().map { it.level })
            .containsExactly(1, 2, 3)
            .inOrder()
    }

    @Test
    fun `whitespace between headings is dropped`() {
        val blocks = splitHtmlBlocks("<h1>A</h1>\n\n  <h2>B</h2>")

        assertThat(blocks).hasSize(2)
    }

    @Test
    fun `heading markup and entities stay inside the heading block`() {
        val blocks = splitHtmlBlocks("<h2>Safety &amp; Liability</h2>")

        assertThat(blocks).containsExactly(HtmlBlock.Heading(2, "Safety &amp; Liability"))
    }

    @Test
    fun `mismatched heading close tags do not pair across levels`() {
        // An <h1> closed by </h2> is malformed; it must not swallow the document.
        val blocks = splitHtmlBlocks("<h1>A</h2><p>text</p>")

        assertThat(blocks.single()).isInstanceOf(HtmlBlock.Body::class.java)
    }

    @Test
    fun `case-insensitive tags and attributes are matched`() {
        val blocks = splitHtmlBlocks("<H1 class=\"x\">Title</H1>rest")

        assertThat(blocks)
            .containsExactly(HtmlBlock.Heading(1, "Title"), HtmlBlock.Body("rest"))
            .inOrder()
    }

    @Test
    fun `multiline heading content is captured`() {
        val blocks = splitHtmlBlocks("<h2>Two\nlines</h2>")

        assertThat(blocks).containsExactly(HtmlBlock.Heading(2, "Two\nlines"))
    }

    @Test
    fun `blockquote becomes a callout block`() {
        val blocks = splitHtmlBlocks("<blockquote><b>Note:</b> read this.</blockquote>")

        assertThat(blocks).containsExactly(HtmlBlock.Callout("<b>Note:</b> read this."))
    }

    @Test
    fun `callout splits the body run it sits in, keeping order`() {
        val blocks = splitHtmlBlocks("Before<blockquote>Note</blockquote>After")

        assertThat(blocks)
            .containsExactly(
                HtmlBlock.Body("Before"),
                HtmlBlock.Callout("Note"),
                HtmlBlock.Body("After"),
            ).inOrder()
    }

    @Test
    fun `breaks around a callout are trimmed so no blank line remains`() {
        // The help strings separate paragraphs with <br/><br/>, which would otherwise be left
        // dangling on the body runs either side of the callout.
        val blocks = splitHtmlBlocks("Before<br/><br/><blockquote>Note</blockquote><br/><br/>After")

        assertThat(blocks)
            .containsExactly(
                HtmlBlock.Body("Before"),
                HtmlBlock.Callout("Note"),
                HtmlBlock.Body("After"),
            ).inOrder()
    }

    @Test
    fun `a body run of only breaks is dropped entirely`() {
        val blocks = splitHtmlBlocks("<h2>A</h2><br/><br/><blockquote>Note</blockquote>")

        assertThat(blocks)
            .containsExactly(HtmlBlock.Heading(2, "A"), HtmlBlock.Callout("Note"))
            .inOrder()
    }

    @Test
    fun `headings and callouts interleave in document order`() {
        val blocks =
            splitHtmlBlocks("<h1>T</h1>intro<blockquote>N</blockquote><h2>S</h2>body")

        assertThat(blocks)
            .containsExactly(
                HtmlBlock.Heading(1, "T"),
                HtmlBlock.Body("intro"),
                HtmlBlock.Callout("N"),
                HtmlBlock.Heading(2, "S"),
                HtmlBlock.Body("body"),
            ).inOrder()
    }

    @Test
    fun `links inside a callout survive the split`() {
        val blocks =
            splitHtmlBlocks("<blockquote>See <a href=\"http://x.test\">this</a>.</blockquote>")

        assertThat(blocks)
            .containsExactly(HtmlBlock.Callout("See <a href=\"http://x.test\">this</a>."))
    }
}
