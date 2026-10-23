/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.settings

import com.google.android.stardroid.layers.LayerParameter
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AlertSettingsTest {
    private val settings = FakeSettings()

    @Test
    fun `shower alerts and the layer parameter are one value`() =
        runTest {
            settings.setShowerAlertsEnabled(true)
            assertThat(
                settings
                    .layerParameter(
                        MeteorShowerLayer.LAYER_ID,
                        LayerParameter.SHOWER_ALERTS,
                        "false",
                    )
                    .first(),
            ).isEqualTo("true")

            settings.setLayerParameter(
                MeteorShowerLayer.LAYER_ID,
                LayerParameter.SHOWER_ALERTS,
                "false",
            )
            assertThat(settings.showerAlertsEnabled.first()).isFalse()
        }

    @Test
    fun `pass alerts read off while the satellites layer is off`() =
        runTest {
            settings.setLayerEnabled(SatelliteLayer.LAYER_ID, false)
            settings.setLayerParameter(SatelliteLayer.LAYER_ID, LayerParameter.PASS_ALERTS, "true")
            assertThat(settings.passAlertsEnabled.first()).isFalse()
        }

    @Test
    fun `turning pass alerts on also turns the satellites layer on`() =
        runTest {
            settings.setLayerEnabled(SatelliteLayer.LAYER_ID, false)
            settings.setPassAlertsEnabled(true)
            assertThat(settings.layerEnabled(SatelliteLayer.LAYER_ID, false).first()).isTrue()
            assertThat(settings.passAlertsEnabled.first()).isTrue()
        }
}
