package com.google.android.stardroid.units

import com.google.android.stardroid.units.RaDec.Companion.raDegreesFromHMS
import com.google.android.stardroid.units.RaDec.Companion.decDegreesFromDMS
import com.google.common.truth.Truth
import org.junit.Test

class RaDecTest {
    @Test
    fun testRaFromHMS() {
        Truth.assertThat(raDegreesFromHMS(0f, 0f, 0f)).isWithin(EPS).of(0.0f)
        Truth.assertThat(raDegreesFromHMS(6f, 0f, 0f)).isWithin(EPS).of(90.0f)
        Truth.assertThat(raDegreesFromHMS(6f, 30f, 0f)).isWithin(EPS).of(6.5f / 24 * 360)
        Truth.assertThat(raDegreesFromHMS(6f, 0f, (30 * 60).toFloat())).isWithin(EPS)
            .of(6.5f / 24 * 360)
    }

    @Test
    fun testDecFromDMS() {
        Truth.assertThat(decDegreesFromDMS(0f, 0f, 0f)).isWithin(EPS).of(0.0f)
        Truth.assertThat(decDegreesFromDMS(90f, 0f, 0f)).isWithin(EPS).of(90.0f)
        Truth.assertThat(decDegreesFromDMS(90f, 30f, 0f)).isWithin(EPS).of(90.5f)
        Truth.assertThat(decDegreesFromDMS(90f, 0f, (30 * 60).toFloat())).isWithin(EPS).of(90.5f)
    }

    companion object {
        private const val EPS = 1e-5f
    }
}