package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Created by johntaylor on 2/15/16.
 */
class LatLongTest {
    private val TOL = 1e-5f
    private val LOWER_TOL = 1e-4f

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

    @Test
    fun testDistanceFrom90Degrees() {
        val l1 = LatLong(0f, 0f)
        val l2 = LatLong(0f, 90f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(90f)
    }

    @Test
    fun testDistanceFromSame() {
        val l1 = LatLong(30f, 9f)
        val l2 = LatLong(30f, 9f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(0f)
    }

    @Test
    fun testDistanceFromOppositePoles() {
        val l1 = LatLong(-90f, 45f)
        val l2 = LatLong(90f, 45f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(180f)
    }

    @Test
    fun testDistanceFromOnEquator() {
        val l1 = LatLong(0f, -20f)
        val l2 = LatLong(0f, 30f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(50f)
    }

    @Test
    fun testDistanceFromOnMeridian() {
        val l1 = LatLong(-10f, 0f)
        val l2 = LatLong(40f, 0f)
        assertThat(l1.distanceFrom(l2)).isWithin(LOWER_TOL).of(50f)
    }
}