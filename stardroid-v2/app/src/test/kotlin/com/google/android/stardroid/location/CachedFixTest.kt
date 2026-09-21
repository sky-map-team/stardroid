/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import com.google.android.stardroid.math.LatLong
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CachedFixTest {
    private val london = LatLong(51.5, -0.12)
    private val paris = LatLong(48.85, 2.35)

    @Test
    fun `no candidates gives no seed`() {
        assertThat(freshestFix(emptyList())).isNull()
    }

    @Test
    fun `the freshest fix wins regardless of order`() {
        val old = CachedFix(london, accuracyM = 10f, ageMillis = 600_000)
        val recent = CachedFix(paris, accuracyM = 3_000f, ageMillis = 5_000)
        assertThat(freshestFix(listOf(old, recent))).isEqualTo(recent)
        assertThat(freshestFix(listOf(recent, old))).isEqualTo(recent)
    }

    @Test
    fun `equally fresh fixes prefer the more accurate one`() {
        val coarse = CachedFix(london, accuracyM = 2_000f, ageMillis = 5_000)
        val precise = CachedFix(paris, accuracyM = 20f, ageMillis = 5_000)
        assertThat(freshestFix(listOf(coarse, precise))).isEqualTo(precise)
    }

    @Test
    fun `an unknown accuracy loses a tie to a known one`() {
        val unknown = CachedFix(london, accuracyM = null, ageMillis = 5_000)
        val known = CachedFix(paris, accuracyM = 500f, ageMillis = 5_000)
        assertThat(freshestFix(listOf(unknown, known))).isEqualTo(known)
    }

    @Test
    fun `a very stale fix is still used when it is all there is`() {
        val ancient = CachedFix(london, accuracyM = null, ageMillis = 90L * 24 * 3_600_000)
        assertThat(freshestFix(listOf(ancient))).isEqualTo(ancient)
    }
}
