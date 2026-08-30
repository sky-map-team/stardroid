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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

/**
 * [AnnotatedString.fromHtml] with the theme's link styling: without an explicit
 * [TextLinkStyles], Compose renders `<a href>` runs indistinguishable from plain text even
 * though they respond to taps. Every HTML-driven dialog (EULA, What's New, Help, calibration)
 * renders through this so links look like links. The parse is remembered — these documents
 * are large enough that re-parsing per recomposition would be wasteful.
 */
@Composable
fun htmlWithLinks(html: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(html, linkColor) {
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
        )
    }
}
