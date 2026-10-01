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
import com.google.android.stardroid.render.api.LayerScene
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
class HorizonLayerTest {
    private val time = Instant.parse("2026-07-03T22:00:00Z")
    private val greenwich = LatLong(51.48, 0.0)

    private val clock = MutableStateFlow(time)
    private val location = MutableStateFlow(greenwich)
    private val strings = MutableStateFlow<LayerStrings>(FakeLayerStrings())

    private fun TestScope.layer() =
        HorizonLayer(
            clock,
            location,
            strings,
            mapContext = UnconfinedTestDispatcher(testScheduler),
        )

    @Test
    fun `scene matches the local frame for the time and place`() =
        runTest {
            val scene = layer().buildScene(time, greenwich, FakeLayerStrings())
            val frame = SkyModel.localFrame(time, greenwich)

            assertThat(scene.depth).isEqualTo(90)
            val horizon = scene.lines.single()
            // N → E → S → W, closed back at north.
            assertThat(horizon.vertices)
                .containsExactly(
                    frame.trueNorth,
                    frame.trueEast,
                    -frame.trueNorth,
                    -frame.trueEast,
                    frame.trueNorth,
                )
                .inOrder()
            // The horizon reads as one green element (D40): line and labels.
            assertThat(horizon.color).isEqualTo(SkyColors.HORIZON_LINE)
            assertThat(horizon.widthDp).isEqualTo(2.5)

            val byText = scene.labels.associateBy { it.text }
            // Zenith/nadir moved to AltAzGridLayer (#1022): this layer only labels the compass
            // points now.
            assertThat(byText.keys).containsExactly("NORTH", "SOUTH", "EAST", "WEST")
            assertThat(byText.getValue("NORTH").pos).isEqualTo(frame.trueNorth)
            assertThat(byText.getValue("NORTH").style.color).isEqualTo(SkyColors.HORIZON_LABEL)
        }

    @Test
    fun `the layer submits reference geometry only, with no shading of its own`() =
        runTest {
            // The glow that used to hang below the line is now `Ground`, a render-state block the
            // backends shade for themselves. What is left here is the horizon's *position*: a line
            // and four letters. If a gradient ever reappears in this layer, something has gone
            // back to faking shading with geometry.
            val scene = layer().buildScene(time, greenwich, FakeLayerStrings())
            assertThat(scene.lines).hasSize(1)
            assertThat(scene.labels).hasSize(4)
            assertThat(scene.points).isEmpty()
            assertThat(scene.images).isEmpty()
        }

    @Test
    fun `the horizon draws in front of the ground, so its line stays legible on top of it`() =
        runTest {
            // The ground is a translucent wash drawn at GROUND_DEPTH, which only works if the
            // horizon furniture comes after it. Nothing about either integer says so on its own,
            // and a layer added deeper than the ground would silently be washed over with no error
            // anywhere, so the invariant is asserted rather than commented.
            assertThat(layer().depth).isGreaterThan(LayerScene.GROUND_DEPTH)
        }

    @Test
    fun `re-emits on clock tick and location change but not on identical recomputes`() =
        runTest {
            val scenes = collectInBackground(layer().scenes())
            assertThat(scenes).hasSize(1)

            // Same instant re-emitted: the scene is identical, distinctUntilChanged drops it.
            strings.value = FakeLayerStrings()
            assertThat(scenes).hasSize(1)

            // The Earth turns: the horizon moves through celestial coordinates.
            clock.value = Instant.parse("2026-07-03T22:10:00Z")
            assertThat(scenes).hasSize(2)

            location.value = LatLong(-33.9, 18.4)
            assertThat(scenes).hasSize(3)
        }

    private fun <T> TestScope.collectInBackground(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { items += it }
        }
        return items
    }
}
