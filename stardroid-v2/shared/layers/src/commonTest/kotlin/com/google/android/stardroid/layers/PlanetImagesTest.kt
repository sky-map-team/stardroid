/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class PlanetImagesTest {
    @Test
    fun `bodies map to their named image`() {
        assertThat(PlanetImages.imageFor(SolarSystemBody.JUPITER))
            .isEqualTo(ImageRef("planet/jupiter"))
        assertThat(PlanetImages.imageFor(SolarSystemBody.SUN))
            .isEqualTo(ImageRef("planet/sun"))
        // D88 collapsed v1's moon0-moon7 into a single fully-lit disc: the terminator is painted
        // by the renderer, so the bitmap doesn't depend on the date.
        assertThat(PlanetImages.imageFor(SolarSystemBody.MOON))
            .isEqualTo(ImageRef("planet/moon"))
    }
}
