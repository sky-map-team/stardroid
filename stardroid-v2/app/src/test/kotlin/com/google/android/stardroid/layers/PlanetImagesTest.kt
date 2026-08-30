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
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class PlanetImagesTest {
    private val images = PlanetImages()

    @Test
    fun `planets map to their named image`() {
        val time = Instant.parse("2026-07-03T22:00:00Z")

        assertThat(images.imageFor(SolarSystemBody.JUPITER, time))
            .isEqualTo(ImageRef("planet/jupiter"))
        assertThat(images.imageFor(SolarSystemBody.SUN, time))
            .isEqualTo(ImageRef("planet/sun"))
        assertThat(images.imageFor(SolarSystemBody.EARTH, time)).isNull()
    }

    @Test
    fun `the moon has one image at every phase`() {
        // D88 collapsed v1's moon0-moon7 into a single fully-lit disc: the terminator is painted
        // by the renderer, so the bitmap no longer depends on the date at all.
        val fullMoon = Instant.parse("2024-01-25T17:54:00Z")
        val newMoon = Instant.parse("2024-02-09T22:59:00Z")

        assertThat(images.imageFor(SolarSystemBody.MOON, fullMoon))
            .isEqualTo(ImageRef("planet/moon"))
        assertThat(images.imageFor(SolarSystemBody.MOON, newMoon))
            .isEqualTo(ImageRef("planet/moon"))
    }
}
