/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.events

import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

/**
 * The events engine over the real low-precision ephemeris. Assertions are structural (which
 * events, in what order) rather than numeric where the ephemeris tolerance would make values
 * fragile; reference dates from published almanacs (timeanddate.com).
 */
class TonightSkyTest {
    @Test
    fun showerPeakNight_isTheTopHighlight() {
        // Perseids peak Aug 12; 2024-08-12 moon was ~50% first quarter, sets before dawn.
        val sky = tonightSky(utc(2024, 8, 12, 18), LONDON, listOf(PERSEIDS), UTC)
        val peak = sky.highlights.filterIsInstance<SkyEvent.ShowerPeak>().single()
        assertThat(peak.daysUntilPeak).isEqualTo(0)
        assertThat(peak.shower.id).isEqualTo(PERSEIDS.id)
        assertThat(sky.highlights.first()).isEqualTo(peak)
    }

    @Test
    fun approachingShower_isCountdownOnly_beyondThreeDays() {
        val sky = tonightSky(utc(2024, 8, 1, 18), LONDON, listOf(PERSEIDS), UTC)
        assertThat(sky.highlights.filterIsInstance<SkyEvent.ShowerPeak>()).isEmpty()
        val countdown = sky.countdown as CountdownTarget.ToShowerPeak
        assertThat(countdown.shower.id).isEqualTo(PERSEIDS.id)
    }

    @Test
    fun countdown_withoutShowers_fallsToNextMoonExtreme() {
        // 2024-01-25 17:54 UTC full moon: from Jan 20 the full moon is the nearest extreme.
        val sky = tonightSky(utc(2024, 1, 20, 18), LONDON, emptyList(), UTC)
        val countdown = sky.countdown as CountdownTarget.ToMoonPhase
        assertThat(countdown.phase).isEqualTo(LunarPhase.FULL)
    }

    @Test
    fun minorShower_neverMakesTheCountdown() {
        val minor = PERSEIDS.copy(id = CelestialObjectId("shower/minor"), peakZhr = 5)
        val sky = tonightSky(utc(2024, 8, 1, 18), LONDON, listOf(minor), UTC)
        assertThat(sky.countdown).isInstanceOf(CountdownTarget.ToMoonPhase::class.java)
    }

    @Test
    fun brightMoon_neverTheLoneHighlight() {
        // 2024-01-25 is a full moon: bright-moon caveat applies, but with no shower and
        // (possibly) no well-placed planet it must not stand alone as a fake "highlight".
        val sky = tonightSky(utc(2024, 1, 25, 18), LONDON, emptyList(), UTC)
        val brightMoonOnly = sky.highlights.all { it is SkyEvent.BrightMoon }
        if (brightMoonOnly) assertThat(sky.highlights).isEmpty()
    }

    @Test
    fun wellPlacedPlanet_reportedWithDirectionAndAltitude() {
        // Jupiter around its 2023-11-03 opposition: high and bright all evening in London.
        val sky = tonightSky(utc(2023, 11, 10, 17), LONDON, emptyList(), UTC)
        val jupiter =
            sky.highlights
                .filterIsInstance<SkyEvent.WellPlacedPlanet>()
                .single { it.body == SolarSystemBody.JUPITER }
        assertThat(jupiter.bestAltitudeDeg).isGreaterThan(25.0)
        assertThat(jupiter.azimuthDeg).isAtLeast(0.0)
        assertThat(jupiter.azimuthDeg).isAtMost(360.0)
        assertThat(jupiter.from).isGreaterThan(utc(2023, 11, 10, 16))
    }

    @Test
    fun nullLocation_stillCountsDown_butHasNoLocalTimes() {
        val sky = tonightSky(utc(2024, 8, 1, 18), null, listOf(PERSEIDS), UTC)
        assertThat(sky.sunset).isNull()
        assertThat(sky.darknessStart).isNull()
        assertThat(sky.highlights.filterIsInstance<SkyEvent.WellPlacedPlanet>()).isEmpty()
        assertThat(sky.countdown).isInstanceOf(CountdownTarget.ToShowerPeak::class.java)
    }

    @Test
    fun showerPeakNight_isWorthAnnouncing_andExposesThePeak() {
        val sky = tonightSky(utc(2024, 8, 12, 18), LONDON, listOf(PERSEIDS), UTC)
        assertThat(sky.worthAnnouncing).isTrue()
        assertThat(sky.showerPeakTonight?.shower?.id).isEqualTo(PERSEIDS.id)
    }

    @Test
    fun brightMoonAlone_isNotWorthAnnouncing() {
        val sky = tonightSky(utc(2024, 1, 25, 18), LONDON, emptyList(), UTC)
        if (sky.highlights.all { it.quality < TonightSky.ANNOUNCE_THRESHOLD }) {
            assertThat(sky.worthAnnouncing).isFalse()
        }
        assertThat(sky.showerPeakTonight).isNull()
    }

    @Test
    fun totalLunarEclipse_isTheTopHighlight_whereMoonIsUp() {
        // The 2025-03-14 total lunar eclipse, greatest eclipse ~06:59 UTC — near local midnight
        // in Denver, so the Moon is up and the eclipse should surface as tonight's top highlight.
        val sky = tonightSky(utc(2025, 3, 13, 22), DENVER, emptyList(), UTC)
        val eclipse = sky.highlights.filterIsInstance<SkyEvent.LunarEclipseUpcoming>().single()
        assertThat(eclipse.circumstances.type).isEqualTo(LunarEclipseType.TOTAL)
        assertThat(sky.highlights.first()).isEqualTo(eclipse)
        assertThat(sky.worthAnnouncing).isTrue()
    }

    @Test
    fun totalLunarEclipse_notHighlighted_whereMoonIsDown() {
        // Same eclipse, but near local noon in Dhaka: the Moon (opposite the full-moon Sun) is
        // below the horizon throughout, so it must not be reported as visible there.
        val sky = tonightSky(utc(2025, 3, 13, 22), DHAKA, emptyList(), UTC)
        assertThat(sky.highlights.filterIsInstance<SkyEvent.LunarEclipseUpcoming>()).isEmpty()
    }

    @Test
    fun lunarEclipse_notHighlighted_beyondThreeDayHorizon() {
        val sky = tonightSky(utc(2025, 2, 20, 18), DENVER, emptyList(), UTC)
        assertThat(sky.highlights.filterIsInstance<SkyEvent.LunarEclipseUpcoming>()).isEmpty()
    }

    @Test
    fun lunarEclipse_surfacesEvenWithoutALocation() {
        val sky = tonightSky(utc(2025, 3, 13, 22), null, emptyList(), UTC)
        assertThat(sky.highlights.filterIsInstance<SkyEvent.LunarEclipseUpcoming>()).hasSize(1)
    }

    @Test
    fun highlights_cappedAtThree_bestFirst() {
        val sky = tonightSky(utc(2024, 8, 12, 18), LONDON, listOf(PERSEIDS), UTC)
        assertThat(sky.highlights.size).isAtMost(3)
        assertThat(sky.highlights).isEqualTo(sky.highlights.sortedByDescending { it.quality })
    }

    private companion object {
        val UTC = TimeZone.UTC
        val LONDON = LatLong(51.51, -0.13)
        val DENVER = LatLong(39.74, -104.99)
        val DHAKA = LatLong(23.81, 90.41)
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

        fun utc(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
        ) = LocalDateTime(year, month, day, hour, 0, 0).toInstant(TimeZone.UTC)
    }
}
