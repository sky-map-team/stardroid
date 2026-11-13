/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.text

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class TextIdTest {
    @Test
    fun `the compass rose has sixteen points from north`() {
        assertThat(TextId.compass(0)).isEqualTo(TextId.COMPASS_N)
        assertThat(TextId.compass(4)).isEqualTo(TextId.COMPASS_E)
        assertThat(TextId.compass(15)).isEqualTo(TextId.COMPASS_NNW)
    }
}
