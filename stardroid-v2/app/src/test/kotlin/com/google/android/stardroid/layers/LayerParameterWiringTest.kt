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
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * The path a parameter actually travels: stored preference → [LayerRegistry] → the layer → the
 * primitive it submits.
 *
 * This exists because that seam broke silently. `LayerRegistry.create` took the disc-size flow
 * with a default so existing callers kept compiling, and the one real caller was never updated —
 * so the sheet wrote the user's choice to DataStore and the layer read a constant. Everything
 * compiled, every unit test passed, and the setting did nothing. Tests that hand the mode
 * straight to the layer cannot see that; this one starts from [Settings] instead.
 *
 * D91 has since closed that hole structurally — the registry takes `Settings` and the layer
 * reads its own parameter — so this test now guards the shape rather than a live bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LayerParameterWiringTest {
    private val time = Instant.parse("2026-08-14T22:00:00Z")
    private val london = LatLong(51.5, -0.13)

    private fun registryOver(settings: FakeSettings) =
        LayerRegistry.create(
            catalog = FakeCatalogRepository(),
            locale = flowOf(LocaleSpec("en")),
            strings = flowOf(FakeLayerStrings()),
            clock = MutableStateFlow(time),
            location = MutableStateFlow(london),
            ephemeris = MeeusEphemeris,
            settings = settings,
            satelliteElements =
                flowOf(
                    SatelliteElements(emptyList(), ElementFreshness.ABSENT, null, null),
                ),
            satellitesEnabled = false,
        )

    private suspend fun jupiterSizeFrom(settings: FakeSettings): Double {
        val layer =
            registryOver(settings).layers.single { it.id == SolarSystemLayer.LAYER_ID }
        return layer
            .scenes()
            .first()
            .images
            .single { it.image == ImageRef("planet/jupiter") }
            .angularSizeDeg
    }

    @Test
    fun `a stored choice reaches the primitives the layer submits`() =
        runTest {
            val settings = FakeSettings()
            settings.setLayerParameter(
                SolarSystemLayer.LAYER_ID,
                LayerParameter.DISC_SIZE,
                LayerParameter.DISC_SIZE_TRUE,
            )

            // True scale: Jupiter's real disc, tens of arcseconds.
            assertThat(jupiterSizeFrom(settings)).isLessThan(0.02)

            settings.setLayerParameter(
                SolarSystemLayer.LAYER_ID,
                LayerParameter.DISC_SIZE,
                LayerParameter.DISC_SIZE_GLYPHS,
            )
            // Glyphs: the compressed physical size, hundreds of times larger than the truth.
            assertThat(jupiterSizeFrom(settings)).isWithin(0.1).of(2.05)
        }

    @Test
    fun `with nothing stored the layer gets the declared default`() =
        runTest {
            val settings = FakeSettings()
            val size = jupiterSizeFrom(settings)
            val expected =
                when (LayerParameter.DISC_SIZE_PARAMETER.defaultOption) {
                    LayerParameter.DISC_SIZE_GLYPHS -> 2.05
                    else -> 0.0087
                }
            assertThat(size).isWithin(expected * 0.1).of(expected)
        }

    @Test
    fun `the layer declares the parameter the sheet renders`() {
        val layer =
            registryOver(FakeSettings()).layers.single { it.id == SolarSystemLayer.LAYER_ID }
        assertThat(layer.parameters).containsExactly(
            LayerParameter.DISC_SIZE_PARAMETER,
            LayerParameter.ECLIPSE_ALERTS_PARAMETER,
        )
        // D91: the sheet's static list and the live instance's are one declaration, so a
        // parameter cannot exist for the UI without existing for the layer that serves it.
        // containsAtLeast, not containsExactly: other layers declare parameters too now (the
        // satellite layer's pass-alert toggle). What D91 requires is that this layer's static and
        // live declarations are the same objects, not that it is the only layer with any.
        assertThat(LayerRegistry.PARAMETERS)
            .containsAtLeastElementsIn(layer.parameters.map { SolarSystemLayer.LAYER_ID to it })
        // Every option the sheet can offer is one the layer will accept.
        assertThat(LayerParameter.DISC_SIZE_PARAMETER.options)
            .containsExactly(
                LayerParameter.DISC_SIZE_TRUE,
                LayerParameter.DISC_SIZE_GLYPHS,
                LayerParameter.DISC_SIZE_AUTO,
            )
    }
}
