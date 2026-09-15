/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.eclipses

import com.google.android.stardroid.astronomy.LunarEclipseCircumstances
import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * [EclipseAlertScheduler.alertTime] — the one piece of the alert pipeline that's a pure function
 * of [LunarEclipseCircumstances] rather than `Context`/`WorkManager` plumbing (untested here for
 * the same reason `PassAlerts.kt` is: it needs a device to verify meaningfully, per D103's own
 * precedent).
 */
class EclipseAlertSchedulerTest {
    private val greatest = Instant.parse("2025-03-14T06:59:00Z")

    @Test
    fun `prefers the umbral contact when there is one`() {
        val circumstances =
            LunarEclipseCircumstances(
                type = LunarEclipseType.TOTAL,
                greatestEclipse = greatest,
                umbralMagnitude = 1.2,
                penumbralMagnitude = 2.3,
                penumbralBegin = greatest - 3.hours,
                umbralBegin = greatest - 2.hours,
                totalityBegin = greatest - 1.hours,
                totalityEnd = greatest + 1.hours,
                umbralEnd = greatest + 2.hours,
                penumbralEnd = greatest + 3.hours,
            )
        assertThat(EclipseAlertScheduler.alertTime(circumstances))
            .isEqualTo(circumstances.umbralBegin)
    }

    @Test
    fun `falls back to the penumbral contact for a penumbral-only eclipse`() {
        val circumstances =
            LunarEclipseCircumstances(
                type = LunarEclipseType.PENUMBRAL,
                greatestEclipse = greatest,
                umbralMagnitude = -0.1,
                penumbralMagnitude = 0.4,
                penumbralBegin = greatest - 1.hours,
                penumbralEnd = greatest + 1.hours,
            )
        assertThat(EclipseAlertScheduler.alertTime(circumstances))
            .isEqualTo(circumstances.penumbralBegin)
    }

    @Test
    fun `falls back to greatest eclipse when somehow no contacts are known`() {
        val circumstances =
            LunarEclipseCircumstances(
                type = LunarEclipseType.PARTIAL,
                greatestEclipse = greatest,
                umbralMagnitude = 0.3,
                penumbralMagnitude = 1.1,
            )
        assertThat(EclipseAlertScheduler.alertTime(circumstances)).isEqualTo(greatest)
    }
}
