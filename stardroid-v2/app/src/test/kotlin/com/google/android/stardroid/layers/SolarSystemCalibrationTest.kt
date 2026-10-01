/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.ui.map.MapViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Ties [SolarSystemLayer]'s size calibration to the map's opening zoom. The two live in different
 * modules — the layer in `:shared:layers`, the map in `:app` — so the relationship is asserted
 * from this side, as [HorizonEdgeSharpnessTest] does for the horizon ramp.
 */
class SolarSystemCalibrationTest {
    @Test
    fun `the calibration field of view is the one the map opens at`() {
        // The floors are calibrated against a copy of MapViewModel's opening FOV, which a layer
        // must not import. If someone retunes the opening framing, this is what catches it.
        assertThat(SolarSystemLayer.CALIBRATION_FOV_DEG)
            .isWithin(1e-9)
            .of(MapViewModel.INITIAL_FOV_DEG)
    }
}
