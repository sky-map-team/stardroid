package com.google.android.stardroid.ephemeris

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val TOL = 1e-5f

class OrbitalElementsTest {
    // Test values from http://www.jgiesen.de/kepler/kepler.html
    @Test
    fun testCalculateAnomaly() {
        val elements = OrbitalElements(
            /*d=*/0.0f,
            /*e=*/0.0f,
            /*i=*/0.0f,
            /*a=*/0.0f,
            /*p=*/0.0f,
            /*l=*/0.0f
        )
        assertThat(elements.anomaly).isWithin(TOL).of(0.0f)

        val elements2 = OrbitalElements(
            /*d=*/0.0f,
            /*e=*/0.25f,
            /*i=*/0.0f,
            /*a=*/0.0f,
            /*p=*/30 * DEGREES_TO_RADIANS,
            /*l=*/120 * DEGREES_TO_RADIANS
        )
        assertThat(elements2.anomaly).isWithin(TOL).of(117.54979f * DEGREES_TO_RADIANS)
    }
}