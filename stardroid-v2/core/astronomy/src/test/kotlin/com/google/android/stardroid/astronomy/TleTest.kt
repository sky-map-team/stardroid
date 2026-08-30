/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The TLE parser: fixed columns, implied decimal points, a two-digit year, and a checksum — a
 * format from the punched-card era that is still exactly what CelesTrak serves today.
 *
 * The fields it gets wrong silently are the ones tested hardest here: an assumed-decimal B\* read
 * as a plain number is off by orders of magnitude, and a mis-pivoted year puts the epoch a century
 * away, both of which produce a propagator that runs happily and answers wrongly.
 */
class TleTest {
    private val issLine1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993"
    private val issLine2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882"

    @Test
    fun `parses every field of a current ISS element set`() {
        val tle = Tle.parse(issLine1, issLine2, name = "ISS (ZARYA)")

        assertThat(tle.name).isEqualTo("ISS (ZARYA)")
        assertThat(tle.noradId).isEqualTo(25544)
        assertThat(tle.inclinationRad * RADIANS_TO_DEGREES).isWithin(1e-9).of(51.6331)
        assertThat(tle.raanRad * RADIANS_TO_DEGREES).isWithin(1e-9).of(8.6030)
        assertThat(tle.eccentricity).isWithin(1e-12).of(0.0007568)
        assertThat(tle.argOfPerigeeRad * RADIANS_TO_DEGREES).isWithin(1e-9).of(47.4901)
        assertThat(tle.meanAnomalyRad * RADIANS_TO_DEGREES).isWithin(1e-9).of(312.6726)
        // 15.49446860 revolutions per day, converted to the radians per minute SGP4 works in.
        assertThat(tle.meanMotionRadPerMin)
            .isWithin(1e-12)
            .of(15.49446860 * 2.0 * Math.PI / 1440.0)
        assertThat(tle.bstar).isWithin(1e-12).of(0.97076e-4)
        assertThat(tle.meanMotionDotRevPerDay2).isWithin(1e-12).of(0.00004985)
        assertThat(tle.meanMotionDdotRevPerDay3).isWithin(1e-12).of(0.0)
    }

    @Test
    fun `the epoch resolves to the instant the day fraction names`() {
        // Day 227.08368470 of 2026. Day 1 is January 1st, so this is 226 whole days after
        // midnight on the 1st — 2026-08-15 — plus 0.08368470 of a day, i.e. 7230.35808 seconds.
        val tle = Tle.parse(issLine1, issLine2)
        val expected = Instant.parse("2026-08-15T02:00:30.358080Z")
        assertThat((tle.epoch - expected).inWholeMicroseconds).isAtMost(1L)
    }

    @Test
    fun `the epoch keeps sub-millisecond precision`() {
        // At 7.7 km/s a rounded millisecond is already ~8 m of position, which would swamp the
        // tolerances the Vallado vectors are held to. Nanoseconds, not milliseconds.
        val tle = Tle.parse(issLine1, issLine2)
        assertThat(tle.epoch.nanosecondsOfSecond % 1_000_000).isNotEqualTo(0)
    }

    @Test
    fun `the two-digit year pivots at 57, the year Sputnik launched`() {
        assertThat(Tle.epochYear(57)).isEqualTo(1957)
        assertThat(Tle.epochYear(99)).isEqualTo(1999)
        assertThat(Tle.epochYear(0)).isEqualTo(2000)
        assertThat(Tle.epochYear(56)).isEqualTo(2056)
    }

    @Test
    fun `a 1950s epoch resolves to the twentieth century`() {
        // Vanguard 1, launched 1958 and still up there. Its element sets are the reason the pivot
        // exists at all, and the case a naive `2000 + yy` gets a century wrong.
        val vanguard =
            Tle.parse(
                "1 00005U 58002B   00179.78495062  .00000023  00000-0  28098-4 0  4753",
                "2 00005  34.2682 348.7242 1859667 331.7664  19.3264 10.82419157413667",
            )
        // Epoch year 00 pivots forward, to 2000.
        assertThat(vanguard.epoch).isGreaterThan(Instant.parse("2000-06-01T00:00:00Z"))
        assertThat(vanguard.epoch).isLessThan(Instant.parse("2000-07-01T00:00:00Z"))
    }

    @Test
    fun `assumed-decimal fields carry their implied leading point and signed exponent`() {
        // `97076-4` is 0.97076e-4, not 97076 and not 0.97076. Getting this wrong makes the drag
        // term absurd, which shows up as a satellite that decays in hours.
        assertThat(Tle.parse(issLine1, issLine2).bstar).isWithin(1e-14).of(0.000097076)

        // A negative mantissa, from a satellite whose fitted drag term came out below zero — which
        // is common and physically fine, B* being a fit parameter rather than a real coefficient.
        val negativeDrag =
            Tle.parse(
                "1 25544U 98067A   26227.08368470  .00004985  00000+0 -97076-4 0  9994",
                issLine2,
            )
        assertThat(negativeDrag.bstar).isWithin(1e-14).of(-0.000097076)

        // The idle form, which is what most well-behaved satellites carry.
        val noDrag =
            Tle.parse(
                "1 25544U 98067A   26227.08368470  .00004985  00000+0  00000+0 0  9999",
                issLine2,
            )
        assertThat(noDrag.bstar).isEqualTo(0.0)
    }

