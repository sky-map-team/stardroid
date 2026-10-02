/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package skymap.strings

/**
 * What aapt2 makes of a string resource's text, so the shared UI's strings read the same through
 * Compose Multiplatform's resources as they do through Android's own. The shared strings are
 * written in the Android resource format, which `tm` reads and writes, but Compose
 * Multiplatform's converter only decodes `\n`, `\t`, `\uXXXX` and `\\`: it would leave `\'` and
 * `\"` in the text, and keep whitespace Android collapses.
 *
 * The rules are aapt2's `StringBuilder::AppendText` (ResourceUtils.cpp):
 * - Outside double quotes, a run of ASCII whitespace becomes one space. Inside them, whitespace is
 *   kept as written.
 * - A leading or trailing space is then dropped, but only from a string with no unescaped `"`
 *   in it at all: one with an `<a href="...">` keeps both. aapt2's source doesn't say so; its
 *   output does, for every string of :app in every locale (checked against
 *   `aapt2 dump resources` when this was written: 604 trimmed, 25 kept, no exceptions).
 * - An unescaped `"` opens or closes a quoted run and is not itself part of the text.
 * - `\t`, `\n` and `\uXXXX` are a tab, a newline and that code point; `\#`, `\@`, `\?`, `\"`,
 *   `\'` and `\\` are the character itself; any other escaped character is kept without its
 *   backslash.
 * - An unescaped `'` outside quotes is an error, as it is to aapt2.
 *
 * [text] is the element's text after XML parsing, so CDATA is already unwrapped and entities
 * decoded; escapes inside CDATA are still processed, as aapt2 does.
 */
object AndroidStringText {
    fun process(text: String): String {
        val out = StringBuilder()
        var quoted = false
        var sawQuote = false
        var leadingSpace = false
        var lastWasCollapsedSpace = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!quoted && c.isAsciiSpace()) {
                if (!lastWasCollapsedSpace) {
                    if (out.isEmpty()) leadingSpace = true
                    out.append(' ')
                }
                lastWasCollapsedSpace = true
                i++
                continue
            }
            lastWasCollapsedSpace = false
            when {
                c == '\\' && i + 1 < text.length -> {
                    i++
                    when (val escaped = text[i]) {
                        't' -> out.append('\t')
                        'n' -> out.append('\n')
                        'u' -> {
                            val hex = text.substring(i + 1).take(4).takeWhile { it.isHexDigit() }
                            if (hex.isNotEmpty()) out.appendCodePoint(hex.toInt(16))
                            i += hex.length
                        }
                        else -> out.append(escaped)
                    }
                }
                // A trailing lone backslash is dropped, as aapt2 does.
                c == '\\' -> Unit
                c == '"' -> {
                    quoted = !quoted
                    sawQuote = true
                }
                c == '\'' && !quoted ->
                    throw IllegalArgumentException("unescaped apostrophe in string \"$text\"")
                else -> out.append(c)
            }
            i++
        }
        if (!sawQuote) {
            if (lastWasCollapsedSpace) out.setLength(out.length - 1)
            if (leadingSpace && out.isNotEmpty()) out.deleteCharAt(0)
        }
        return out.toString()
    }

    /** C's `isspace` over ASCII, as aapt2 tests it: a no-break space is text, not whitespace. */
    private fun Char.isAsciiSpace() = this in " \t\n\r\u000B\u000C"

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
