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
import com.google.android.stardroid.astronomy.nextLunarEclipse
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.testing.assertThat
import com.google.android.stardroid.text.TextSource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class NotificationPlanAheadTest {
    private val london = LatLong(51.5, -0.1)
    private val utc = TimeZone.UTC
    private val planAhead =
        NotificationPlanAhead(
            words =
                NotificationText(
                    texts = TextSource { id, args -> "$id(${args.joinToString()})" },
                    formatTime = { "t" },
                ),
            satelliteObjectId = { "satellite/$it" },
        )

    private fun inputs(
        now: String,
        preferences: AlertPreferences,
        passes: List<SatellitePass> = emptyList(),
        eclipse: com.google.android.stardroid.astronomy.LunarEclipseCircumstances? = null,
        location: LatLong = london,
    ) = NotificationPlanAhead.Inputs(
        now = Instant.parse(now),
        zone = utc,
        location = location,
        showers = listOf(PERSEIDS),
        passes = passes,
        eclipse = eclipse,
        preferences = preferences,
    )

    private fun pass(
        start: String,
        magnitude: Double,
    ): SatellitePass {
        val begin = Instant.parse(start)
        return SatellitePass(
            "ISS (ZARYA)",
            25544,
            begin,
            begin + 3.minutes,
            begin + 6.minutes,
            60.0,
            270.0,
            90.0,
            magnitude,
            null,
        )
    }

    @Test
    fun `nothing is planned with every kind off`() {
        assertThat(planAhead.plan(inputs("2026-08-10T12:00:00Z", AlertPreferences()))).isEmpty()
    }

    @Test
    fun `a shower peak within the next nights is planned for that night`() {
        val planned =
            planAhead.plan(inputs("2026-08-10T12:00:00Z", AlertPreferences(showers = true)))

        assertThat(planned.map { it.id }).containsExactly("showers-2026-08-12")
        val alert = planned.single()
        assertThat(alert.title).isEqualTo("NOTIF_SHOWER_TITLE(Perseids)")
        assertThat(alert.threadId).isEqualTo("showers")
        assertThat(alert.objectId).isEqualTo("shower/perseids")
    }

    @Test
    fun `planning again after the alert has gone does not announce the night twice`() {
        val planned =
            planAhead.plan(inputs("2026-08-12T22:00:00Z", AlertPreferences(showers = true)))

        assertThat(planned).isEmpty()
    }

    @Test
    fun `nights with no sunset are not planned`() {
        // Svalbard: midnight sun in June, polar night in December.
        val svalbard = LatLong(78.2, 15.6)
        for (now in listOf("2026-06-20T08:00:00Z", "2026-12-20T08:00:00Z")) {
            val planned =
                planAhead.plan(
                    inputs(now, AlertPreferences(digest = true), location = svalbard),
                )

            assertThat(planned).isEmpty()
        }
    }

    @Test
    fun `the same inputs plan the same ids so a replan replaces rather than duplicates`() {
        val first = planAhead.plan(inputs("2026-08-10T12:00:00Z", AlertPreferences(showers = true)))
        val second =
            planAhead.plan(
                inputs("2026-08-10T15:30:00Z", AlertPreferences(showers = true)),
            )

        assertThat(second.map { it.id }).isEqualTo(first.map { it.id })
    }

    @Test
    fun `a pass alert is planned for each night's best pass six minutes ahead`() {
        val tonight = pass("2026-11-10T18:00:00Z", -2.0)
        val tomorrow = pass("2026-11-11T18:10:00Z", -3.0)

        val planned =
            planAhead.plan(
                inputs(
                    "2026-11-10T12:00:00Z",
                    AlertPreferences(passes = true),
                    passes = listOf(tomorrow, tonight),
                ),
            )

        assertThat(planned.map { it.id }).containsExactly("pass-2026-11-10", "pass-2026-11-11")
            .inOrder()
        assertThat(planned.first().fireAt).isEqualTo(tonight.start - 6.minutes)
        assertThat(planned.first().objectId).isEqualTo("satellite/25544")
    }

    private val eclipseNow = Instant.parse("2026-02-01T12:00:00Z")

    @Test
    fun `an eclipse within two months is planned and one further off waits`() {
        val eclipse = nextLunarEclipse(eclipseNow)!!
        val newYork =
            inputs("2026-02-01T12:00:00Z", AlertPreferences(eclipses = true), eclipse = eclipse)
                .let {
                    NotificationPlanAhead.Inputs(
                        it.now,
                        it.zone,
                        LatLong(40.7, -74.0),
                        it.showers,
                        it.passes,
                        it.eclipse,
                        it.preferences,
                    )
                }
        val early = Instant.parse("2025-12-01T12:00:00Z")

        assertThat(planAhead.plan(newYork).map { it.id }).containsExactly("eclipse-2026-03-03")
        assertThat(
            planAhead.plan(
                NotificationPlanAhead.Inputs(
                    early,
                    utc,
                    LatLong(40.7, -74.0),
                    emptyList(),
                    emptyList(),
                    eclipse,
                    AlertPreferences(eclipses = true),
                ),
            ),
        ).isEmpty()
    }

    @Test
    fun `a pass is not alerted when passes are switched off`() {
        val planned =
            planAhead.plan(
                inputs(
                    "2026-11-10T12:00:00Z",
                    AlertPreferences(showers = true),
                    passes = listOf(pass("2026-11-10T18:00:00Z", -3.0)),
                ),
            )

        assertThat(planned.none { it.threadId == "passes" }).isTrue()
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
