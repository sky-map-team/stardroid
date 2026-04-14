package com.google.android.stardroid.layers

import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test
import java.lang.IllegalArgumentException
import java.lang.RuntimeException

const val TOL = 0.0001f

class CometsLayerTest {
  @Test
  fun testInterpolator_oneSegment() {
    val interpolator = CometsLayer.Interpolator(listOf(0, 10), listOf(0f, 2f))
    assertThat(interpolator.interpolate(0)).isWithin(TOL).of(0f)
    assertThat(interpolator.interpolate(1)).isWithin(TOL).of(0.2f)
    assertThat(interpolator.interpolate(5)).isWithin(TOL).of(1.0f)
    assertThat(interpolator.interpolate(8)).isWithin(TOL).of(1.6f)
  }

  @Test
  fun testInterpolator_twoSegments() {
    val interpolator = CometsLayer.Interpolator(listOf(0, 10, 15), listOf(0f, 2f, 4f))
    assertThat(interpolator.interpolate(10)).isWithin(TOL).of(2f)
    assertThat(interpolator.interpolate(15)).isWithin(TOL).of(4f)
    assertThat(interpolator.interpolate(11)).isWithin(TOL).of(2.4f)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testInterpolator_throwsOutOfRange() {
    CometsLayer.Interpolator(listOf(0, 2, 5), listOf(0f, 2f, 4f)).interpolate(-1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testInterpolator_tooFew() {
    CometsLayer.Interpolator(listOf(0), listOf(0f))
  }

  @Test(expected = IllegalArgumentException::class)
  fun testInterpolator_mismathedsizes() {
    CometsLayer.Interpolator(listOf(0, 1, 2), listOf(0f, 2f))
  }
}