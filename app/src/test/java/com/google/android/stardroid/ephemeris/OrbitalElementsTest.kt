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
            distance = 0.0f,
            eccentricity= 0.0f,
            inclination= 0.0f,
            ascendingNode= 0.0f,
            perihelion= 0.0f,
            meanLongitude= 0.0f
        )
        assertThat(elements.anomaly).isWithin(TOL).of(0.0f)

        val elements2 = OrbitalElements(
            distance = 0.0f,
            eccentricity = 0.25f,
            inclination = 0.0f,
            ascendingNode = 0.0f,
            perihelion = 30 * DEGREES_TO_RADIANS,
            meanLongitude = 120 * DEGREES_TO_RADIANS
        )
        assertThat(elements2.anomaly).isWithin(TOL).of(117.54979f * DEGREES_TO_RADIANS)
    }
}