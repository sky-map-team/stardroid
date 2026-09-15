/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

private const val TOL = 1e-7

/** Ported from v1 `LatLongTest`. */
class LatLongTest {
    @Test
    fun instantiatesCorrectly() {
        assertThat(LatLong(45.0, 50.0).latitudeDeg).isWithin(TOL).of(45.0)
    }

    @Test
    fun normalizesBounds() {
        assertThat(LatLong(95.0, 50.0).latitudeDeg).isWithin(TOL).of(90.0)
        assertThat(LatLong(-105.0, 50.0).latitudeDeg).isWithin(TOL).of(-90.0)
        assertThat(LatLong(45.0, 240.0).longitudeDeg).isWithin(TOL).of(-120.0)
        assertThat(LatLong(45.0, -200.0).longitudeDeg).isWithin(TOL).of(160.0)
        assertThat(LatLong(45.0, 600.0).longitudeDeg).isWithin(TOL).of(-120.0)
        assertThat(LatLong(45.0, -560.0).longitudeDeg).isWithin(TOL).of(160.0)
    }

    @Test
    fun distanceTo() {
        assertThat(LatLong(0.0, 0.0).distanceTo(LatLong(0.0, 90.0))).isWithin(TOL).of(90.0)
        assertThat(LatLong(30.0, 9.0).distanceTo(LatLong(30.0, 9.0))).isWithin(TOL).of(0.0)
        assertThat(LatLong(-90.0, 45.0).distanceTo(LatLong(90.0, 45.0))).isWithin(TOL).of(180.0)
        assertThat(LatLong(0.0, -20.0).distanceTo(LatLong(0.0, 30.0))).isWithin(TOL).of(50.0)
        assertThat(LatLong(-10.0, 0.0).distanceTo(LatLong(40.0, 0.0))).isWithin(1e-4).of(50.0)
    }
}
