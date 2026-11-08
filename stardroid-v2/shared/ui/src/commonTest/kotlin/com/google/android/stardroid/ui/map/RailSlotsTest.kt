/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class RailSlotsTest {
    // Each item costs a 48 dp slot, and the group a 9 dp divider.
    @Test
    fun fitsOnlyWhatTheHeightAllows() {
        assertEquals(2, railSlotsThatFit(used = 100.dp, maxHeight = 210.dp, count = 5))
        assertEquals(5, railSlotsThatFit(used = 100.dp, maxHeight = 1000.dp, count = 5))
    }

    @Test
    fun noRoomOrNoItemsFitsNothing() {
        assertEquals(0, railSlotsThatFit(used = 100.dp, maxHeight = 150.dp, count = 5))
        assertEquals(0, railSlotsThatFit(used = 0.dp, maxHeight = 1000.dp, count = 0))
    }
}
