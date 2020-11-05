package com.google.android.stardroid.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that require roboelectric for API calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LatLongRoboTest {
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

    companion object {
        private const val TOL = 1e-5f
    }
}