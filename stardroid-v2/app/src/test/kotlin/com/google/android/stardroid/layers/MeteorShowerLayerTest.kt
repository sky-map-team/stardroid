/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.PointAppearance
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeteorShowerLayerTest {
    private val catalog = FakeCatalogRepository()
    private val locale = MutableStateFlow(LocaleSpec("en"))
    private val clock = MutableStateFlow(Instant.parse("2026-08-13T02:00:00Z"))

    private fun TestScope.layer() =
        MeteorShowerLayer(
            catalog = catalog,
            locale = locale,
            clock = clock,
            mapContext = UnconfinedTestDispatcher(testScheduler),
        )

    /** v1's Perseids row: Jul 17 – Aug 13 (peak) – Aug 24, ZHR 100. */
    private val perseids =
        MeteorShower(
            id = CelestialObjectId("shower/perseids"),
            name = "Perseids",
            radiant = RaDec(48.0, 58.0),
            activeFrom = MonthDay(7, 17),
            peak = MonthDay(8, 13),
            activeTo = MonthDay(8, 24),
            peakZhr = 100,
        )

    /** v1's Ursids row: peak ZHR 10 never exceeds the icon threshold. */
    private val ursids =
        MeteorShower(
            id = CelestialObjectId("shower/ursids"),
            name = "Ursids",
            radiant = RaDec(217.0, 76.0),
            activeFrom = MonthDay(12, 16),
            peak = MonthDay(12, 23),
            activeTo = MonthDay(12, 26),
            peakZhr = 10,
        )

    private fun date(
        month: Int,
        day: Int,
    ) = LocalDate(2026, month, day)

    // ---- scene building --------------------------------------------------------------

    @Test
    fun `shower outside its window contributes nothing`() =
        runTest {
            val scene = layer().buildScene(listOf(perseids), date(3, 1))

            assertThat(scene.points).isEmpty()
            assertThat(scene.labels).isEmpty()
        }

    @Test
    fun `active shower renders a radiant icon and its name label`() =
        runTest {
            val scene = layer().buildScene(listOf(perseids), date(8, 13))

            val appearance = scene.points.single().appearance as PointAppearance.Icon
            assertThat(appearance.image).isEqualTo(MeteorShowerLayer.ICON_RADIANT_PEAK)
            val label = scene.labels.single()
            assertThat(label.text).isEqualTo("Perseids")
            assertThat(label.style.color).isEqualTo(SkyColors.SKY_LABEL)
        }

    @Test
    fun `icon stays small while the interpolated rate is under the threshold`() =
        runTest {
            // Jul 18: 1 of 27 days to peak, ZHR ~3.7 < 10.
            val scene = layer().buildScene(listOf(perseids), date(7, 18))

            val appearance = scene.points.single().appearance as PointAppearance.Icon
            assertThat(appearance.image).isEqualTo(MeteorShowerLayer.ICON_RADIANT)
        }

    @Test
    fun `low-rate showers never get the peak icon, as in v1`() =
        runTest {
            // Ursids peak at exactly the threshold; v1's strict > keeps the small glyph.
            val scene = layer().buildScene(listOf(ursids), date(12, 23))

            val appearance = scene.points.single().appearance as PointAppearance.Icon
            assertThat(appearance.image).isEqualTo(MeteorShowerLayer.ICON_RADIANT)
        }

    @Test
    fun `rate interpolates linearly up to the peak and back down`() =
        runTest {
            val layer = layer()

            assertThat(layer.zenithalHourlyRate(perseids, date(7, 17))).isEqualTo(0.0)
            assertThat(layer.zenithalHourlyRate(perseids, date(8, 13))).isEqualTo(100.0)
            // Aug 24 is the window's inclusive end: rate has fallen back to 0.
            assertThat(layer.zenithalHourlyRate(perseids, date(8, 24))).isEqualTo(0.0)
            // Halfway down the descent (peak Aug 13 → end Aug 24 is 11 days).
            assertThat(layer.zenithalHourlyRate(perseids, date(8, 19)))
                .isWithin(1e-9)
                .of(100.0 * 5.0 / 11.0)
        }

    @Test
    fun `handles a Feb 29 shower date in a non-leap year without crashing`() =
        runTest {
            val leapShower =
                perseids.copy(
                    activeFrom = MonthDay(2, 25),
                    peak = MonthDay(2, 29),
                    activeTo = MonthDay(3, 4),
                )

            // 2026 is not a leap year; the window spans the (absent) leap day.
            assertThat(layer().zenithalHourlyRate(leapShower, LocalDate(2026, 3, 1)))
                .isGreaterThan(0.0)
        }

    @Test
    fun `rate is identical in leap and non-leap years`() =
        runTest {
            val layer = layer()

            assertThat(layer.zenithalHourlyRate(perseids, LocalDate(2024, 8, 19)))
                .isEqualTo(layer.zenithalHourlyRate(perseids, LocalDate(2026, 8, 19)))
        }

    // ---- flow behavior ---------------------------------------------------------------

    @Test
    fun `re-emits when the utc date changes but not within a date`() =
        runTest {
            catalog.showers.value = listOf(perseids)
            val scenes = collectInBackground(layer().scenes())

            assertThat(scenes).hasSize(1)
            assertThat(scenes.single().points).hasSize(1)

            // Same UTC date, later hour: no recompute.
            clock.value = Instant.parse("2026-08-13T20:00:00Z")
            assertThat(scenes).hasSize(1)

            // Past the window's end: the radiant disappears.
            clock.value = Instant.parse("2026-08-25T02:00:00Z")
            assertThat(scenes).hasSize(2)
            assertThat(scenes.last().points).isEmpty()
        }

    @Test
    fun `re-emits on catalog change`() =
        runTest {
            catalog.showers.value = listOf(perseids)
            val scenes = collectInBackground(layer().scenes())

            catalog.showers.value = listOf(perseids, ursids)

            assertThat(scenes).hasSize(2)
            // Ursids are inactive in August; only the Perseids radiant renders.
            assertThat(scenes.last().points).hasSize(1)
        }

    private fun <T> TestScope.collectInBackground(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { items += it }
        }
        return items
    }
}
