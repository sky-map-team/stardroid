/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

/** Apple's App Review rejects links to outside donation pages, so iOS hides them for now. */
internal expect val donationLinksAllowed: Boolean

/** The Support section's HTML, or nothing where donation links aren't allowed. */
internal fun supportHtml(html: String): String = if (donationLinksAllowed) html else ""

/** [html] with Buy Me a Coffee links reduced to their text where donation links aren't allowed. */
internal fun withDonationLinks(html: String): String =
    if (donationLinksAllowed) html else stripDonationLinks(html)

internal fun stripDonationLinks(html: String): String =
    DONATION_LINK.replace(html) { it.groupValues[1] }

private val DONATION_LINK =
    Regex(
        """<a\s[^>]*href="https://buymeacoffee\.com/[^"]*"[^>]*>(.*?)</a>""",
        RegexOption.DOT_MATCHES_ALL,
    )
