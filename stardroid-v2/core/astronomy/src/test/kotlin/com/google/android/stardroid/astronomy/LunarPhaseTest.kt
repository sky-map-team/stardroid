/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.days

/**
 * Lunar phase classification and event finding. Phase-event reference times are from published
 * almanacs (timeanddate.com); the tolerance reflects the low-precision ephemeris plus the
 * RA-based elongation proxy.
 */
class LunarPhaseTest {
    private val eventTol = 0.3.days // ~7 hours

    private fun assertNear(
        actual: Instant,
        expected: Instant,
    ) {
        val diff = (actual - expected)
        assertThat(abs(diff.inWholeMinutes)).isLessThan(eventTol.inWholeMinutes)
    }

    @Test
    fun bucket_classifiesFullAndNewMoon() {
        // 2024-01-25 17:54 UTC full moon; 2024-02-09 22:59 UTC new moon.
        assertThat(lunarPhaseBucket(utc(2024, 1, 25, 17, 54))).isEqualTo(LunarPhase.FULL)
        assertThat(lunarPhaseBucket(utc(2024, 2, 9, 22, 59))).isEqualTo(LunarPhase.NEW)
    }

    @Test
    fun nextLunarPhaseEvent_findsFullMoon() {
        val full = nextLunarPhaseEvent(LunarPhase.FULL, utc(2024, 1, 20))
        assertNear(full, utc(2024, 1, 25, 17, 54))
        assertThat(lunarPhaseBucket(full)).isEqualTo(LunarPhase.FULL)
    }

    @Test
    fun nextLunarPhaseEvent_findsNewMoon() {
        val new = nextLunarPhaseEvent(LunarPhase.NEW, utc(2024, 2, 1))
        assertNear(new, utc(2024, 2, 9, 22, 59))
        assertThat(lunarPhaseBucket(new)).isEqualTo(LunarPhase.NEW)
    }

    @Test
    fun nextLunarPhaseEvent_isStrictlyAfterAndConverges() {
        val after = utc(2024, 6, 1, 12)
        for (phase in LunarPhase.entries) {
            val event = nextLunarPhaseEvent(phase, after)
            assertThat(event).isGreaterThan(after)
            // Within one synodic month.
            assertThat((event - after).inWholeDays).isLessThan(31)
        }
    }
}
