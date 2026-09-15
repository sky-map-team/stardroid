/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import androidx.compose.ui.graphics.Color

/** One label/value pair on the diagnostics screen, and in the report built from it. */
data class DiagnosticsRow(
    val label: String,
    val value: String,
    val valueColor: Color = Color.Unspecified,
)

/** A titled group of [DiagnosticsRow]s — one section heading on screen. */
data class DiagnosticsSection(
    val title: String,
    val rows: List<DiagnosticsRow>,
)

/**
 * Renders the diagnostics screen's sections as the plain text we ask users to send us.
 *
 * The screen builds its sections as data and both draws them and formats them here, so the
 * report is the screen — there is no second description of the same facts to drift out of date.
 * That matters because the help text has always told users to send "a screenshot of this page";
 * the report has to be worth at least as much as the screenshot was.
 *
 * **On privacy:** the report contains whatever the screen shows, which includes the user's
 * latitude and longitude. That is deliberate — a sky-position bug is unreproducible without it —
 * and it is safe because of how it is delivered: the text goes into the user's own mail
 * composer as an editable body, so it is read and consciously sent, never transmitted by the
 * app itself. Nothing here leaves the device without the user pressing send in their own client.
 */
object DiagnosticsReport {
    /**
     * Formats [sections] as a flat text report under [header].
     *
     * One `Label: value` per line. An earlier version padded labels to a common width to make a
     * column, which looked right in a monospaced editor and wrong everywhere it actually lands:
     * mail composers render proportional text, so the padding became ragged whitespace and the
     * longer rows wrapped mid-column. Plain `Label: value` survives any font and any wrap.
     *
     * Rows with an empty label are continuation lines (the rotation matrix's second and third
     * rows) and are indented under the row that named them.
     *
     * The report opens with blank lines so that the mail composer's cursor lands above [header]
     * and the user types their description there — without them, people reply underneath the
     * data and we lose the one part we cannot reconstruct.
     */
    fun format(
        header: String,
        sections: List<DiagnosticsSection>,
    ): String =
        buildString {
            appendLine()
            appendLine()
            appendLine(header)
            for (section in sections) {
                appendLine()
                appendLine(section.title)
                appendLine("-".repeat(section.title.length))
                for (row in section.rows) {
                    if (row.label.isEmpty()) {
                        appendLine("  ${row.value}")
                    } else {
                        appendLine("${row.label}: ${row.value}")
                    }
                }
            }
        }
}