    @Test
    fun `a second derivative in exponent form parses`() {
        // Catalog 22312 is the only fixture case with a non-zero value here: `81888-5`.
        val decaying =
            Tle.parse(
                "1 22312U 93002D   06094.46235912  .99999999  81888-5  49949-3 0  3953",
                "2 22312  62.1486  77.4698 0308723 267.9229  88.7392 15.95744531 98783",
            )
        assertThat(decaying.meanMotionDdotRevPerDay3).isWithin(1e-12).of(0.81888e-5)
        assertThat(decaying.meanMotionDotRevPerDay2).isWithin(1e-12).of(0.99999999)
    }

    @Test
    fun `a corrupted line fails its checksum rather than propagating quietly`() {
        // One digit changed. The whole point of the checksum is that a truncated or garbled
        // download is caught here, not turned into a satellite drawn in the wrong place.
        val corrupted = issLine1.replaceRange(20, 21, "9")
        assertThrows<IllegalArgumentException> { Tle.parse(corrupted, issLine2) }
    }

    @Test
    fun `the checksum counts minus signs as one and ignores other characters`() {
        assertThat(Tle.checksum(issLine1)).isEqualTo(3)
        assertThat(Tle.checksum(issLine2)).isEqualTo(2)
    }

    @Test
    fun `lines that are truncated, misnumbered or mismatched are rejected`() {
        assertThrows<IllegalArgumentException> { Tle.parse(issLine1.take(40), issLine2) }
        // Line 2 handed over in line 1's place.
        assertThrows<IllegalArgumentException> { Tle.parse(issLine2, issLine2) }
        // Two lines describing different satellites, which is what a mis-aligned catalog looks
        // like — and which would otherwise silently blend two objects' elements into one.
        assertThrows<IllegalArgumentException> {
            Tle.parse(
                issLine1,
                "2 06251  58.0579  54.0425 0030035 139.1568 221.1854 15.56387291  6774",
            )
        }
    }

    @Test
    fun `trailing content past column 69 is ignored`() {
        // SGP4-VER.TLE appends test parameters to line 2, and real feeds pad with whitespace.
        // Only the fixed 69-column prefix is significant.
        val padded = Tle.parse(issLine1, "$issLine2   0.0   1440.0   120.00")
        assertThat(padded.noradId).isEqualTo(25544)
    }

    @Test
    fun `a three-line catalog parses into named satellites`() {
        val catalog =
            """
            ISS (ZARYA)
            $issLine1
            $issLine2
            VANGUARD 1
            1 00005U 58002B   00179.78495062  .00000023  00000-0  28098-4 0  4753
            2 00005  34.2682 348.7242 1859667 331.7664  19.3264 10.82419157413667
            """.trimIndent()
        val parsed = Tle.parseCatalog(catalog)
        assertThat(parsed.map { it.name }).containsExactly("ISS (ZARYA)", "VANGUARD 1").inOrder()
        assertThat(parsed.map { it.noradId }).containsExactly(25544, 5).inOrder()
    }

    @Test
    fun `a bare two-line catalog parses with empty names`() {
        val parsed = Tle.parseCatalog("$issLine1\n$issLine2\n")
        assertThat(parsed).hasSize(1)
        assertThat(parsed.single().name).isEmpty()
    }

    @Test
    fun `the catalog parser drops bad entries instead of failing the whole download`() {
        // A feed is third-party text fetched over the network. One garbled satellite should cost
        // that satellite, not the other ninety-nine.
        val catalog =
            """
            BROKEN
            1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9991
            $issLine2
            ISS (ZARYA)
            $issLine1
            $issLine2
            """.trimIndent()
        val parsed = Tle.parseCatalog(catalog)
        assertThat(parsed.map { it.name }).containsExactly("ISS (ZARYA)")
    }

    @Test
    fun `the catalog parser filters deep-space element sets`() {
        // The repository must never hand SGP4 something it will answer wrongly, so the filter sits
        // at parse time rather than relying on every call site to remember.
        val catalog =
            """
            MOLNIYA-LIKE
            1 09880U 77021A   06176.56157475  .00000421  00000-0  10000-3 0  9814
            2 09880  64.5968 349.3786 7069051 270.0229  16.3320  2.00813614112380
            ISS (ZARYA)
            $issLine1
            $issLine2
            """.trimIndent()
        assertThat(Tle.parseCatalog(catalog).map { it.noradId }).containsExactly(25544)
    }

    @Test
    fun `element age is measured from the epoch`() {
        val tle = Tle.parse(issLine1, issLine2)
        assertThat(tle.ageDays(tle.epoch)).isWithin(1e-12).of(0.0)
        assertThat(tle.ageDays(tle.epoch + kotlin.time.Duration.parse("3d")))
            .isWithin(1e-9)
            .of(3.0)
        // Negative before the epoch, which a pass search starting yesterday will see.
        assertThat(tle.ageDays(tle.epoch - kotlin.time.Duration.parse("12h")))
            .isWithin(1e-9)
            .of(-0.5)
    }
}
