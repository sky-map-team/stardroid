/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BrightLimbTest {
    @Test
    fun `matches Meeus worked example 48a`() {
        // Meeus, Astronomical Algorithms, example 48.a: the Moon on 1992 April 12.0 TD.
        val moon = RaDec(134.6885, 13.7684)
        val sun = RaDec(20.6579, 8.6964)
        assertThat(brightLimbAngleDeg(moon, sun)).isWithin(0.01).of(285.04)
    }

    @Test
    fun `lit limb points at the Sun along the equator`() {
        // A body on the celestial equator with the Sun due east of it is lit from the east:
        // position angle 90.
        val body = RaDec(100.0, 0.0)
        val sun = RaDec(130.0, 0.0)
        assertThat(brightLimbAngleDeg(body, sun)).isWithin(1e-6).of(90.0)
    }

    @Test
    fun `lit limb points west when the Sun is west`() {
        val body = RaDec(100.0, 0.0)
        val sun = RaDec(70.0, 0.0)
        assertThat(brightLimbAngleDeg(body, sun)).isWithin(1e-6).of(270.0)
    }

    @Test
    fun `lit limb points north when the Sun is north on the same meridian`() {
        val body = RaDec(100.0, 0.0)
        val sun = RaDec(100.0, 20.0)
        assertThat(brightLimbAngleDeg(body, sun)).isWithin(1e-6).of(0.0)
    }

    @Test
    fun `lit limb points south when the Sun is south on the same meridian`() {
        val body = RaDec(100.0, 10.0)
        val sun = RaDec(100.0, -20.0)
        assertThat(brightLimbAngleDeg(body, sun)).isWithin(1e-6).of(180.0)
    }

    @Test
    fun `result is always in zero to 360`() {
        for (ra in 0..350 step 10) {
            for (dec in -80..80 step 20) {
                val chi =
                    brightLimbAngleDeg(RaDec(120.0, 5.0), RaDec(ra.toDouble(), dec.toDouble()))
                assertThat(chi).isAtLeast(0.0)
                assertThat(chi).isLessThan(360.0)
            }
        }
    }
}
