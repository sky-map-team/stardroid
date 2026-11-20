/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MoonWidgetTest {
    @Test
    fun `1x1 and 2x1 footprints use the compact layout`() {
        assertThat(moonWidgetLayoutFor(MoonWidget.COMPACT_SIZE)).isEqualTo(MoonWidgetLayout.COMPACT)
        assertThat(moonWidgetLayoutFor(DpSize(250.dp, 57.dp))).isEqualTo(MoonWidgetLayout.COMPACT)
        assertThat(moonWidgetLayoutFor(DpSize(57.dp, 250.dp))).isEqualTo(MoonWidgetLayout.COMPACT)
    }

    @Test
    fun `2x2 and larger use the full layout`() {
        assertThat(moonWidgetLayoutFor(MoonWidget.FULL_SIZE)).isEqualTo(MoonWidgetLayout.FULL)
        assertThat(moonWidgetLayoutFor(DpSize(250.dp, 250.dp))).isEqualTo(MoonWidgetLayout.FULL)
    }
}
