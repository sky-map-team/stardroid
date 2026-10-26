/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

/**
 * `mailto:address?subject=…&body=…`, every field percent-encoded, which is how a report's
 * newlines, `&` and `=` survive the trip into the mail draft. Android builds its own with
 * `Uri.encode` (`DiagnosticsShare`); this is iOS's, where there is no such helper.
 */
fun mailtoUrl(
    address: String,
    subject: String,
    body: String,
): String =
    "mailto:${percentEncode(address)}?subject=${percentEncode(subject)}" +
        "&body=${percentEncode(body)}"

/** Everything but letters, digits and RFC 3986's `-._~`, as UTF-8 bytes. */
internal fun percentEncode(text: String): String =
    buildString {
        for (byte in text.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 0x80 && (char.isLetterOrDigit() || char in "-._~")) {
                append(char)
            } else {
                append('%')
                append(HEX[code shr 4])
                append(HEX[code and 0xF])
            }
        }
    }

private const val HEX = "0123456789ABCDEF"
