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
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
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
import kotlin.math.sin

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
            // The horizon reads as one green element (D40): line, glow, and labels.
            assertThat(horizon.color).isEqualTo(SkyColors.HORIZON_LINE)
            assertThat(horizon.widthDp).isEqualTo(2.5)

            val byText = scene.labels.associateBy { it.text }
            assertThat(byText.keys)
                .containsExactly("ZENITH", "NADIR", "NORTH", "SOUTH", "EAST", "WEST")
            assertThat(byText.getValue("ZENITH").pos).isEqualTo(frame.up)
            assertThat(byText.getValue("NADIR").pos).isEqualTo(-frame.up)
            assertThat(byText.getValue("NORTH").pos).isEqualTo(frame.trueNorth)
            assertThat(byText.getValue("NORTH").style.color).isEqualTo(SkyColors.HORIZON_LABEL)
        }

    @Test
    fun `glow mesh hangs below the horizon with an exponentially fading alpha`() =
        runTest {
            val scene = layer().buildScene(time, greenwich, FakeLayerStrings())
            val frame = SkyModel.localFrame(time, greenwich)
            val nadir = -frame.up

            val glow = scene.glows.single()
            // 9 rings of 181 vertices: ring 0 on the horizon, 8 more tilted toward the nadir.
            assertThat(glow.rings).hasSize(9)
            for (ring in glow.rings) {
                assertThat(ring.vertices).hasSize(181)
                // Closed loop.
                assertThat(ring.vertices.first()).isEqualTo(ring.vertices.last())
            }

            // Ring 0 starts exactly at the horizon (its first vertex is true north)...
            assertThat(glow.rings[0].vertices[0]).isEqualTo(frame.trueNorth)
            // ...and each further ring dips 1° deeper toward the nadir.
            for ((i, ring) in glow.rings.withIndex()) {
                val towardNadir = ring.vertices[0] dot nadir
                assertThat(towardNadir).isWithin(1e-9).of(sin(i * 1.0 * DEGREES_TO_RADIANS))
            }

            // Alpha decays exponentially from the peak and the deepest ring is transparent, so
            // the additive gradient fades out instead of ending in a hard edge.
            val alphas = glow.rings.map { it.color.a }
            assertThat(alphas.first()).isWithin(1e-6f).of(0.7f)
            assertThat(alphas.last()).isEqualTo(0f)
            for (i in 1 until alphas.size) {
                assertThat(alphas[i]).isLessThan(alphas[i - 1])
            }
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
