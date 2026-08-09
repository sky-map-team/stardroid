/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.space

import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*

// True angular radii are only good to a percent or so: distances vary through the year/orbit
// (e.g. Earth's own orbital eccentricity), and this checks against the well known mean values.
private const val ANGULAR_RADIUS_TOL_DEG = 0.05f

class SolarSystemObjectTest {
    @Test
    fun testSunTrueAngularRadius() {
        // Mean apparent angular diameter of the Sun as seen from Earth is about 0.533 degrees,
        // i.e. an angular radius of about 0.267 degrees - a small fraction of the fixed,
        // exaggerated-for-visibility 0.02 radians (about 1.15 degrees) getPlanetaryImageSize()
        // returns.
        val sun = Sun()
        val angularRadiusDeg = sun.getTrueAngularRadius(Date()) * RADIANS_TO_DEGREES
        assertThat(angularRadiusDeg).isWithin(ANGULAR_RADIUS_TOL_DEG).of(0.267f)
    }

    @Test
    fun testJupiterTrueAngularRadiusIsFarSmallerThanTheFixedImageSize() {
        val jupiter = SunOrbitingObject(com.google.android.stardroid.ephemeris.SolarSystemBody.Jupiter)
        val angularRadiusDeg = jupiter.getTrueAngularRadius(Date()) * RADIANS_TO_DEGREES
        val fixedSizeDeg = jupiter.getPlanetaryImageSize() * RADIANS_TO_DEGREES
        // Jupiter's true angular radius is at most ~0.012 degrees (opposition); its fixed,
        // exaggerated-for-visibility image size is ~1.43 degrees - roughly two orders of
        // magnitude larger.
        assertThat(angularRadiusDeg).isLessThan(0.02f)
        assertThat(angularRadiusDeg).isLessThan(fixedSizeDeg)
    }
}
