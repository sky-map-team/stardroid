/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import com.google.android.stardroid.math.RaDec.Companion.decDegreesFromDms
import com.google.android.stardroid.math.RaDec.Companion.fromGeocentricVector
import com.google.android.stardroid.math.RaDec.Companion.raDegreesFromHms
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

private const val TOL = 1e-9

/** Ported from v1 `RaDecTest` and `CoordinateManipulationsTest`. */
class RaDecTest {
    @Test
    fun raFromHms() {
        assertThat(raDegreesFromHms(0.0, 0.0, 0.0)).isWithin(TOL).of(0.0)
        assertThat(raDegreesFromHms(6.0, 0.0, 0.0)).isWithin(TOL).of(90.0)
        assertThat(raDegreesFromHms(6.0, 30.0, 0.0)).isWithin(TOL).of(6.5 / 24 * 360)
        assertThat(raDegreesFromHms(6.0, 0.0, 30.0 * 60)).isWithin(TOL).of(6.5 / 24 * 360)
    }

    @Test
    fun decFromDms() {
        assertThat(decDegreesFromDms(0.0, 0.0, 0.0)).isWithin(TOL).of(0.0)
        assertThat(decDegreesFromDms(90.0, 0.0, 0.0)).isWithin(TOL).of(90.0)
        assertThat(decDegreesFromDms(90.0, 30.0, 0.0)).isWithin(TOL).of(90.5)
        assertThat(decDegreesFromDms(90.0, 0.0, 30.0 * 60)).isWithin(TOL).of(90.5)
        // Negative declinations: minutes/seconds move away from zero, not toward it.
        assertThat(decDegreesFromDms(-10.0, 30.0, 0.0)).isWithin(TOL).of(-10.5)
        assertThat(decDegreesFromDms(-0.0, 30.0, 0.0)).isWithin(TOL).of(-0.5)
    }

    // ra, dec, x, y, z
    private val cases =
        listOf(
            listOf(0.0, 0.0, 1.0, 0.0, 0.0),
            listOf(90.0, 0.0, 0.0, 1.0, 0.0),
            listOf(0.0, 90.0, 0.0, 0.0, 1.0),
            listOf(180.0, 0.0, -1.0, 0.0, 0.0),
            listOf(0.0, -90.0, 0.0, 0.0, -1.0),
            listOf(270.0, 0.0, 0.0, -1.0, 0.0),
        )

    @Test
    fun raDecToGeocentricVector() {
        for ((ra, dec, x, y, z) in cases) {
            val v = RaDec(ra, dec).toGeocentricVector()
            assertThat(v.x).isWithin(1e-9).of(x)
            assertThat(v.y).isWithin(1e-9).of(y)
            assertThat(v.z).isWithin(1e-9).of(z)
        }
    }

    @Test
    fun geocentricVectorToRaDec() {
        for ((ra, dec, x, y, z) in cases) {
            val result = fromGeocentricVector(Vector3(x, y, z))
            assertThat(result.raDeg).isWithin(1e-7).of(ra)
            assertThat(result.decDeg).isWithin(1e-7).of(dec)
        }
    }
}
