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
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * The strings' HTML as an [AnnotatedString] where Compose Multiplatform 1.9 has no
 * `AnnotatedString.fromHtml` (iOS). It reads the subset the strings use and lays it out as
 * Android's `Html.fromHtml` does in the compact mode Compose's Android version asks for:
 *
 * - Block elements (`p`, `div`, `ul`, `ol`, `li`, `blockquote`, `h1`–`h6`) start and end on a
 *   line of their own: one newline between blocks, none at the very start, and the text keeps
 *   the newline that ends its last block.
 * - `br` is a newline. Spaces and newlines in the text collapse to one space, and a space at the
 *   start of a line is dropped.
 * - `b`/`strong` are bold, `i`/`em`/`cite`/`dfn` italic, `u` underlined, `a href` a link with
 *   [linkStyles] and [linkInteractionListener]. A list item is a "• " line, where Android draws
 *   a bullet.
 * - Other tags keep their text and lose their markup; entities decode.
 *
 * Headings and callouts rarely reach this: [splitHtmlBlocks] takes them out first.
 */
internal fun simpleHtmlToAnnotatedString(
    html: String,
    linkStyles: TextLinkStyles?,
    linkInteractionListener: LinkInteractionListener?,
): AnnotatedString {
    val out = SpannedText(linkStyles, linkInteractionListener)
    var index = 0
    while (index < html.length) {
        val tagStart = html.indexOf('<', index)
        val textEnd = if (tagStart < 0) html.length else tagStart
        if (textEnd > index) out.appendText(decodeEntities(html.substring(index, textEnd)))
        if (tagStart < 0) break
        val tagEnd = html.indexOf('>', tagStart)
        if (tagEnd < 0) {
            // An unclosed '<' is text, as an HTML parser reads it.
            out.appendText(html.substring(tagStart))
            break
        }
        Tag.parse(html.substring(tagStart + 1, tagEnd))?.let(out::handle)
        index = tagEnd + 1
    }
    return out.builder.toAnnotatedString()
}

private class Tag(
    val name: String,
    val closing: Boolean,
    val selfClosing: Boolean,
    val href: String?,
) {
    companion object {
        private val NAME = Regex("^/?\\s*([a-zA-Z][a-zA-Z0-9]*)")
        private val HREF =
            Regex("""href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

        fun parse(body: String): Tag? {
            val name = NAME.find(body)?.groupValues?.get(1)?.lowercase() ?: return null
            val href =
                HREF.find(body)
                    ?.groupValues
                    ?.drop(1)
                    ?.firstOrNull { it.isNotEmpty() }
                    ?.let(::decodeEntities)
            return Tag(name, body.trimStart().startsWith("/"), body.trimEnd().endsWith("/"), href)
        }
    }
}

private val BLOCK_TAGS =
    setOf("p", "div", "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6")
private val BOLD_TAGS = setOf("b", "strong")
private val ITALIC_TAGS = setOf("i", "em", "cite", "dfn")

/** The text being built, and the open tags whose spans close later. */
private class SpannedText(
    private val linkStyles: TextLinkStyles?,
    private val linkInteractionListener: LinkInteractionListener?,
) {
    val builder = AnnotatedString.Builder()

    /** The last character appended; a newline before any, as Android treats the start. */
    private var last = '\n'
    private val open = mutableListOf<Pair<Tag, Int>>()

    fun handle(tag: Tag) {
        when {
            // `<br>` and `<br/>`; Android ignores a lone `</br>` as TagSoup does.
            tag.name == "br" -> if (!tag.closing) append("\n")
            !tag.closing -> {
                if (tag.name in BLOCK_TAGS) startLine()
                if (tag.name == "li") append("• ")
                if (!tag.selfClosing) open += tag to builder.length
            }
            else -> close(tag.name)
        }
    }

    /** Closes the innermost open tag of this name; a stray closing tag is ignored. */
    private fun close(name: String) {
        val index = open.indexOfLast { it.first.name == name }
        if (index < 0) return
        val (tag, start) = open.removeAt(index)
        val end = builder.length
        when {
            name in BLOCK_TAGS -> startLine()
            start == end -> Unit
            name in BOLD_TAGS ->
                builder.addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start,
                    end,
                )
            name in ITALIC_TAGS ->
                builder.addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
            name == "u" ->
                builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            name == "a" && tag.href != null ->
                builder.addLink(
                    LinkAnnotation.Url(tag.href, linkStyles, linkInteractionListener),
                    start,
                    end,
                )
        }
    }

    /** Android's `appendNewlines(text, 1)`: a block starts a line, unless it starts the text. */
    private fun startLine() {
        if (builder.length > 0 && last != '\n') append("\n")
    }

    /** Android's `handleCharacters`: spaces and newlines collapse, and never start a line. */
    fun appendText(text: String) {
        val collapsed = StringBuilder(text.length)
        var previous = last
        for (c in text) {
            if (c == ' ' || c == '\n') {
                if (previous == ' ' || previous == '\n') continue
                collapsed.append(' ')
                previous = ' '
            } else {
                collapsed.append(c)
                previous = c
            }
        }
        append(collapsed.toString())
    }

    private fun append(text: String) {
        if (text.isEmpty()) return
        builder.append(text)
        last = text.last()
    }
}

private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES =
    mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0",
    )

private fun decodeEntities(text: String): String =
    ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        val codePoint =
            when {
                body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)
                body.startsWith("#") -> body.drop(1).toIntOrNull()
                else -> null
            }
        when {
            codePoint == null -> NAMED_ENTITIES[body] ?: match.value
            codePoint <= 0xFFFF -> codePoint.toChar().toString()
            else -> match.value
        }
    }
