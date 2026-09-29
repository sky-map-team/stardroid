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
    private val opacity = 0.55

    @Test
    fun `there is no ground above the horizon`() {
        assertThat(GroundRamp.coverage(45.0)).isEqualTo(0.0)
        assertThat(GroundRamp.coverage(1.0)).isEqualTo(0.0)
    }

    @Test
    fun `the ground fully covers below the horizon`() {
        assertThat(GroundRamp.coverage(-GroundRamp.EDGE_RAMP_DEG)).isEqualTo(1.0)
        assertThat(GroundRamp.coverage(-45.0)).isEqualTo(1.0)
        assertThat(GroundRamp.coverage(-90.0)).isEqualTo(1.0)
    }

    @Test
    fun `the ramp straddles the horizon rather than hanging below it`() {
        // The horizon line is drawn at exactly zero, so the blend has to be centred there for the
        // line to cover it. Hanging the ramp below zero is what put a visible band under the line
        // at high zoom -- the sky dissolving into ground over sixty pixels, starting at the line
        // and finishing well beneath it.
        assertThat(GroundRamp.coverage(0.0)).isWithin(1e-9).of(0.5)
        assertThat(GroundRamp.coverage(GroundRamp.EDGE_RAMP_DEG)).isEqualTo(0.0)
        assertThat(GroundRamp.coverage(-GroundRamp.EDGE_RAMP_DEG)).isEqualTo(1.0)
    }

    @Test
    fun `a narrower ramp sharpens the edge without moving it`() {
        // What the shader does per pixel: the half-width shrinks with the field of view so the
        // edge stays about a pixel wide, and the midpoint must not drift while it does.
        for (ramp in listOf(0.25, 0.05, 0.002)) {
            assertThat(GroundRamp.coverage(0.0, ramp)).isWithin(1e-9).of(0.5)
            assertThat(GroundRamp.coverage(-ramp, ramp)).isEqualTo(1.0)
            assertThat(GroundRamp.coverage(ramp, ramp)).isEqualTo(0.0)
        }
        // Tighter ramp, sharper edge: at a fixed small angle below the horizon, a narrow ramp is
        // already fully covering where a wide one is still blending.
        assertThat(GroundRamp.coverage(-0.02, 0.01)).isEqualTo(1.0)
        assertThat(GroundRamp.coverage(-0.02, 0.25)).isLessThan(1.0)
    }

    @Test
    fun `the edge ramp cap is narrow enough to read as a cut at the horizon`() {
        // The ramp antialiases without moving the boundary away from the horizon line. Anything
        // wider is a fade, and a fade reads as a second edge adrift of the horizon.
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
    fun `the depth profile is monotone all the way down`() {
        var previous = GroundRamp.depthProfile(0.0)
        for (altitude in 1..90) {
            val current = GroundRamp.depthProfile(-altitude.toDouble())
            assertThat(current).isLessThan(previous)
            previous = current
        }
    }

    @Test
    fun `the ground never thins away entirely, because it is a wash and not a band`() {
        // Looking straight down must still show ground. This is what separates it from the glow
        // it replaces, which was a band along the horizon with nothing beneath it.
        assertThat(GroundRamp.depthProfile(-90.0))
            .isGreaterThan(GroundRamp.NADIR_FRACTION * 0.99)
        assertThat(GroundRamp.NADIR_FRACTION).isGreaterThan(0.0)
    }

    @Test
    fun `the wash stays mostly uniform while still suggesting depth`() {
        // The brief pulls both ways -- "a mostly uniform wash" and "denser near the horizon to
        // give an illusion of depth" -- so this pins the compromise from both sides. A flat panel
        // (contrast 1) is what the first version looked like; a band with a hole under it is the
        // other failure.
        val contrast = GroundRamp.depthProfile(0.0) / GroundRamp.depthProfile(-90.0)
        assertThat(contrast).isGreaterThan(1.8)
        assertThat(contrast).isLessThan(4.0)
    }

    @Test
    fun `opacity is exactly the two view-dependent terms, with nothing else folded in`() {
        // The load-bearing property of the whole design: the Sun changes the ground's colour and
        // nothing else. If a solar term is ever multiplied into alpha again, the ground stops
        // being the same substance at every azimuth, and it took two attempts to learn that.
        //
        // Asserted as an identity rather than by inspecting the function signature. The signature
        // version broke the moment `rampDeg` was added -- a correct, unrelated change -- which is
        // the standing hazard with structural assertions.
        for (altitude in listOf(-0.1, -1.0, -8.0, -35.0, -90.0)) {
            val expected =
                opacity * GroundRamp.coverage(altitude) * GroundRamp.depthProfile(altitude)
            assertThat(GroundRamp.alpha(altitude, opacity)).isWithin(1e-12).of(expected)
        }
    }

    @Test
    fun `the sun drives the colour mix, saturating at day and at astronomical twilight`() {
        assertThat(GroundRamp.daylight(45.0)).isWithin(1e-9).of(1.0)
        assertThat(GroundRamp.daylight(GroundRamp.DAY_SUN_ALTITUDE_DEG)).isWithin(1e-9).of(1.0)
        assertThat(GroundRamp.daylight(GroundRamp.NIGHT_SUN_ALTITUDE_DEG)).isWithin(1e-9).of(0.0)
        assertThat(GroundRamp.daylight(-90.0)).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `the day-to-night colour transition is monotone and has no steps`() {
        var previous = GroundRamp.daylight(5.0)
        for (sunAltitude in 0 downTo -25) {
            val current = GroundRamp.daylight(sunAltitude.toDouble())
            assertThat(current).isAtMost(previous)
            previous = current
        }
        assertThat(previous).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `the ground is still lit through civil and nautical twilight`() {
        // Anchored to astronomical twilight so the ground tracks the sky's own darkening. At -12
        // the sky is still visibly lit, and a ground that had already gone fully dark would be a
        // hole again, at dusk instead of at noon.
        assertThat(GroundRamp.daylight(-6.0)).isGreaterThan(0.5)
        assertThat(GroundRamp.daylight(-12.0)).isGreaterThan(0.1)
    }

    @Test
    fun `alpha approaches but never reaches the requested opacity`() {
        // Ground.opacity is the value at the horizon in the limit, and nothing quite gets there:
        // coverage only reaches 1 at the bottom of the edge ramp, by which point depthProfile has
        // decayed a little. The shortfall is well under a percent and invisible, but it is a
        // property of the ramp rather than a rounding error.
        val peak = GroundRamp.alpha(-GroundRamp.EDGE_RAMP_DEG, opacity)
        assertThat(peak).isLessThan(opacity)
        assertThat(peak).isGreaterThan(opacity * 0.98)
    }

    @Test
    fun `alpha is zero above the ramp`() {
        assertThat(GroundRamp.alpha(10.0, 1.0)).isEqualTo(0.0)
        assertThat(GroundRamp.alpha(GroundRamp.EDGE_RAMP_DEG, 1.0)).isEqualTo(0.0)
    }

    @Test
    fun `zero opacity is a real off switch`() {
        for (altitude in listOf(-0.5, -10.0, -45.0, -90.0)) {
            assertThat(GroundRamp.alpha(altitude, 0.0)).isEqualTo(0.0)
        }
    }

    @Test
    fun `alpha never exceeds the requested opacity`() {
        // Guards against a future term being added as a boost rather than an attenuation, which
        // would let the ground become more opaque than the preference asked for.
        for (altitude in 0 downTo -90) {
            val alpha = GroundRamp.alpha(altitude.toDouble(), opacity)
            assertThat(alpha).isAtMost(opacity)
            assertThat(alpha).isAtLeast(0.0)
        }
    }
}
