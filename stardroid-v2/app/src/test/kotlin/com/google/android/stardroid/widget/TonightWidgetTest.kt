/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** [showDarkTime]: the Tonight header drops the darkness time when it would collide. */
class TonightWidgetTest {
    @Test
    fun `wide widget shows the darkness time`() {
        assertThat(showDarkTime(widthDp = 340f, fontScale = 1f)).isTrue()
    }

    @Test
    fun `narrow widget shows sunset only`() {
        assertThat(showDarkTime(widthDp = 260f, fontScale = 1f)).isFalse()
    }

    @Test
    fun `the darkness time appears at the 340dp ladder step, not below`() {
        assertThat(showDarkTime(widthDp = 320f, fontScale = 1f)).isFalse()
        assertThat(showDarkTime(widthDp = 340f, fontScale = 1f)).isTrue()
    }

    @Test
    fun `larger font scale needs more width`() {
        assertThat(showDarkTime(widthDp = 340f, fontScale = 1.3f)).isFalse()
    }
}
