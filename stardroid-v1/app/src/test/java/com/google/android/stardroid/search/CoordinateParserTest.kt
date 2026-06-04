package com.google.android.stardroid.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoordinateParserTest {

    private val TOL = 0.0001f

    @Test
    fun testDecimalDegrees() {
        // Unsuffixed RA > 24 is treated as degrees
        var result = CoordinateParser.parseCoordinates("187.5 45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(45.2f)

        // Unsuffixed RA <= 24 is treated as hours (12.5h = 187.5 deg)
        result = CoordinateParser.parseCoordinates("12.5 45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(45.2f)

        // Explicit degree unit for RA <= 24 is treated as degrees
        result = CoordinateParser.parseCoordinates("12.5d 45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(12.5f)
        assertThat(result.dec).isWithin(TOL).of(45.2f)

        result = CoordinateParser.parseCoordinates("12.5° 45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(12.5f)
        assertThat(result.dec).isWithin(TOL).of(45.2f)

        // Explicit hour unit
        result = CoordinateParser.parseCoordinates("12.5h 45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(45.2f)
    }

    @Test
    fun testCommaSeparation() {
        var result = CoordinateParser.parseCoordinates("187.5, 45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(45.2f)

        result = CoordinateParser.parseCoordinates("12.5h, 45.2d")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(45.2f)
    }

    @Test
    fun testHmsDmsFormats() {
        // Standard HMS DMS with units
        var result = CoordinateParser.parseCoordinates("12h30m45s -45d12m30s")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.6875f)
        assertThat(result.dec).isWithin(TOL).of(-45.20833f)

        // Colon separated
        result = CoordinateParser.parseCoordinates("12:30:45 -45:12:30")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.6875f)
        assertThat(result.dec).isWithin(TOL).of(-45.20833f)

        // Spaces only
        result = CoordinateParser.parseCoordinates("12 30 -45 12")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(-45.2f)
    }

    @Test
    fun testSignedZeroDec() {
        var result = CoordinateParser.parseCoordinates("12h -0d 12m")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(180.0f)
        assertThat(result.dec).isWithin(TOL).of(-0.2f)

        result = CoordinateParser.parseCoordinates("12h +0d 12m")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(180.0f)
        assertThat(result.dec).isWithin(TOL).of(0.2f)
    }

    @Test
    fun testUnicodeSigns() {
        // U+2212 Minus
        var result = CoordinateParser.parseCoordinates("187.5 −45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(-45.2f)

        // U+2013 En Dash
        result = CoordinateParser.parseCoordinates("187.5 –45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(-45.2f)

        // U+2014 Em Dash
        result = CoordinateParser.parseCoordinates("187.5 —45.2")
        assertThat(result).isNotNull()
        assertThat(result!!.ra).isWithin(TOL).of(187.5f)
        assertThat(result.dec).isWithin(TOL).of(-45.2f)
    }

    @Test
    fun testInvalidBoundsAndForm() {
        // Out of bounds RA hours
        assertThat(CoordinateParser.parseCoordinates("25h 45d")).isNull()

        // Out of bounds RA degrees
        assertThat(CoordinateParser.parseCoordinates("370d 45d")).isNull()
        assertThat(CoordinateParser.parseCoordinates("-10d 45d")).isNull()

        // Out of bounds Dec
        assertThat(CoordinateParser.parseCoordinates("12h 95d")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12h -95d")).isNull()

        // Incomplete / invalid form
        assertThat(CoordinateParser.parseCoordinates("12h")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12h 30m")).isNull()
        assertThat(CoordinateParser.parseCoordinates("abc")).isNull()

        // Extra non-coordinate text must not produce false positives
        assertThat(CoordinateParser.parseCoordinates("Mars 12 45")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12 45 Mars")).isNull()
        assertThat(CoordinateParser.parseCoordinates("NGC 12 45")).isNull()
        assertThat(CoordinateParser.parseCoordinates("12h 30m extra -45d 12m")).isNull()
    }
}
