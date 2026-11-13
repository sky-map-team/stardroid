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
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.nextLunarEclipse
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.testing.assertThat
import com.google.android.stardroid.text.TextSource
import kotlinx.datetime.Instant
import kotlin.test.Test

class NotificationTextTest {
    private val peakTime = Instant.parse("2026-08-12T23:00:00Z")
    private val london = LatLong(51.5, -0.1)

    // Each string comes back as its id and arguments, so the tests read what was asked for.
    private val text =
        NotificationText(
            texts = TextSource { id, args -> "$id(${args.joinToString()})" },
            formatTime = { "t" },
        )

    private fun peak(moon: Double) = SkyEvent.ShowerPeak(PERSEIDS, peakTime, 0, moon, quality = 0.9)

    @Test
    fun `the shower title names the shower`() {
        assertThat(text.showerTitle(peak(0.1))).isEqualTo("NOTIF_SHOWER_TITLE(Perseids)")
    }

    @Test
    fun `a dark moon gives only the moon line when there is no location`() {
        assertThat(text.showerBody(peak(0.1), location = null))
            .isEqualTo("NOTIF_SHOWER_DARK_MOON(100)")
    }

    @Test
    fun `with a location the direction comes first - then the bright moon warning`() {
        val body = text.showerBody(peak(0.9), london)

        assertThat(body.startsWith("NOTIF_SHOWER_DIRECTION(COMPASS_")).isTrue()
        assertThat(body.endsWith("NOTIF_SHOWER_BRIGHT_MOON(90, 100)")).isTrue()
    }

    @Test
    fun `the digest is the highlight sentences in order`() {
        val sky =
            TonightSky(
                sunset = null,
                darknessStart = null,
                highlights =
                    listOf(
                        SkyEvent.MoonPhaseTonight(LunarPhase.FULL, peakTime, quality = 0.6),
                        peak(0.1),
                    ),
                countdown = null,
            )

        assertThat(text.digestLines(sky))
            .containsExactly("TONIGHT_FULL_MOON()", "TONIGHT_SHOWER_PEAK(Perseids)")
            .inOrder()
    }

    @Test
    fun `every solar system body has a name`() {
        for (body in SolarSystemBody.entries) {
            val event = SkyEvent.WellPlacedPlanet(body, peakTime, 40.0, 180.0, quality = 0.5)

            assertThat(text.highlight(event).text.startsWith("TONIGHT_WELL_PLACED(BODY_")).isTrue()
        }
    }

    @Test
    fun `a station is named by its translated name and another satellite by its element set`() {
        assertThat(text.passTitle(25544, "ISS (ZARYA)"))
            .isEqualTo("NOTIF_PASS_TITLE(SATELLITE_NAME_ISS())")
        assertThat(text.passTitle(48274, "CSS (TIANHE)"))
            .isEqualTo("NOTIF_PASS_TITLE(SATELLITE_NAME_TIANGONG())")
        assertThat(text.passTitle(99999, "SOMETHING"))
            .isEqualTo("NOTIF_PASS_TITLE(SOMETHING)")
    }

    @Test
    fun `the pass body gives the start time`() {
        assertThat(text.passBody(peakTime)).isEqualTo("NOTIF_PASS_TEXT(t)")
    }

    @Test
    fun `an eclipse is named by its type and timed by its peak`() {
        val eclipse = nextLunarEclipse(Instant.parse("2026-02-01T12:00:00Z"))!!

        assertThat(text.eclipseTitle(eclipse))
            .isEqualTo("NOTIF_ECLIPSE_TITLE(TONIGHT_LUNAR_ECLIPSE_TOTAL())")
        assertThat(text.eclipseBody(eclipse)).isEqualTo("NOTIF_ECLIPSE_TEXT(t)")
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
