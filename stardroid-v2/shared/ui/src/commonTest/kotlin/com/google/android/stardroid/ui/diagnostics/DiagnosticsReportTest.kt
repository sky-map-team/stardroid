/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        assertTrue(report.startsWith("\n\n--- header ---"))
    }

    @Test
    fun `renders every section title - label and value`() {
        val report = DiagnosticsReport.format("--- header ---", sections)

        assertTrue("General" in report)
        assertTrue("Graphics" in report)
        assertTrue("Device" in report)
        assertTrue("Pixel 5 (redfin) en" in report)
        assertTrue("Renderer" in report)
        assertTrue("Adreno (TM) 620" in report)
    }

    @Test
    fun `underlines each section title to its own length`() {
        val report = DiagnosticsReport.format("h", sections)

        assertTrue("General\n-------" in report)
        assertTrue("Graphics\n--------" in report)
    }

    @Test
    fun `writes one label colon value per line - not a padded column`() {
        val report = DiagnosticsReport.format("h", sections)
        val lines = report.lines()

        // Padding to a column looks right in a monospaced editor and ragged in the proportional
        // font of every mail composer this actually lands in.
        assertTrue("Device: Pixel 5 (redfin) en" in lines)
        assertTrue("Renderer: Adreno (TM) 620" in lines)
        assertFalse(lines.any { it.contains("  ") && it.startsWith("Device") })
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

        assertTrue("Rot Matrix: 0.86,0.50,-0.09" in report.lines())
        assertTrue("  -0.51,0.85,-0.12" in report.lines())
    }

    @Test
    fun `handles an empty section list`() {
        assertEquals("\n\nh\n", DiagnosticsReport.format("h", emptyList()))
    }
}
