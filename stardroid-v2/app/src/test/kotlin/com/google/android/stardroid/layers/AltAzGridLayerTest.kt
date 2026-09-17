/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.math.LatLong
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AltAzGridLayerTest {
    private val time = Instant.parse("2026-07-03T22:00:00Z")
    private val greenwich = LatLong(51.48, 0.0)

    private val clock = MutableStateFlow(time)
    private val location = MutableStateFlow(greenwich)
    private val strings = MutableStateFlow<LayerStrings>(FakeLayerStrings())
    private val density =
        MutableStateFlow(LayerParameter.ALTAZ_GRID_DENSITY_PARAMETER.defaultOption)

    private fun TestScope.layer() =
        AltAzGridLayer(
            clock,
            location,
            strings,
            density,
            mapContext = UnconfinedTestDispatcher(testScheduler),
        )

    @Test
    fun `scene carries azimuth meridians and altitude circles at the default density`() =
        runTest {
            val scene = layer().buildScene(time, greenwich, FakeLayerStrings(), "12")

            // 12 azimuth lines + 5 altitude circles each side of the horizon (15° steps at the
            // medium density).
            assertThat(scene.lines).hasSize(12 + 10)
            assertThat(scene.depth).isEqualTo(80)

            val meridians = scene.lines.filter { it.vertices.size == 3 }
            // 60 vertices per circle at the medium density, +1 to close the loop.
            val circles = scene.lines.filter { it.vertices.size == 61 }
            assertThat(meridians).hasSize(12)
            assertThat(circles).hasSize(10)
            for (circle in circles) {
                assertThat(circle.vertices.first()).isEqualTo(circle.vertices.last())
            }

            val frame = SkyModel.localFrame(time, greenwich)
            for (meridian in meridians) {
                assertThat(meridian.vertices.first()).isEqualTo(frame.up)
                assertThat(meridian.vertices.last()).isEqualTo(-frame.up)
            }
        }

    @Test
    fun `labels skip the cardinal directions and carry zenith and nadir`() =
        runTest {
            val scene = layer().buildScene(time, greenwich, FakeLayerStrings(), "12")
            val frame = SkyModel.localFrame(time, greenwich)

            val texts = scene.labels.map { it.text }
            // 12 azimuth lines minus the 4 that fall on N/E/S/W (already labeled by
            // HorizonLayer) + 5 altitude labels each side of the horizon (15° steps) + zenith
            // + nadir.
            assertThat(scene.labels).hasSize((12 - 4) + 10 + 2)
            assertThat(texts).doesNotContain("0°")
            assertThat(texts).doesNotContain("90°")
            assertThat(texts).doesNotContain("180°")
            assertThat(texts).doesNotContain("270°")
            assertThat(texts).containsAtLeast("30°", "15°", "-75°", "ZENITH", "NADIR")

            val byText = scene.labels.associateBy { it.text }
            assertThat(byText.getValue("ZENITH").pos).isEqualTo(frame.up)
            assertThat(byText.getValue("NADIR").pos).isEqualTo(-frame.up)
        }

    @Test
    fun `meridian count, altitude circle count, and circle sampling all follow density`() =
        runTest {
            val coarse = layer().buildScene(time, greenwich, FakeLayerStrings(), "8")
            val fine = layer().buildScene(time, greenwich, FakeLayerStrings(), "24")

            // Azimuth meridians: 8 vs 24.
            assertThat(coarse.lines.count { it.vertices.size == 3 }).isEqualTo(8)
            assertThat(fine.lines.count { it.vertices.size == 3 }).isEqualTo(24)

            // Altitude circles (#1022 review): a fixed count regardless of density left this
            // half of the "line count" setting doing nothing visible. Coarse steps every 30°
            // (2 circles/side); fine steps every 10° (8 circles/side).
            assertThat(coarse.lines.count { it.vertices.size != 3 }).isEqualTo(4)
            assertThat(fine.lines.count { it.vertices.size != 3 }).isEqualTo(16)

            // Circle sampling scales too: a fixed vertex count left the near-horizon circles
            // looking faceted regardless of the chosen density.
            val coarseCircle = coarse.lines.first { it.vertices.size != 3 }
            val fineCircle = fine.lines.first { it.vertices.size != 3 }
            assertThat(coarseCircle.vertices).hasSize(37)
            assertThat(fineCircle.vertices).hasSize(91)
        }

    @Test
    fun `re-emits on clock, location, and density change but not on identical recomputes`() =
        runTest {
            val scenes = collectInBackground(layer().scenes())
            assertThat(scenes).hasSize(1)

            strings.value = strings.value
            assertThat(scenes).hasSize(1)

            clock.value = Instant.parse("2026-07-03T22:10:00Z")
            assertThat(scenes).hasSize(2)

            location.value = LatLong(-33.9, 18.4)
            assertThat(scenes).hasSize(3)

            density.value = LayerParameter.ALTAZ_GRID_DENSITY_FINE
            assertThat(scenes).hasSize(4)
        }

    private fun <T> TestScope.collectInBackground(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { items += it }
        }
        return items
    }
}
