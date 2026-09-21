/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PlatformAvailabilityTest {
    private fun available(
        network: Boolean = false,
        gps: Boolean = false,
        fine: Boolean = false,
        cached: Boolean = false,
    ) = canProvideLocation(network, gps, fine) { cached }

    @Test
    fun `an enabled network provider is enough`() {
        assertThat(available(network = true)).isTrue()
    }

    @Test
    fun `gps alone is unusable without the fine permission`() {
        assertThat(available(gps = true, fine = false)).isFalse()
    }

    @Test
    fun `gps counts once the fine permission is held`() {
        assertThat(available(gps = true, fine = true)).isTrue()
    }

    @Test
    fun `a cached fix keeps an otherwise dead device available`() {
        assertThat(available(gps = true, cached = true)).isTrue()
    }

    @Test
    fun `nothing enabled and nothing cached is unavailable`() {
        assertThat(available()).isFalse()
    }

    @Test
    fun `the cache is only consulted when no live provider works`() {
        var consulted = false
        canProvideLocation(networkEnabled = true, gpsEnabled = false, fineLocationGranted = false) {
            consulted = true
            true
        }
        assertThat(consulted).isFalse()
    }
}
