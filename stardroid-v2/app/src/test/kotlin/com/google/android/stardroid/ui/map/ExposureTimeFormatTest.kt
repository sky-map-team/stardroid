/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ExposureTimeFormatTest {
    @Test
    fun `shutter readout uses fractions below a quarter second and decimals above`() {
        assertThat(formatExposureTime(33_333_333L)).isEqualTo("1/30s")
        assertThat(formatExposureTime(1_000_000L)).isEqualTo("1/1000s")
        assertThat(formatExposureTime(500_000_000L)).isEqualTo("0.5s")
        assertThat(formatExposureTime(2_000_000_000L)).isEqualTo("2.0s")
    }
}
