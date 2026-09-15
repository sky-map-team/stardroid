/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.stardroid.R

/**
 * Hands a formatted diagnostics report to the user's mail client, addressed to the developers.
 *
 * `ACTION_SENDTO` with a `mailto:` URI is the right intent here rather than the general share
 * sheet: it resolves only to mail apps, and it pre-fills the recipient, so the user does not
 * have to know or type the address. The address is the one the app already publishes in the
 * help text and the one in `SECURITY.md`.
 *
 * **The subject and body go in the URI, not in intent extras.** Gmail's `ACTION_SENDTO`
 * handler silently drops `EXTRA_SUBJECT`/`EXTRA_TEXT` — verified on a Pixel 5 — and opens an
 * empty draft, which is worse than useless because it looks like it worked. `mailto:`'s own
 * `?subject=&body=` query is what every client honours. The extras are still set on the
 * `ACTION_SEND` fallback below, where they are the only mechanism there is.
 *
 * The report lands in the composer as an **editable body**. That is the consent model: the user
 * sees exactly what would be sent — including their coordinates — and can trim it or abandon it.
 * The app never transmits anything itself.
 *
 * If no mail client is installed (common on tablets and emulators), we fall back to the general
 * share sheet with plain text, so the report can still reach a notes app, a messaging app, or
 * the clipboard.
 */
object DiagnosticsShare {
    fun send(
        context: Context,
        subject: String,
        body: String,
    ) {
        val address = context.getString(R.string.support_email)
        val mail = Intent(Intent.ACTION_SENDTO, mailtoUri(address, subject, body))
        try {
            context.startActivity(mail)
            return
        } catch (_: ActivityNotFoundException) {
            // No mail client — fall through to the general share sheet.
        }
        val share =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
        val chooser = Intent.createChooser(share, subject)
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            // Nothing on the device can take text at all; there is no third option worth
            // building, and failing silently beats crashing the diagnostics screen.
        }
    }

    /**
     * `mailto:address?subject=…&body=…` with both fields percent-encoded, which is how the
     * newlines in the report survive the trip into the composer.
     */
    internal fun mailtoUri(
        address: String,
        subject: String,
        body: String,
    ): Uri =
        Uri.parse(
            "mailto:" + Uri.encode(address) +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body),
        )
}
