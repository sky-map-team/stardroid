/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CropRegionTest {
    @Test
    fun `wide source crops its sides to a portrait window`() {
        // A 4:3 still (3000×4000 portrait-rotated is 3:4; use landscape 4000×3000) cropped
        // for a 9:19.5 portrait screen keeps full height and trims the sides.
        val crop = centerCrop(4000, 3000, 1080.0 / 2424.0)
        assertThat(crop.height).isEqualTo(3000)
        assertThat(crop.width).isEqualTo(1337)
        assertThat(crop.left).isEqualTo((4000 - 1337) / 2)
        assertThat(crop.top).isEqualTo(0)
    }

    @Test
    fun `tall source crops top and bottom to a wide window`() {
        val crop = centerCrop(3000, 4000, 2.0)
        assertThat(crop.width).isEqualTo(3000)
        assertThat(crop.height).isEqualTo(1500)
        assertThat(crop.left).isEqualTo(0)
        assertThat(crop.top).isEqualTo((4000 - 1500) / 2)
    }

    @Test
    fun `matching aspect crops nothing`() {
        val crop = centerCrop(1080, 2424, 1080.0 / 2424.0)
        assertThat(crop).isEqualTo(CropRegion(0, 0, 1080, 2424))
    }
}
