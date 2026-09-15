/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.ui.theme.DocumentColors
import com.google.android.stardroid.ui.theme.documentColors

/**
 * The styled document renderer for the long-form HTML strings (EULA, What's New, Help,
 * calibration): [AnnotatedString.fromHtml] flattens block structure to default typography, so
 * this splits the document on its block tags and renders each as its own composable —
 * headings with Material typography and v1's `help.css` accent colors, `<blockquote>` as a
 * highlighted callout. Body runs render through [htmlWithLinks], keeping D48's no-WebView rule.
 */
@Composable
fun StyledHtml(
    html: String,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
    bodyStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(html) { splitHtmlBlocks(html) }
    val colors = documentColors(nightMode)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (block in blocks) {
            when (block) {
                is HtmlBlock.Body ->
                    Text(htmlWithLinks(block.html), style = bodyStyle)
                is HtmlBlock.Heading ->
                    Text(
                        htmlWithLinks(block.html),
                        style = headingStyle(block.level),
                        color = colors.headings[block.level - 1],
                        // Breathing room above a heading, tighter to the text it titles —
                        // the spacing v1's help.css margins gave the WebView document.
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                is HtmlBlock.Callout -> Callout(block.html, colors, bodyStyle)
            }
        }
    }
}

/**
 * An accent-barred, tonally filled note — the block-level highlight `AnnotatedString` cannot
 * express, since it carries only inline spans. The bar stretches to the text's height via
 * [IntrinsicSize.Min] so it tracks multi-line notes.
 */
@Composable
private fun Callout(
    html: String,
    colors: DocumentColors,
    bodyStyle: TextStyle,
) {
    Row(
        Modifier
            // The outer Column already spaces blocks by 4dp; this adds the rest of the gap a
            // callout wants without doubling it.
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.calloutBackground)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(colors.calloutAccent),
        )
        Text(
            htmlWithLinks(html),
            style = bodyStyle,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle =
    when (level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }

/** One run of a split document. */
internal sealed interface HtmlBlock {
    /** Inline content rendered as flowing prose. */
    data class Body(
        val html: String,
    ) : HtmlBlock

    /** An `<h1>`–`<h3>`; [level] is 1–3. */
    data class Heading(
        val level: Int,
        val html: String,
    ) : HtmlBlock

    /** A `<blockquote>` — an important note lifted out of the prose. */
    data class Callout(
        val html: String,
    ) : HtmlBlock
}

// Headings and callouts in one alternation so a single scan keeps them in document order.
// Group 1 = heading level, 2 = heading content, 3 = callout content.
private val BLOCK_TAG =
    Regex(
        "<h([1-3])[^>]*>(.*?)</h\\1\\s*>|<blockquote[^>]*>(.*?)</blockquote\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

/**
 * Splits an HTML document into body, heading and callout blocks on its `<h1>`–`<h3>` and
 * `<blockquote>` tags. Whitespace-only body runs between blocks are dropped so they don't
 * become empty paragraphs.
 *
 * The help strings separate paragraphs with `<br/><br/>` rather than `<p>`, so a callout
 * mid-run splits that run in two; the trailing `<br/>`s left dangling either side are trimmed
 * so the break doesn't leave a blank line behind.
 */
internal fun splitHtmlBlocks(html: String): List<HtmlBlock> =
    buildList {
        var cursor = 0
        for (match in BLOCK_TAG.findAll(html)) {
            val body = html.substring(cursor, match.range.first).trimSurroundingBreaks()
            if (body.isNotBlank()) add(HtmlBlock.Body(body))
            val headingLevel = match.groupValues[1]
            if (headingLevel.isNotEmpty()) {
                add(HtmlBlock.Heading(headingLevel.toInt(), match.groupValues[2].trim()))
            } else {
                add(HtmlBlock.Callout(match.groupValues[3].trim()))
            }
            cursor = match.range.last + 1
        }
        val tail = html.substring(cursor).trimSurroundingBreaks()
        if (tail.isNotBlank()) add(HtmlBlock.Body(tail))
    }

private val LEADING_BREAKS = Regex("^(?:\\s|<br\\s*/?>)+", RegexOption.IGNORE_CASE)
private val TRAILING_BREAKS = Regex("(?:\\s|<br\\s*/?>)+$", RegexOption.IGNORE_CASE)

/** Drops `<br/>` runs and whitespace at either end of a body chunk. */
private fun String.trimSurroundingBreaks(): String =
    replace(LEADING_BREAKS, "").replace(TRAILING_BREAKS, "")
