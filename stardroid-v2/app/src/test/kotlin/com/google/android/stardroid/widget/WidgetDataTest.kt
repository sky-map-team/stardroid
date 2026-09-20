/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * The widgets' `provideGlance` data step (D75): with the shipped flags on each widget gets
 * real content, and with its flag off it gets null — the quiet frozen tile — without touching
 * settings, the catalog or the satellite cache.
 */
class WidgetDataTest {
    private val now = Instant.parse("2026-08-12T18:00:00Z")
    private val london = LatLong(51.5, -0.1)

    private val allOff = ExperimentConfig { false }
    private val onlyMoon = ExperimentConfig { it == Experiment.MOON_WIDGET }
    private val onlyTonight = ExperimentConfig { it == Experiment.TONIGHT_WIDGET }

    /** A flow that fails the test if anyone collects it. */
    private fun <T> untouched(): Flow<T> = flow { error("must not be read while disabled") }

    @Test
    fun `moon widget with the shipped flags gets a model with times at a known location`() =
        runTest {
            val model =
                moonWidgetModelFor(ExperimentConfig.Static, now, MutableStateFlow(london))

            assertThat(model).isNotNull()
            assertThat(model!!.illuminatedFraction).isAtLeast(0.0)
            assertThat(model.illuminatedFraction).isAtMost(1.0)
            assertThat(model.riseTime != null || model.setTime != null).isTrue()
        }

    @Test
    fun `moon widget without a saved location degrades to geometry only`() =
        runTest {
            val model = moonWidgetModelFor(ExperimentConfig.Static, now, MutableStateFlow(null))

            assertThat(model).isNotNull()
            assertThat(model!!.riseTime).isNull()
            assertThat(model.setTime).isNull()
        }

    @Test
    fun `moon widget is null and reads nothing when its flag is off`() =
        runTest {
            assertThat(moonWidgetModelFor(onlyTonight, now, untouched())).isNull()
            assertThat(moonWidgetModelFor(allOff, now, untouched())).isNull()
        }

    @Test
    fun `tonight widget with the shipped flags gets a sky and asks for passes at the location`() =
        runTest {
            var passesAskedAt: LatLong? = null
            val sky =
                tonightSkyFor(
                    ExperimentConfig.Static,
                    now,
                    MutableStateFlow(london),
                    { MutableStateFlow(emptyList<MeteorShower>()) },
                ) { location ->
                    passesAskedAt = location
                    emptyList<SatellitePass>()
                }

            assertThat(sky).isNotNull()
            assertThat(sky!!.sunset).isNotNull()
            assertThat(passesAskedAt).isEqualTo(london)
        }

    @Test
    fun `tonight widget is null and reads nothing when its flag is off`() =
        runTest {
            val sky =
                tonightSkyFor(
                    onlyMoon,
                    now,
                    untouched(),
                    { error("catalog must not be opened while disabled") },
                ) {
                    error("satellite cache must not be read while disabled")
                }

            assertThat(sky).isNull()
        }

    @Test
    fun `countdown widget with the shipped flags always has a target ahead`() =
        runTest {
            val target =
                countdownFor(ExperimentConfig.Static, now) { MutableStateFlow(emptyList()) }

            // No showers in the catalog still leaves the next moon extreme.
            assertThat(target).isNotNull()
            assertThat(target!!.time > now).isTrue()
        }

    @Test
    fun `countdown widget follows the tonight flag and reads nothing when it is off`() =
        runTest {
            val catalogOpened = { error("catalog must not be opened while disabled") }
            assertThat(countdownFor(onlyMoon, now, catalogOpened)).isNull()
            assertThat(countdownFor(onlyTonight, now) { MutableStateFlow(emptyList()) })
                .isNotNull()
        }
}
