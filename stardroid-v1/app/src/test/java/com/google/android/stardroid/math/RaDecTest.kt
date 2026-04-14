package com.google.android.stardroid.math

import com.google.android.stardroid.math.RaDec.Companion.decDegreesFromDMS
import com.google.android.stardroid.math.RaDec.Companion.raDegreesFromHMS
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RaDecTest {
    private val EPS = 1e-5f

    @Test
    fun testRaFromHMS() {
        assertThat(raDegreesFromHMS(0f, 0f, 0f)).isWithin(EPS).of(0.0f)
        assertThat(raDegreesFromHMS(6f, 0f, 0f)).isWithin(EPS).of(90.0f)
        assertThat(raDegreesFromHMS(6f, 30f, 0f)).isWithin(EPS).of(6.5f / 24 * 360)
        assertThat(raDegreesFromHMS(6f, 0f, (30 * 60).toFloat())).isWithin(EPS)
            .of(6.5f / 24 * 360)
    }

    @Test
    fun testDecFromDMS() {
        assertThat(decDegreesFromDMS(0f, 0f, 0f)).isWithin(EPS).of(0.0f)
        assertThat(decDegreesFromDMS(90f, 0f, 0f)).isWithin(EPS).of(90.0f)
        assertThat(decDegreesFromDMS(90f, 30f, 0f)).isWithin(EPS).of(90.5f)
        assertThat(decDegreesFromDMS(90f, 0f, (30 * 60).toFloat())).isWithin(EPS).of(90.5f)
    }
}