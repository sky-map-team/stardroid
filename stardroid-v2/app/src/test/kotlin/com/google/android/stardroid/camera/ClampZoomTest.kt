/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.camera

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClampZoomTest {
    @Test
    fun `clamps to the session range`() {
        assertThat(clampZoom(8f, 1f, 5f)).isEqualTo(5f)
        assertThat(clampZoom(0.5f, 1f, 5f)).isEqualTo(1f)
        assertThat(clampZoom(2f, 1f, 5f)).isEqualTo(2f)
    }

    @Test
    fun `leaves the request alone while the range is unknown`() {
        assertThat(clampZoom(3f, null, null)).isEqualTo(3f)
    }
}
