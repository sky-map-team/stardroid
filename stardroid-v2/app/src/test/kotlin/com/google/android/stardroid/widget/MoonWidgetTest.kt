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

    @Test
    fun `roomy widget keeps the age line and a full-size disc`() {
        val plan = fullLayoutPlan(heightDp = 190f, fontScale = 1f, timeRows = 2)
        assertThat(plan.showAge).isTrue()
        assertThat(plan.discDp).isEqualTo(56)
    }

    @Test
    fun `tight widget drops the age line before the rise and set rows`() {
        val plan = fullLayoutPlan(heightDp = 130f, fontScale = 1f, timeRows = 2)
        assertThat(plan.showAge).isFalse()
        assertThat(plan.discDp).isAtLeast(32)
    }

    @Test
    fun `larger font scale needs more height for the same content`() {
        val normal = fullLayoutPlan(heightDp = 150f, fontScale = 1f, timeRows = 2)
        val large = fullLayoutPlan(heightDp = 150f, fontScale = 1.5f, timeRows = 2)
        assertThat(large.discDp).isAtMost(normal.discDp)
        assertThat(large.showAge).isFalse()
    }

    @Test
    fun `the age line flips at the exact height where a minimum disc fits`() {
        assertThat(fullLayoutPlan(heightDp = 134f, fontScale = 1f, timeRows = 2).showAge).isTrue()
        assertThat(fullLayoutPlan(heightDp = 133f, fontScale = 1f, timeRows = 2).showAge).isFalse()
    }

    @Test
    fun `no time rows leaves more room and no gap`() {
        assertThat(fullLayoutPlan(heightDp = 190f, fontScale = 1f, timeRows = 0).discDp)
            .isEqualTo(56)
        assertThat(fullLayoutPlan(heightDp = 110f, fontScale = 1f, timeRows = 1).showAge).isFalse()
    }

    @Test
    fun `the disc never shrinks below its minimum`() {
        val plan = fullLayoutPlan(heightDp = 110f, fontScale = 2f, timeRows = 2)
        assertThat(plan.discDp).isEqualTo(32)
    }
}
