/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TonightWidgetSurfaceTest {
    private val london = LatLong(51.5, -0.1)
    private val utc = TimeZone.UTC
    private val now = Instant.parse("2026-08-10T12:00:00Z")

    private fun pass(start: Instant) =
        SatellitePass(
            "ISS (ZARYA)",
            25544,
            start,
            start + 3.minutes,
            start + 6.minutes,
            60.0,
            270.0,
            90.0,
            -3.0,
            null,
        )

    @Test
    fun `tonight's passes are those beginning within the horizon`() {
        val soon = pass(now + 2.hours)
        val begun = pass(now - 1.minutes)
        val tomorrow = pass(now + TONIGHT_PASS_HORIZON)
        assertThat(listOf(begun, soon, tomorrow).tonightFrom(now)).containsExactly(soon)
    }

    @Test
    fun `rows open their object's card`() {
        val jupiter = SkyEvent.WellPlacedPlanet(SolarSystemBody.JUPITER, now, 40.0, 180.0, 0.7)
        assertThat(jupiter.targetObjectId).isEqualTo("planet/jupiter")
        assertThat(SkyEvent.SatellitePassTonight(pass(now), 0.5).targetObjectId)
            .isEqualTo("satellite/25544")
        assertThat(SkyEvent.ShowerPeak(PERSEIDS, now, 0, 0.1, 0.8).targetObjectId)
            .isEqualTo("shower/perseids")
        assertThat(SkyEvent.MoonPhaseTonight(LunarPhase.FULL, now, 0.5).targetObjectId)
            .isEqualTo(MOON_OBJECT_ID)
        assertThat(CountdownTarget.ToMoonPhase(LunarPhase.NEW, now).targetObjectId)
            .isEqualTo(MOON_OBJECT_ID)
        assertThat(CountdownTarget.ToShowerPeak(PERSEIDS, now, darkPeak = true).targetObjectId)
            .isEqualTo("shower/perseids")
    }

    @Test
    fun `the ring fills over the last thirty days`() {
        assertThat(countdownRingProgress(40)).isEqualTo(0f)
        assertThat(countdownRingProgress(COUNTDOWN_RING_DAYS)).isEqualTo(0f)
        assertThat(countdownRingProgress(15)).isEqualTo(0.5f)
        assertThat(countdownRingProgress(0)).isEqualTo(1f)
    }

    @Test
    fun `dots grade the highlights as Android's do`() {
        assertThat(highlightDot(0.6)).isEqualTo(HighlightDot.GOLD)
        assertThat(highlightDot(0.4)).isEqualTo(HighlightDot.BLUE)
        assertThat(highlightDot(0.39)).isEqualTo(HighlightDot.DIM)
    }

    @Test
    fun `each tonight entry is the events engine's answer at its own time`() {
        val passes = listOf(pass(now + 3.hours))
        val entries =
            tonightWidgetTimeline(now, london, listOf(PERSEIDS), passes, utc, span = 4.hours)
        assertThat(entries).hasSize(5)
        assertThat(entries[1].time).isEqualTo(now + 1.hours)
        assertThat(entries[1].sky)
            .isEqualTo(tonightSky(now + 1.hours, london, listOf(PERSEIDS), utc, passes = passes))
        // Once the pass has begun it is no longer tonight's to show.
        assertThat(entries[4].sky)
            .isEqualTo(tonightSky(now + 4.hours, london, listOf(PERSEIDS), utc))
    }

    @Test
    fun `countdown entries fall at each local midnight and count down`() {
        val entries = countdownWidgetTimeline(now, listOf(PERSEIDS), utc, days = 2)
        assertThat(entries.map { it.time }).containsExactly(
            now,
            Instant.parse("2026-08-11T00:00:00Z"),
            Instant.parse("2026-08-12T00:00:00Z"),
        )
        val first = entries.first()
        assertThat(first.target).isEqualTo(tonightSky(now, null, listOf(PERSEIDS), utc).countdown)
        assertThat(entries[1].daysRemaining).isEqualTo(first.daysRemaining - 1)
    }

    private companion object {
        val PERSEIDS =
            MeteorShower(
                id = CelestialObjectId("shower/perseids"),
                name = "Perseids",
                radiant = RaDec(46.2, 57.4),
                activeFrom = MonthDay(7, 17),
                peak = MonthDay(8, 12),
                activeTo = MonthDay(8, 24),
                peakZhr = 100,
            )
    }
}
