/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GroundRampTest {
    @Test
    fun `there is no ground above the horizon`() {
        assertThat(GroundRamp.coverage(45.0)).isEqualTo(0.0)
        assertThat(GroundRamp.coverage(1.0)).isEqualTo(0.0)
        assertThat(GroundRamp.coverage(0.0)).isEqualTo(0.0)
    }

    @Test
    fun `the ground is fully covering just below the horizon`() {
        assertThat(GroundRamp.coverage(-GroundRamp.EDGE_RAMP_DEG)).isEqualTo(1.0)
        assertThat(GroundRamp.coverage(-45.0)).isEqualTo(1.0)
        assertThat(GroundRamp.coverage(-90.0)).isEqualTo(1.0)
    }

    @Test
    fun `the edge ramp is narrow enough to read as a cut at the horizon`() {
        // The whole point of the ramp is that it antialiases without moving the boundary away
        // from the horizon line. Half a degree is roughly two pixels at a typical field of view,
        // so anything wider than this is a fade, and a fade reads as a second edge.
        assertThat(GroundRamp.EDGE_RAMP_DEG).isLessThan(0.5)
    }

    @Test
    fun `the ground is densest at the horizon and thins with depth`() {
        val atHorizon = GroundRamp.depthProfile(0.0)
        val shallow = GroundRamp.depthProfile(-5.0)
        val deep = GroundRamp.depthProfile(-30.0)
        val nadir = GroundRamp.depthProfile(-90.0)

        assertThat(atHorizon).isWithin(1e-9).of(1.0)
        assertThat(shallow).isLessThan(atHorizon)
        assertThat(deep).isLessThan(shallow)
        assertThat(nadir).isLessThan(deep)
    }

    @Test
    fun `the ground never thins away entirely, because it is a hemisphere and not a band`() {
        // Looking straight down must show ground. This is what separates it from the glow it
        // replaces, which was a band along the horizon with nothing beneath it.
        assertThat(GroundRamp.depthProfile(-90.0))
            .isGreaterThan(GroundRamp.NADIR_FRACTION * 0.99)
        assertThat(GroundRamp.NADIR_FRACTION).isGreaterThan(0.0)
    }

    @Test
    fun `the depth profile is monotone all the way down`() {
        var previous = GroundRamp.depthProfile(0.0)
        for (altitude in 1..90) {
            val current = GroundRamp.depthProfile(-altitude.toDouble())
            assertThat(current).isLessThan(previous)
            previous = current
        }
    }

    @Test
    fun `the ground is at full strength in daylight and backs off at night`() {
        assertThat(GroundRamp.solarScale(45.0)).isWithin(1e-9).of(1.0)
        assertThat(GroundRamp.solarScale(0.0)).isWithin(1e-9).of(1.0)
        assertThat(GroundRamp.solarScale(GroundRamp.NIGHT_SUN_ALTITUDE_DEG))
            .isWithin(1e-9)
            .of(GroundRamp.NIGHT_FRACTION)
        assertThat(GroundRamp.solarScale(-90.0)).isWithin(1e-9).of(GroundRamp.NIGHT_FRACTION)
    }

    @Test
    fun `the day-to-night transition is monotone and has no steps`() {
        var previous = GroundRamp.solarScale(5.0)
        for (sunAltitude in 0 downTo -20) {
            val current = GroundRamp.solarScale(sunAltitude.toDouble())
            assertThat(current).isAtMost(previous)
            previous = current
        }
        assertThat(previous).isWithin(1e-9).of(GroundRamp.NIGHT_FRACTION)
    }

    @Test
    fun `alpha approaches but never reaches the requested opacity`() {
        // Ground.opacity is the value at the horizon in the limit, and nothing ever quite gets
        // there: coverage only reaches 1 at the bottom of the edge ramp, by which point
        // depthProfile has already decayed a little. The shortfall is well under a percent and
        // invisible, but it is a property of the ramp rather than a rounding error, so it is
        // asserted rather than tolerated.
        val peak = GroundRamp.alpha(-GroundRamp.EDGE_RAMP_DEG, sunAltitudeDeg = 30.0, 0.55)
        assertThat(peak).isLessThan(0.55)
        assertThat(peak).isGreaterThan(0.55 * 0.98)
    }

    @Test
    fun `alpha peaks at the horizon and decreases downward from there`() {
        val justBelow = GroundRamp.alpha(-0.3, sunAltitudeDeg = 30.0, 0.55)
        val shallow = GroundRamp.alpha(-8.0, sunAltitudeDeg = 30.0, 0.55)
        val deep = GroundRamp.alpha(-60.0, sunAltitudeDeg = 30.0, 0.55)
        assertThat(shallow).isLessThan(justBelow)
        assertThat(deep).isLessThan(shallow)
    }

    @Test
    fun `alpha is zero above the horizon whatever else is true`() {
        assertThat(GroundRamp.alpha(10.0, sunAltitudeDeg = 30.0, 1.0)).isEqualTo(0.0)
        assertThat(GroundRamp.alpha(10.0, sunAltitudeDeg = -30.0, 1.0)).isEqualTo(0.0)
    }

    @Test
    fun `zero opacity is a real off switch`() {
        for (altitude in listOf(-0.5, -10.0, -45.0, -90.0)) {
            assertThat(GroundRamp.alpha(altitude, sunAltitudeDeg = 30.0, 0.0)).isEqualTo(0.0)
        }
    }

    @Test
    fun `alpha never exceeds the requested opacity`() {
        // Guards against a future term being added as a boost rather than an attenuation, which
        // would let the ground become more opaque than the preference asked for.
        for (altitude in 0 downTo -90) {
            for (sunAltitude in listOf(60.0, 10.0, 0.0, -6.0, -18.0, -60.0)) {
                val alpha = GroundRamp.alpha(altitude.toDouble(), sunAltitude, 0.55)
                assertThat(alpha).isAtMost(0.55)
                assertThat(alpha).isAtLeast(0.0)
            }
        }
    }
}
