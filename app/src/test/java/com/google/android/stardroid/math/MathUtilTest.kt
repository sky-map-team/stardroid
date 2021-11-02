package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val TOL = 1e-5f

class MathUtilTest {

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

    @Test
    fun testMod2pi() {
        assertThat(mod2pi(PI / 2)).isWithin(TOL).of(PI / 2)
        assertThat(mod2pi(7 * PI / 3)).isWithin(TOL).of(PI / 3)
        assertThat(mod2pi(-PI / 4)).isWithin(TOL).of(1.75f * PI)
    }
}