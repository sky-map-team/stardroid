/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Who may schedule the CelesTrak download (#1272): the rule 2.3.0 lacked, which let a reboot or a
 * clock change schedule downloads for installs nobody used (#1269). The gates themselves are
 * pinned in `SatelliteFetchConditionsTest`.
 */
class SatelliteScheduleActionTest {
    private val now = Instant.fromEpochMilliseconds(1_794_000_000_000)

    private val ready =
        SatelliteFetchConditions(
            featureEnabled = true,
            dataConsented = true,
            layerEnabled = true,
            termsAccepted = true,
            lastForegroundAt = now - 1.hours,
            backgroundUse = false,
        )

    private val lapsed = ready.copy(lastForegroundAt = now - 30.days)

    private val layerOff = ready.copy(layerEnabled = false)

    @Test
    fun `a background-only process start never schedules`() {
        assertThat(satelliteScheduleAction(ready, now, userVisible = false))
            .isEqualTo(SatelliteScheduleAction.LEAVE)
    }

    @Test
    fun `a background-only process start still cancels when a setting is off`() {
        assertThat(satelliteScheduleAction(layerOff, now, userVisible = false))
            .isEqualTo(SatelliteScheduleAction.CANCEL)
    }

    @Test
    fun `a background-only process start leaves lapsed use to the worker`() {
        assertThat(satelliteScheduleAction(lapsed, now, userVisible = false))
            .isEqualTo(SatelliteScheduleAction.LEAVE)
    }

    @Test
    fun `a session the user sees schedules when every gate is open`() {
        assertThat(satelliteScheduleAction(ready, now, userVisible = true))
            .isEqualTo(SatelliteScheduleAction.SCHEDULE)
    }

    @Test
    fun `a session the user sees cancels when a gate is closed`() {
        assertThat(satelliteScheduleAction(layerOff, now, userVisible = true))
            .isEqualTo(SatelliteScheduleAction.CANCEL)
        assertThat(satelliteScheduleAction(lapsed, now, userVisible = true))
            .isEqualTo(SatelliteScheduleAction.CANCEL)
    }

    @Test
    fun `a background start never cancels what a visible session schedules`() {
        // MainActivity and SkyMapApplication both follow the conditions in a visible session, so
        // the background rule must not undo the visible one.
        val lastUses = listOf(null, now - 1.hours, now - 30.days)
        // Every combination of the five boolean gates, one per bit.
        for (bits in 0 until 32) {
            for (lastUse in lastUses) {
                val conditions =
                    SatelliteFetchConditions(
                        featureEnabled = bits and 1 != 0,
                        dataConsented = bits and 2 != 0,
                        layerEnabled = bits and 4 != 0,
                        termsAccepted = bits and 8 != 0,
                        lastForegroundAt = lastUse,
                        backgroundUse = bits and 16 != 0,
                    )
                val visible = satelliteScheduleAction(conditions, now, userVisible = true)
                if (visible == SatelliteScheduleAction.SCHEDULE) {
                    assertThat(satelliteScheduleAction(conditions, now, userVisible = false))
                        .isEqualTo(SatelliteScheduleAction.LEAVE)
                }
            }
        }
    }
}
