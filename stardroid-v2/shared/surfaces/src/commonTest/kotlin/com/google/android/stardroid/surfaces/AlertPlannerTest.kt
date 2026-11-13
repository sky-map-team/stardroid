/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.surfaces

import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.nextLunarEclipse
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AlertPlannerTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val lead = 6.minutes
    private val utc = TimeZone.UTC

    private fun pass(
        start: String,
        magnitude: Double,
    ): SatellitePass {
        val begin = Instant.parse(start)
        return SatellitePass(
            satelliteName = "ISS (ZARYA)",
            noradId = 25544,
            start = begin,
            culmination = begin + 3.minutes,
            end = begin + 6.minutes,
            maxAltitudeDeg = 60.0,
            startAzimuthDeg = 270.0,
            endAzimuthDeg = 90.0,
            peakMagnitude = magnitude,
            shadowEntry = null,
        )
    }

    @Test
    fun `the brightest pass with enough notice is the one to alert`() {
        val dim = pass("2026-11-10T18:00:00Z", -1.0)
        val bright = pass("2026-11-10T19:40:00Z", -3.5)

        assertThat(NotificationPlanner.alertablePass(now, listOf(dim, bright), lead))
            .isEqualTo(bright)
    }

    @Test
    fun `a pass too close for its alert to land is skipped`() {
        val soon = pass("2026-11-10T12:07:00Z", -4.0)
        val later = pass("2026-11-10T18:00:00Z", -1.0)

        assertThat(NotificationPlanner.alertablePass(now, listOf(soon, later), lead))
            .isEqualTo(later)
    }

    @Test
    fun `there is one alert per night even when a night has several passes`() {
        val first = pass("2026-11-10T18:00:00Z", -2.0)
        val second = pass("2026-11-10T19:35:00Z", -3.9)

        val planned =
            NotificationPlanner.planPassAlerts(now, listOf(first, second), utc, lead, 3.days)

        assertThat(planned.map { it.pass }).containsExactly(second)
        assertThat(planned.single().fireAt).isEqualTo(second.start - lead)
    }

    @Test
    fun `a pass just after midnight belongs to the evening before`() {
        val evening = pass("2026-11-10T22:00:00Z", -1.5)
        val afterMidnight = pass("2026-11-11T00:40:00Z", -3.0)

        val planned =
            NotificationPlanner.planPassAlerts(
                now,
                listOf(evening, afterMidnight),
                utc,
                lead,
                3.days,
            )

        assertThat(planned.map { it.pass }).containsExactly(afterMidnight)
    }

    @Test
    fun `each night gets its own alert in order and the horizon is respected`() {
        val tonight = pass("2026-11-10T18:00:00Z", -2.0)
        val tomorrow = pass("2026-11-11T18:10:00Z", -3.0)
        val farOff = pass("2026-11-20T18:00:00Z", -4.0)

        val planned =
            NotificationPlanner.planPassAlerts(
                now,
                listOf(farOff, tomorrow, tonight),
                utc,
                lead,
                3.days,
            )

        assertThat(planned.map { it.pass }).containsExactly(tonight, tomorrow).inOrder()
    }

    private val springEclipseNow = Instant.parse("2026-02-01T12:00:00Z")
    private val eclipse = nextLunarEclipse(springEclipseNow)!!

    @Test
    fun `the next lunar eclipse is the total one in March 2026`() {
        assertThat(eclipse.type).isEqualTo(LunarEclipseType.TOTAL)
        assertThat(eclipse.greatestEclipse.toString().startsWith("2026-03-03")).isTrue()
    }

    @Test
    fun `an eclipse visible from here is alerted half an hour before its umbral start`() {
        val newYork = LatLong(40.7, -74.0)

        val planned = NotificationPlanner.planEclipseAlert(springEclipseNow, eclipse, newYork)!!

        assertThat(planned.fireAt).isEqualTo(eclipse.umbralBegin!! - 30.minutes)
    }

    @Test
    fun `an eclipse the moon is not up for is not alerted`() {
        val london = LatLong(51.5, -0.1)

        assertThat(NotificationPlanner.planEclipseAlert(springEclipseNow, eclipse, london)).isNull()
    }

    @Test
    fun `with no location the alert is sent anyway`() {
        assertThat(NotificationPlanner.planEclipseAlert(springEclipseNow, eclipse, null))
            .isNotNull()
    }

    @Test
    fun `an eclipse already under way is alerted a minute from now`() {
        val underWay = eclipse.umbralBegin!! + 20.minutes

        val planned = NotificationPlanner.planEclipseAlert(underWay, eclipse, null)!!

        assertThat(planned.fireAt).isEqualTo(underWay + 1.minutes)
    }

    @Test
    fun `an eclipse that has ended is not alerted`() {
        val after = (eclipse.penumbralEnd ?: eclipse.greatestEclipse) + 1.hours

        assertThat(NotificationPlanner.planEclipseAlert(after, eclipse, null)).isNull()
    }

    @Test
    fun `a horizon holds back an eclipse that is too far off`() {
        assertThat(
            NotificationPlanner.planEclipseAlert(
                springEclipseNow,
                eclipse,
                null,
                horizon = 30.hours,
            ),
        ).isNull()
    }
}
