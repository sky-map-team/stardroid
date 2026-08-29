/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.events

import com.google.android.stardroid.astronomy.SatellitePass
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * Satellite passes as [SkyEvent]s.
 *
 * The point of joining this hierarchy is that passes reach the tonight widget and the D77
 * notification digest with no new UI — those surfaces render `SkyEvent`s and nothing else. So
 * what matters here is that a pass ranks *sensibly against the other things competing for those
 * three slots*, not that its quality has any particular absolute value.
 */
class SatellitePassEventTest {
    private fun pass(peakMagnitude: Double) =
        SatellitePass(
            satelliteName = "ISS (ZARYA)",
            noradId = 25544,
            start = Instant.parse("2026-08-15T21:47:00Z"),
            culmination = Instant.parse("2026-08-15T21:50:00Z"),
            end = Instant.parse("2026-08-15T21:53:00Z"),
            maxAltitudeDeg = 68.0,
            startAzimuthDeg = 315.0,
            endAzimuthDeg = 110.0,
            peakMagnitude = peakMagnitude,
            shadowEntry = null,
        )

    private fun qualityOf(peakMagnitude: Double): Double =
        tonightSky(
            now = Instant.parse("2026-08-15T18:00:00Z"),
            location = null,
            showers = emptyList(),
            passes = listOf(pass(peakMagnitude)),
        ).highlights.filterIsInstance<SkyEvent.SatellitePassTonight>().single().quality

    @Test
    fun `a brilliant pass outranks every planet`() {
        // Venus, the best-scoring planet, sits at 0.68. A -3.5 ISS pass is brighter than anything
        // in the sky but the Moon, and worth going outside for.
        assertThat(qualityOf(-3.5)).isGreaterThan(0.68)
    }

    @Test
    fun `an ordinary pass ranks below the planets`() {
        // Raw brightness is the wrong scale to borrow: a pass is a ~6-minute window while a
        // well-placed planet is available all evening. A merely-visible pass should not push
        // Jupiter out of a three-slot list.
        assertThat(qualityOf(0.5)).isLessThan(0.58)
        assertThat(qualityOf(-1.0)).isLessThan(0.62)
    }

    @Test
    fun `quality rises monotonically with brightness`() {
        val qualities = listOf(2.0, -1.0, -2.5, -3.5).map(::qualityOf)
        assertThat(qualities).isInOrder()
    }

    @Test
    fun `passes compete for the same three highlight slots`() {
        // Not appended alongside — ranked among. Three bright passes in one night should not crowd
        // out everything else by sheer number, and the existing cap is what prevents that.
        val sky =
            tonightSky(
                now = Instant.parse("2026-08-15T18:00:00Z"),
                location = null,
                showers = emptyList(),
                passes = listOf(pass(-3.5), pass(-3.4), pass(-3.3), pass(-3.2), pass(-3.1)),
            )
        assertThat(sky.highlights).hasSize(3)
    }

    @Test
    fun `no passes means no satellite events at all`() {
        // The caller passes an empty list when the layer is off, which is how one gate covers the
        // widget and the digest consistently without a preference read reaching a pure module.
        val sky =
            tonightSky(
                now = Instant.parse("2026-08-15T18:00:00Z"),
                location = null,
                showers = emptyList(),
            )
        assertThat(sky.highlights.filterIsInstance<SkyEvent.SatellitePassTonight>()).isEmpty()
    }

    @Test
    fun `a bright pass is worth announcing`() {
        // worthAnnouncing drives the D77 digest. A -3 pass clears the 0.5 threshold; a marginal
        // one should not wake anybody up.
        fun announced(mag: Double) =
            tonightSky(
                now = Instant.parse("2026-08-15T18:00:00Z"),
                location = null,
                showers = emptyList(),
                passes = listOf(pass(mag)),
            ).worthAnnouncing

        assertThat(announced(-3.2)).isTrue()
        assertThat(announced(2.0)).isFalse()
    }
}
