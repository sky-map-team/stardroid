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
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class NotificationPlannerTest {
    private val now = Instant.parse("2026-08-12T10:00:00Z")
    private val sunset = Instant.parse("2026-08-12T19:30:00Z")

    private val perseidsTonight =
        SkyEvent.ShowerPeak(PERSEIDS, sunset + 4.hours, daysUntilPeak = 0, 0.1, quality = 0.9)
    private val perseidsSoon = perseidsTonight.copy(daysUntilPeak = 2, quality = 0.4)
    private val fullMoon = SkyEvent.MoonPhaseTonight(LunarPhase.FULL, sunset, quality = 0.6)
    private val brightMoon = SkyEvent.BrightMoon(0.9, moonSet = null, quality = 0.2)

    private fun sky(
        vararg events: SkyEvent,
        sunset: Instant? = this.sunset,
    ) = TonightSky(sunset, darknessStart = null, highlights = events.toList(), countdown = null)

    private fun plan(
        sky: TonightSky,
        showers: Boolean = true,
        digest: Boolean = true,
        at: Instant = now,
    ) = NotificationPlanner.plan(at, sky, showers, digest)

    @Test
    fun aQuietNightYieldsNothing() {
        assertThat(plan(sky())).isNull()
        assertThat(plan(sky(brightMoon))).isNull()
        assertThat(plan(sky(perseidsSoon, brightMoon))).isNull()
    }

    @Test
    fun aShowerPeakTonightTakesTheSlotOverTheDigest() {
        val planned = plan(sky(perseidsTonight, fullMoon))!!

        assertThat(planned.kind).isEqualTo(NotificationKind.SHOWER_PEAK)
        assertThat(planned.showerPeak).isEqualTo(perseidsTonight)
    }

    @Test
    fun withShowerAlertsOffTheDigestIsUsed() {
        val planned = plan(sky(perseidsTonight, fullMoon), showers = false)!!

        assertThat(planned.kind).isEqualTo(NotificationKind.TONIGHT_DIGEST)
        assertThat(planned.showerPeak).isNull()
    }

    @Test
    fun aWorthyNightWithNoPeakIsADigest() {
        assertThat(plan(sky(fullMoon))!!.kind).isEqualTo(NotificationKind.TONIGHT_DIGEST)
    }

    @Test
    fun nothingIsPlannedWithBothKindsOff() {
        assertThat(plan(sky(perseidsTonight, fullMoon), showers = false, digest = false)).isNull()
    }

    @Test
    fun aPeakIsNotADigestWhenOnlyShowersAreOffAndTheNightIsQuiet() {
        assertThat(plan(sky(perseidsSoon), showers = false)).isNull()
    }

    @Test
    fun itFiresHalfAnHourAfterSunset() {
        assertThat(plan(sky(fullMoon))!!.fireAt).isEqualTo(sunset + 30.minutes)
    }

    @Test
    fun enabledAfterThatTimeItFiresInAMinute() {
        val late = sunset + 2.hours

        assertThat(plan(sky(fullMoon), at = late)!!.fireAt).isEqualTo(late + 1.minutes)
    }

    @Test
    fun withoutALocationItFiresHalfAnHourFromNow() {
        assertThat(plan(sky(fullMoon, sunset = null))!!.fireAt).isEqualTo(now + 30.minutes)
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
