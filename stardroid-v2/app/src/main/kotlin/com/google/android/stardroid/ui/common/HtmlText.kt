/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

/** The private scheme the help document uses for links that go somewhere inside the app. */
const val APP_LINK_SCHEME = "skymap://"

/**
 * Whether an href is one of ours. Everything else — the `https:` and `mailto:` links the same
 * documents carry — has to reach the platform URI handler, so this is the fork that keeps them
 * working once a link listener is installed.
 */
internal fun isAppLink(url: String): Boolean = url.startsWith(APP_LINK_SCHEME)

/**
 * [AnnotatedString.fromHtml] with the theme's link styling: without an explicit
 * [TextLinkStyles], Compose renders `<a href>` runs indistinguishable from plain text even
 * though they respond to taps. Every HTML-driven dialog (EULA, What's New, Help, calibration)
 * renders through this so links look like links. The parse is remembered — these documents
 * are large enough that re-parsing per recomposition would be wasteful.
 *
 * [highlight] wraps every occurrence of a search term in [highlightColor], for Help's filter
 * box. [onInternalLink] receives [APP_LINK_SCHEME] hrefs — the help document's deep links into
 * the app — and everything else keeps going to the platform URI handler.
 *
 * The parse and the highlight overlay are remembered separately: [highlight] is Help's live
 * search query, so keying one `remember` on it would force the expensive `fromHtml` parse to
 * redo on every keystroke instead of just recomputing the (cheap) highlight spans.
 */
@Composable
fun htmlWithLinks(
    html: String,
    highlight: String? = null,
    highlightColor: Color = Color.Unspecified,
    onInternalLink: ((String) -> Unit)? = null,
): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    // Held by the listener baked into the parse below, which outlives any one composition.
    val uriHandler by rememberUpdatedState(LocalUriHandler.current)
    val internalLink by rememberUpdatedState(onInternalLink)
    // Whether there is a listener at all changes the parse; which listener it is does not.
    val routesInternally = onInternalLink != null
    val parsed =
        remember(html, linkColor, routesInternally) {
            val listener =
                if (routesInternally) {
                    // Supplying a listener *replaces* Compose's default URI opening, so the
                    // ordinary http and mailto links in the same document have to be handed to
                    // the URI handler by hand or they quietly stop working.
                    LinkInteractionListener { annotation ->
                        val url = (annotation as? LinkAnnotation.Url)?.url
                        when {
                            url == null -> Unit
                            isAppLink(url) -> internalLink?.invoke(url)
                            else -> uriHandler.openUri(url)
                        }
                    }
                } else {
                    null
                }
            AnnotatedString.fromHtml(
                html,
                linkStyles =
                    TextLinkStyles(
                        style =
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                    ),
                linkInteractionListener = listener,
            )
        }
    return remember(parsed, highlight, highlightColor) {
        parsed.withHighlight(highlight, highlightColor)
    }
}

/**
 * Overlays a background wash on every match of [query]. The ranges are computed against the
 * *rendered* text rather than the HTML source, so tags and entities can't throw the offsets
 * off by the length of their own markup.
 */
private fun AnnotatedString.withHighlight(
    query: String?,
    color: Color,
): AnnotatedString {
    if (query.isNullOrBlank()) return this
    val ranges = FoldedText.of(text).matchRanges(query)
    if (ranges.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withHighlight)
        for (range in ranges) {
            addStyle(SpanStyle(background = color), range.first, range.last + 1)
        }
    }
}
