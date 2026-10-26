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
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles

internal actual fun annotatedStringFromHtml(
    html: String,
    linkStyles: TextLinkStyles?,
    linkInteractionListener: LinkInteractionListener?,
): AnnotatedString = simpleHtmlToAnnotatedString(html, linkStyles, linkInteractionListener)
