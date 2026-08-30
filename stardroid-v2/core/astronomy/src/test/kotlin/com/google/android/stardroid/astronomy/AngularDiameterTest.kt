/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

/**
 * D86 draws bodies at their true apparent size, so these check the sizes against the ranges each
 * body is known to span as seen from Earth — the numbers in any observing handbook.
 *
 * Sampling a few years catches the extremes: every one of these bodies runs through its full
 * range of distances well within that.
 */
class AngularDiameterTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")

    /** Apparent diameter in arcseconds at [dayOffset] days after [start]. */
    private fun arcsec(
        body: SolarSystemBody,
        dayOffset: Int,
    ) = MeeusEphemeris.angularDiameterDeg(body, start + dayOffset.days) * 3600.0

    private fun range(
        body: SolarSystemBody,
        days: Int = 800,
        step: Int = 2,
    ): ClosedRange<Double> {
        val samples = (0..days step step).map { arcsec(body, it) }
        return samples.min()..samples.max()
    }

    @Test
    fun `the Sun spans its known range`() {
        // 31.5' at aphelion to 32.5' at perihelion.
        val sun = range(SolarSystemBody.SUN)
        assertThat(sun.start).isWithin(6.0).of(1889.0)
        assertThat(sun.endInclusive).isWithin(6.0).of(1952.0)
    }

    @Test
    fun `the Moon spans its known range, which is what makes a supermoon`() {
        // 29.4' at apogee to 33.5' at perigee — about a 12% swing, and the reason D86 bothers.
        val moon = range(SolarSystemBody.MOON, days = 400, step = 1)
        assertThat(moon.start).isWithin(40.0).of(1765.0)
        assertThat(moon.endInclusive).isWithin(40.0).of(2010.0)
        assertThat(moon.endInclusive / moon.start).isGreaterThan(1.10)
    }

    @Test
    fun `Venus swings from a distant dot to a near crescent`() {
        // ~9.7" at superior conjunction to ~66" at inferior — the largest swing of any planet,
        // and the payoff for phases (D88 section 5).
        val venus = range(SolarSystemBody.VENUS)
        assertThat(venus.start).isWithin(1.5).of(9.7)
        assertThat(venus.endInclusive).isWithin(6.0).of(63.0)
    }

    @Test
    fun `the outer planets stay within a few arcseconds of their handbook sizes`() {
        val jupiter = range(SolarSystemBody.JUPITER)
        assertThat(jupiter.start).isWithin(2.0).of(30.5)
        assertThat(jupiter.endInclusive).isWithin(2.5).of(48.0)

        // Saturn's globe, not its rings: the rings span 2.269 equatorial radii.
        val saturn = range(SolarSystemBody.SATURN)
        assertThat(saturn.start).isWithin(1.5).of(15.0)
        assertThat(saturn.endInclusive).isWithin(1.5).of(19.5)

        val mars = range(SolarSystemBody.MARS)
        assertThat(mars.start).isWithin(1.0).of(3.5)
        assertThat(mars.endInclusive).isGreaterThan(13.0)

        val uranus = range(SolarSystemBody.URANUS)
        assertThat(uranus.start).isWithin(0.6).of(3.4)
        assertThat(uranus.endInclusive).isWithin(0.6).of(3.8)

        val neptune = range(SolarSystemBody.NEPTUNE)
        assertThat(neptune.start).isWithin(0.4).of(2.2)
        assertThat(neptune.endInclusive).isWithin(0.4).of(2.4)
    }

    @Test
    fun `Mercury and Pluto sit at the extremes`() {
        val mercury = range(SolarSystemBody.MERCURY)
        assertThat(mercury.start).isWithin(1.0).of(4.5)
        assertThat(mercury.endInclusive).isWithin(2.0).of(13.0)

        // Pluto is the smallest thing the app draws as a disc: about a tenth of an arcsecond.
        val pluto = range(SolarSystemBody.PLUTO)
        assertThat(pluto.endInclusive).isLessThan(0.15)
        assertThat(pluto.start).isGreaterThan(0.05)
    }

    @Test
    fun `the Sun and Moon are close enough in size to eclipse each other`() {
        // The coincidence the whole of D86 exists to render: at some point in any year the two
        // discs are within a few percent, which is what makes totality and annularity possible.
        val ratios =
            (0..400).map {
                arcsec(SolarSystemBody.MOON, it) / arcsec(SolarSystemBody.SUN, it)
            }
        assertThat(ratios.max()).isGreaterThan(1.02)
        assertThat(ratios.min()).isLessThan(0.98)
    }

    @Test
    fun `diameter tracks distance exactly`() {
        // The relation the floor arithmetic in D86 assumes: half the distance, twice the size.
        val time = start + 100.days
        val distance = MeeusEphemeris.earthDistanceAu(SolarSystemBody.MARS, time)
        val diameter = MeeusEphemeris.angularDiameterDeg(SolarSystemBody.MARS, time)
        // Small-angle: diameter * distance is constant, to well within a part in a million.
        val product = diameter * distance
        val other = start + 300.days
        val otherProduct =
            MeeusEphemeris.angularDiameterDeg(SolarSystemBody.MARS, other) *
                MeeusEphemeris.earthDistanceAu(SolarSystemBody.MARS, other)
        assertThat(otherProduct).isWithin(product * 1e-6).of(product)
    }
}
