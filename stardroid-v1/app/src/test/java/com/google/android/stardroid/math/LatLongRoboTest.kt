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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that require roboelectric for API calls.
 */
// TODO(jontayler): what was the point of this test exactly?
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LatLongRoboTest {
    private val TOL = 1e-5f

    @Test
    fun latLong_testDistance() {
        val point1 = LatLong(0.0, 0.0)
        val point2 = LatLong(90.0, 0.0)
        assertThat(point1.distanceFrom(point2)).isWithin(TOL).of(90f)
    }

    @Test
    fun latLong_testDistance2() {
        val point1 = LatLong(45.0, 45.0)
        val point2 = LatLong(90.0, 0.0)
        assertThat(point1.distanceFrom(point2)).isWithin(TOL).of(45f)
    }
}