/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DiagnosticsReportTest {
    private val sections =
        listOf(
            DiagnosticsSection(
                "General",
                listOf(
                    DiagnosticsRow("Device", "Pixel 5 (redfin) en"),
                    DiagnosticsRow("Android version", "16 (36)"),
                ),
            ),
            DiagnosticsSection(
                "Graphics",
                listOf(DiagnosticsRow("Renderer", "Adreno (TM) 620")),
            ),
        )

    @Test
    fun `opens with blank lines so the composer cursor sits above the data`() {
        val report = DiagnosticsReport.format("--- header ---", sections)

        assertThat(report).startsWith("\n\n--- header ---")
    }

    @Test
    fun `renders every section title, label and value`() {
        val report = DiagnosticsReport.format("--- header ---", sections)

        assertThat(report).contains("General")
        assertThat(report).contains("Graphics")
        assertThat(report).contains("Device")
        assertThat(report).contains("Pixel 5 (redfin) en")
        assertThat(report).contains("Renderer")
        assertThat(report).contains("Adreno (TM) 620")
    }

    @Test
    fun `underlines each section title to its own length`() {
        val report = DiagnosticsReport.format("h", sections)

        assertThat(report).contains("General\n-------")
        assertThat(report).contains("Graphics\n--------")
    }

    @Test
    fun `writes one label colon value per line, not a padded column`() {
        val report = DiagnosticsReport.format("h", sections)
        val lines = report.lines()

        // Padding to a column looks right in a monospaced editor and ragged in the proportional
        // font of every mail composer this actually lands in.
        assertThat(lines).contains("Device: Pixel 5 (redfin) en")
        assertThat(lines).contains("Renderer: Adreno (TM) 620")
        assertThat(lines.none { it.contains("  ") && it.startsWith("Device") }).isTrue()
    }

    @Test
    fun `indents continuation rows that carry no label`() {
        val report =
            DiagnosticsReport.format(
                "h",
                listOf(
                    DiagnosticsSection(
                        "Sensors",
                        listOf(
                            DiagnosticsRow("Rot Matrix", "0.86,0.50,-0.09"),
                            DiagnosticsRow("", "-0.51,0.85,-0.12"),
                        ),
                    ),
                ),
            )

        assertThat(report.lines()).contains("Rot Matrix: 0.86,0.50,-0.09")
        assertThat(report.lines()).contains("  -0.51,0.85,-0.12")
    }

    @Test
    fun `handles an empty section list`() {
        assertThat(DiagnosticsReport.format("h", emptyList())).isEqualTo("\n\nh\n")
    }
}
