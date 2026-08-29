/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.astronomy.SolarSystemBody.JUPITER
import com.google.android.stardroid.astronomy.SolarSystemBody.MARS
import com.google.android.stardroid.astronomy.SolarSystemBody.MERCURY
import com.google.android.stardroid.astronomy.SolarSystemBody.MOON
import com.google.android.stardroid.astronomy.SolarSystemBody.NEPTUNE
import com.google.android.stardroid.astronomy.SolarSystemBody.PLUTO
import com.google.android.stardroid.astronomy.SolarSystemBody.SATURN
import com.google.android.stardroid.astronomy.SolarSystemBody.SUN
import com.google.android.stardroid.astronomy.SolarSystemBody.URANUS
import com.google.android.stardroid.astronomy.SolarSystemBody.VENUS
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase, illumination, and magnitude. Most expected numbers are v1's regression outputs (from
 * `UniverseSmokeTest.regressionTests`) — they pin *algorithm equivalence* across the Float→Double
 * port, not physical accuracy (v1's own comment: "don't trust these numbers"). Tolerances are
 * loose enough to absorb Float→Double drift but tight enough to catch a real regression.
 *
 * Exception: the Moon's phase/illumination are the *corrected* values (full moon = ~78% lit, not
 * v1's inverted ~22%). v1 returned the elongation as the phase angle; v2 returns the true phase
 * angle (0° = full), so these match v1's own disabled "real value" test (~79%). See D25.
 */
class EphemerisAppearanceTest {
    private val t = utc(2010, 12, 25, 12)
    private val magTol = 0.05
    private val phaseTol = 0.5
    private val fractionTol = 0.01

    private fun illum(body: SolarSystemBody) = KeplerianEphemeris.illuminatedFraction(body, t)

    private fun phase(body: SolarSystemBody) = KeplerianEphemeris.phaseAngleDeg(body, t)

    private fun mag(body: SolarSystemBody) = KeplerianEphemeris.magnitude(body, t)

    @Test
    fun illuminatedFraction() {
        // v1 reported percentages; v2 returns a fraction in [0, 1]. Moon corrected (v1: 0.217420).
        assertThat(illum(MOON)).isWithin(fractionTol).of(0.782580)
        assertThat(illum(MERCURY)).isWithin(fractionTol).of(0.121317)
        assertThat(illum(VENUS)).isWithin(fractionTol).of(0.420389)
        assertThat(illum(MARS)).isWithin(fractionTol).of(0.996485)
    }

    @Test
    fun phaseAngle() {
        // Corrected true phase angle = 180 - v1's elongation value (124.41341).
        assertThat(phase(MOON)).isWithin(phaseTol).of(55.58659)
        assertThat(phase(MERCURY)).isWithin(phaseTol).of(139.23260)
        assertThat(phase(VENUS)).isWithin(phaseTol).of(99.16174)
        assertThat(phase(MARS)).isWithin(phaseTol).of(6.797830)
    }

    @Test
    fun magnitude() {
        assertThat(mag(SUN)).isWithin(magTol).of(-27.0)
        assertThat(mag(MOON)).isWithin(magTol).of(-10.0)
        assertThat(mag(MERCURY)).isWithin(magTol).of(1.796470)
        assertThat(mag(VENUS)).isWithin(magTol).of(-4.544736)
        assertThat(mag(MARS)).isWithin(magTol).of(1.228771)
        assertThat(mag(JUPITER)).isWithin(magTol).of(-2.377939)
        assertThat(mag(SATURN)).isWithin(magTol).of(1.100657)
        assertThat(mag(URANUS)).isWithin(magTol).of(5.848584)
        assertThat(mag(NEPTUNE)).isWithin(magTol).of(7.944333)
        assertThat(mag(PLUTO)).isWithin(magTol).of(14.110676)
    }
}
