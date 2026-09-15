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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** [Ephemeris.earthDistanceAu] exists for D18's image ordering: only the ordering must hold. */
class EarthDistanceTest {
    private val t = utc(2024, 1, 25, 17, 54)

    private fun distance(body: SolarSystemBody) = KeplerianEphemeris.earthDistanceAu(body, t)

    @Test
    fun `sun sits near one astronomical unit`() {
        assertThat(distance(SolarSystemBody.SUN)).isWithin(0.02).of(0.984)
    }

    @Test
    fun `moon is nearer than everything else`() {
        val others = SolarSystemBody.entries - SolarSystemBody.MOON - SolarSystemBody.EARTH
        for (body in others) {
            assertThat(distance(SolarSystemBody.MOON)).isLessThan(distance(body))
        }
    }

    @Test
    fun `outer planets are farther than the sun`() {
        assertThat(distance(SolarSystemBody.JUPITER)).isGreaterThan(distance(SolarSystemBody.SUN))
        assertThat(distance(SolarSystemBody.NEPTUNE)).isGreaterThan(
            distance(SolarSystemBody.JUPITER),
        )
    }

    @Test
    fun `earth has no distance from itself`() {
        assertThrows(IllegalArgumentException::class.java) {
            distance(SolarSystemBody.EARTH)
        }
    }
}
