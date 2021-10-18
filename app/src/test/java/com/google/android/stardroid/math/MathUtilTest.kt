package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MathUtilTest {
    private val TOL = 1e-5f

    @Test
    fun testNorm() {
        val n = norm(3f, 4f, 5f)
        assertThat(n * n).isWithin(TOL).of(3f * 3 + 4 * 4 + 5 * 5)
    }

    @Test
    fun testFlooredMod() {
        assertThat(flooredMod(180f, 360f)).isWithin(TOL).of(180f)
        assertThat(flooredMod(400f, 360f)).isWithin(TOL).of(40f)
        assertThat(flooredMod(-90f, 360f)).isWithin(TOL).of(270f)
    }
}