/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.settings.FakeSettings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * Guards the one ordering invariant the ground depends on and that nothing else states.
 *
 * The ground is drawn part-way through the layer order, at [LayerScene.GROUND_DEPTH], so that it
 * washes over everything it should obscure while the horizon's line and cardinal labels stay
 * legible on top. That only works if the horizon layer is the **deepest** layer — which was true
 * by accident, because 90 happened to be the largest number anyone had picked. A layer added
 * between the ground and the horizon would silently draw un-washed, with no error anywhere and
 * nothing to notice but a screenshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LayerDepthOrderTest {
    private val registry =
        LayerRegistry.create(
            catalog = FakeCatalogRepository(),
            locale = flowOf(LocaleSpec("en")),
            strings = flowOf(FakeLayerStrings()),
            clock = MutableStateFlow(Instant.parse("2026-08-14T22:00:00Z")),
            location = MutableStateFlow(LatLong(51.5, -0.13)),
            ephemeris = MeeusEphemeris,
            settings = FakeSettings(),
            satelliteElements =
                flowOf(SatelliteElements(emptyList(), ElementFreshness.ABSENT, null, null)),
            satellitesEnabled = false,
        )

    @Test
    fun `the horizon is the only layer drawn after the ground`() {
        val afterGround =
            registry.layers.filter { it.depth >= LayerScene.GROUND_DEPTH }.map { it.id }
        assertThat(afterGround).containsExactly(HorizonLayer.LAYER_ID)
    }

    @Test
    fun `the horizon is the deepest layer, strictly`() {
        val deepest = registry.layers.maxOf { it.depth }
        val horizon = registry.layers.single { it.id == HorizonLayer.LAYER_ID }.depth
        assertThat(horizon).isEqualTo(deepest)
        // Strictly: a tie would make the draw order between them depend on the sort's stability.
        assertThat(registry.layers.count { it.depth == deepest }).isEqualTo(1)
    }

    @Test
    fun `every other layer is drawn before the ground, so the ground washes over it`() {
        val others = registry.layers.filter { it.id != HorizonLayer.LAYER_ID }
        assertThat(others).isNotEmpty()
        for (layer in others) {
            assertThat(layer.depth).isLessThan(LayerScene.GROUND_DEPTH)
        }
    }

    @Test
    fun `the ground sits strictly between the ordinary layers and the horizon`() {
        // Pins the constant itself rather than only its relationship to today's layers: if
        // GROUND_DEPTH were moved past the horizon, the tests above would still pass while the
        // ground covered the horizon line.
        val horizon = registry.layers.single { it.id == HorizonLayer.LAYER_ID }.depth
        val deepestOther =
            registry.layers.filter { it.id != HorizonLayer.LAYER_ID }.maxOf { it.depth }
        assertThat(LayerScene.GROUND_DEPTH).isGreaterThan(deepestOther)
        assertThat(LayerScene.GROUND_DEPTH).isAtMost(horizon)
    }
}
