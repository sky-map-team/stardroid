package com.google.android.stardroid.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Created by johntaylor on 2/15/16.
 */
class LatLongTest {
    @Test
    fun latLong_testInstantiatesCorrectly() {
        val position = LatLong(45f, 50f)
        assertThat(position.latitude).isWithin(TOL).of(45f)
    }

    @Test
    fun latLong_testBoundsCorrectly() {
        var position = LatLong(95f, 50f)
        assertThat(position.latitude).isWithin(TOL).of(90f)
        position = LatLong(-105f, 50f)
        assertThat(position.latitude).isWithin(TOL).of(-90f)
        position = LatLong(45f, 240f)
        assertThat(position.longitude).isWithin(TOL).of(-120f)
        position = LatLong(45f, -200f)
        assertThat(position.longitude).isWithin(TOL).of(160f)
        position = LatLong(45f, 600f)
        assertThat(position.longitude).isWithin(TOL).of(-120f)
        position = LatLong(45f, -560f)
        assertThat(position.longitude).isWithin(TOL).of(160f)
    }

    companion object {
        private const val TOL = 1e-5f
    }
}