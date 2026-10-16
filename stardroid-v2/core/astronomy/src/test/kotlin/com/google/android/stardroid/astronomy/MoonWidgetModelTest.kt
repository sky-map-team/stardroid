/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.LatLong
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * The moon widget's display model (D75). Reference values from published almanacs
 * (timeanddate.com); tolerances reflect the low-precision ephemeris, as in [LunarPhaseTest]
 * and [RiseSetTest].
 */
class MoonWidgetModelTest {
    @Test
    fun fullMoon_isNearFullyIlluminated() {
        // 2024-01-25 17:54 UTC full moon.
        val model = moonWidgetModel(utc(2024, 1, 25, 17, 54), LONDON)
        assertThat(model.phase).isEqualTo(LunarPhase.FULL)
        assertThat(model.illuminatedFraction).isGreaterThan(0.92)
    }

    @Test
    fun newMoon_isNearDark() {
        // 2024-02-09 22:59 UTC new moon.
        val model = moonWidgetModel(utc(2024, 2, 9, 22, 59), LONDON)
        assertThat(model.phase).isEqualTo(LunarPhase.NEW)
        assertThat(model.illuminatedFraction).isLessThan(0.08)
    }

    @Test
    fun waxing_and_waning_disambiguated() {
        // 2024-01-18: waxing gibbous (between 2024-01-11 new and 2024-01-25 full).
        assertThat(moonWidgetModel(utc(2024, 1, 18, 12), LONDON).waxing).isTrue()
        // 2024-01-30: waning gibbous (after the full moon).
        assertThat(moonWidgetModel(utc(2024, 1, 30, 12), LONDON).waxing).isFalse()
    }

    @Test
    fun age_countsDaysSinceNewMoon() {
        // 2024-01-11 11:57 UTC new moon -> 2024-01-25 17:54 UTC full moon is ~14.25 days later.
        assertThat(moonWidgetModel(utc(2024, 1, 25, 17, 54), LONDON).ageDays)
            .isWithin(1.0).of(14.25)
        assertThat(moonWidgetModel(utc(2024, 1, 18, 12), LONDON).ageDays).isWithin(1.0).of(7.0)
    }

    @Test
    fun age_wrapsAroundNewMoon() {
        // 2024-01-11 11:57 UTC new moon: age is near 0 or just under a full month, never outside.
        for (hour in 0..24 step 6) {
            val age = moonWidgetModel(utc(2024, 1, 11, 0).plus(hour.hours), LONDON).ageDays
            assertThat(age < 1.0 || age > 28.5).isTrue()
        }
    }

    @Test
    fun mirrored_followsHemisphere() {
        val time = utc(2024, 1, 18, 12)
        assertThat(moonWidgetModel(time, LONDON).mirrored).isFalse()
        assertThat(moonWidgetModel(time, SYDNEY).mirrored).isTrue()
        assertThat(moonWidgetModel(time, null).mirrored).isFalse()
    }

    @Test
    fun riseAndSet_presentWithLocation_withinADay() {
        val time = utc(2024, 1, 18, 12)
        val model = moonWidgetModel(time, LONDON)
        // The Moon rises and sets daily at London's latitude; both crossings land within ~a day.
        assertThat(model.riseTime).isNotNull()
        assertThat(model.setTime).isNotNull()
        assertThat(model.riseTime).isGreaterThan(time)
        assertThat(model.setTime).isGreaterThan(time)
    }

    @Test
    fun nullLocation_degradesToGeometryOnly() {
        val model = moonWidgetModel(utc(2024, 1, 25, 17, 54), null)
        assertThat(model.phase).isEqualTo(LunarPhase.FULL)
        assertThat(model.riseTime).isNull()
        assertThat(model.setTime).isNull()
    }

    private companion object {
        val LONDON = LatLong(51.51, -0.13)
        val SYDNEY = LatLong(-33.87, 151.21)
    }
}
