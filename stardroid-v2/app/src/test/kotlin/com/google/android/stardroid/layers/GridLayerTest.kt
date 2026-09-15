/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GridLayerTest {
    private val strings = MutableStateFlow<LayerStrings>(FakeLayerStrings())

    private fun TestScope.layer() =
        GridLayer(strings, mapContext = UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `scene carries the v1 graticule geometry`() =
        runTest {
            val scene = layer().buildScene(FakeLayerStrings())

            // 24 meridians + the equator + 8 circles each side of it.
            assertThat(scene.lines).hasSize(24 + 1 + 16)
            assertThat(scene.depth).isEqualTo(0)

            val meridians = scene.lines.filter { it.vertices.size == 3 }
            val circles = scene.lines.filter { it.vertices.size == 37 }
            assertThat(meridians).hasSize(24)
            assertThat(circles).hasSize(17)
            // Declination circles close on their starting vertex.
            for (circle in circles) {
                assertThat(circle.vertices.first()).isEqualTo(circle.vertices.last())
            }
        }

    @Test
    fun `labels cover poles, hours, and declinations`() =
        runTest {
            val scene = layer().buildScene(FakeLayerStrings())

            val texts = scene.labels.map { it.text }
            // 2 poles + 12 hour markers + 8 declinations each side.
            assertThat(scene.labels).hasSize(2 + 12 + 16)
            // "0", not "0h": RA 0h is also the ecliptic's vernal equinox, and the single shared
            // label serves both layers (upstream #923).
            assertThat(texts).containsAtLeast("NP", "SP", "0", "22h", "10°", "-80°")
            assertThat(texts).doesNotContain("0h")

            val northPole = scene.labels.first { it.text == "NP" }
            assertThat(northPole.pos).isEqualTo(RaDec(0.0, 90.0).toGeocentricVector())
        }

    @Test
    fun `re-emits only on locale change`() =
        runTest {
            val scenes = collectInBackground(layer().scenes())
            assertThat(scenes).hasSize(1)

            // The same strings instance again: no re-emission.
            strings.value = strings.value
            assertThat(scenes).hasSize(1)

            strings.value = FakeLayerStrings(" [de]")
            assertThat(scenes).hasSize(2)
            assertThat(scenes.last().labels.map { it.text }).contains("NP [de]")
        }

    private fun <T> TestScope.collectInBackground(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { items += it }
        }
        return items
    }
}
