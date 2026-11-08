/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class LayerRegistryDefaultsTest {
    @Test
    fun satellitesStartOffBecauseTurningThemOnIsTheConsentToFetch() {
        assertThat(LayerRegistry.defaultEnabled(SatelliteLayer.LAYER_ID)).isFalse()
    }

    @Test
    fun theAltAzGridStillStartsOff() {
        assertThat(LayerRegistry.defaultEnabled(AltAzGridLayer.LAYER_ID)).isFalse()
    }

    @Test
    fun otherLayersStartOn() {
        assertThat(LayerRegistry.defaultEnabled(CatalogLayers.STARS_LAYER_ID)).isTrue()
    }
}
