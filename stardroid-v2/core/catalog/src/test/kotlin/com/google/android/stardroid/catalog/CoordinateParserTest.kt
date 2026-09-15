/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * v1's `CoordinateParserTest` carried over as the golden baseline for the port (D32); tolerances
 * tightened for the `Float` → `Double` conversion.
 */
class CoordinateParserTest {
    private val tolerance = 1e-9

    private fun assertParsesTo(
        query: String,
        raDeg: Double,
        decDeg: Double,
    ) {
        val result = CoordinateParser.parseCoordinates(query)
        assertThat(result).isNotNull()
        assertThat(result!!.raDeg).isWithin(tolerance).of(raDeg)
        assertThat(result.decDeg).isWithin(tolerance).of(decDeg)
    }

    @Test
    fun `decimal degrees`() {
        // Unsuffixed RA > 24 is treated as degrees.
        assertParsesTo("187.5 45.2", 187.5, 45.2)
        // Unsuffixed RA <= 24 is treated as hours (12.5h = 187.5 deg).
        assertParsesTo("12.5 45.2", 187.5, 45.2)
        // Explicit degree unit for RA <= 24 is treated as degrees.
        assertParsesTo("12.5d 45.2", 12.5, 45.2)
        assertParsesTo("12.5° 45.2", 12.5, 45.2)
        // Explicit hour unit.
        assertParsesTo("12.5h 45.2", 187.5, 45.2)
    }

    @Test
    fun `comma separation`() {
        assertParsesTo("187.5, 45.2", 187.5, 45.2)
        assertParsesTo("12.5h, 45.2d", 187.5, 45.2)
    }

    @Test
    fun `hms dms formats`() {
        // Standard HMS DMS with units.
        assertParsesTo("12h30m45s -45d12m30s", 187.6875, -(45.0 + 12.0 / 60.0 + 30.0 / 3600.0))
        // Colon separated.
        assertParsesTo("12:30:45 -45:12:30", 187.6875, -(45.0 + 12.0 / 60.0 + 30.0 / 3600.0))
        // Spaces only.
        assertParsesTo("12 30 -45 12", 187.5, -45.2)
    }

    @Test
    fun `signed zero declination`() {
        assertParsesTo("12h -0d 12m", 180.0, -0.2)
        assertParsesTo("12h +0d 12m", 180.0, 0.2)
    }

    @Test
    fun `unicode signs`() {
        // U+2212 minus sign.
        assertParsesTo("187.5 −45.2", 187.5, -45.2)
        // U+2013 en dash.
        assertParsesTo("187.5 –45.2", 187.5, -45.2)
        // U+2014 em dash.
        assertParsesTo("187.5 —45.2", 187.5, -45.2)
    }

    @Test
    fun `smart quotes as minute and second marks`() {
        val dec = -(45.0 + 12.0 / 60.0 + 30.0 / 3600.0)
        // Straight-quote baseline.
        assertParsesTo("12h30'45\" -45d12'30\"", 187.6875, dec)
        // U+2019 right single quote / U+201D right double quote (mobile smart punctuation).
        assertParsesTo("12h30’45” -45d12’30”", 187.6875, dec)
        // U+2018 left single quote / U+201C left double quote (copy-pasted text).
        assertParsesTo("12h30‘45“ -45d12‘30“", 187.6875, dec)
        // Smart-quote minute as the pass-2 end-of-RA split marker.
        assertParsesTo("12h 30’ 45.5 12.5", 187.5, 45.5 + 12.5 / 60.0)
        // U+2032 prime / U+2033 double prime split pass 2 like their straight-quote forms.
        assertParsesTo("12h 30′ 45″ 45", 187.6875, 45.0)
    }

    @Test
    fun `non-breaking spaces as separators`() {
        // U+00A0 no-break space and U+202F narrow no-break space (copy-pasted coordinates).
        assertParsesTo("187.5\u00A045.2", 187.5, 45.2)
        assertParsesTo("12h\u202F30m\u00A0-45d\u202F12m", 187.5, -45.2)
    }

    @Test
    fun `invalid bounds and forms are rejected`() {
        // Out of bounds RA hours.
        assertThat(CoordinateParser.parseCoordinates("25h 45d")).isNull()
        // Out of bounds RA degrees.
        assertThat(CoordinateParser.parseCoordinates("370d 45d")).isNull()
        assertThat(CoordinateParser.parseCoordinates("-10d 45d")).isNull()
        // Out of bounds Dec.
        assertThat(CoordinateParser.parseCoordinates("12h 95d")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12h -95d")).isNull()
        // Incomplete / invalid form.
        assertThat(CoordinateParser.parseCoordinates("12h")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12h 30m")).isNull()
        assertThat(CoordinateParser.parseCoordinates("abc")).isNull()
        // Extra non-coordinate text must not produce false positives.
        assertThat(CoordinateParser.parseCoordinates("Mars 12 45")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12 45 Mars")).isNull()
        assertThat(CoordinateParser.parseCoordinates("NGC 12 45")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12h 30m extra -45d 12m")).isNull()
    }

    @Test
    fun `null and blank input`() {
        assertThat(CoordinateParser.parseCoordinates(null)).isNull()
        assertThat(CoordinateParser.parseCoordinates("")).isNull()
        assertThat(CoordinateParser.parseCoordinates("   ")).isNull()
    }
}
