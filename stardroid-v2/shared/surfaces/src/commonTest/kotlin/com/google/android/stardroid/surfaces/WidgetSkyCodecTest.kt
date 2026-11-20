/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WidgetSkyCodecTest {
    private val showers =
        listOf(
            MeteorShower(
                CelestialObjectId("shower/perseids"),
                "Perséides",
                RaDec(46.2, 57.4),
                MonthDay(7, 17),
                MonthDay(8, 12),
                MonthDay(8, 24),
                100,
            ),
            MeteorShower(
                CelestialObjectId("shower/quadrantids"),
                "Quadrantids",
                RaDec(230.1, 48.5),
                MonthDay(1, 1),
                MonthDay(1, 4),
                MonthDay(1, 10),
                110,
            ),
        )

    private val start = Instant.parse("2026-08-10T21:14:05.250Z")
    private val passes =
        listOf(
            SatellitePass(
                "ISS (ZARYA)",
                25544,
                start,
                start + 3.minutes,
                start + 6.minutes,
                61.25,
                270.5,
                88.0,
                -3.4,
                start + 5.minutes,
            ),
            SatellitePass(
                "CSS (TIANHE)",
                48274,
                start,
                start + 90.seconds,
                start + 4.minutes,
                33.0,
                10.0,
                150.0,
                -1.2,
                null,
            ),
        )

    @Test
    fun `showers and passes come back as they went in`() {
        assertThat(WidgetSkyCodec.decodeShowers(WidgetSkyCodec.encodeShowers(showers)))
            .isEqualTo(showers)
        assertThat(WidgetSkyCodec.decodePasses(WidgetSkyCodec.encodePasses(passes)))
            .isEqualTo(passes)
    }

    @Test
    fun `nothing stored reads as none`() {
        assertThat(WidgetSkyCodec.decodeShowers("")).isEmpty()
        assertThat(WidgetSkyCodec.decodePasses("")).isEmpty()
    }

    @Test
    fun `a line that does not parse is dropped but the rest are kept`() {
        val badDate = "shower/x\tX\t1\t2\t13-40\t01-01\t01-02\t5"
        val text = "garbage\n" + WidgetSkyCodec.encodeShowers(showers.take(1)) + "\n" + badDate
        assertThat(WidgetSkyCodec.decodeShowers(text)).containsExactly(showers[0])
    }

    @Test
    fun `a tab in a name cannot split its record`() {
        val tabbed = showers[0].copy(name = "Per\tseids")
        assertThat(WidgetSkyCodec.decodeShowers(WidgetSkyCodec.encodeShowers(listOf(tabbed))))
            .containsExactly(tabbed.copy(name = "Per seids"))
    }
}
