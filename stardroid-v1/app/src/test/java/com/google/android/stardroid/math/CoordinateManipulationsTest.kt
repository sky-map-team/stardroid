// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.math

import com.google.android.stardroid.math.RaDec.Companion.fromGeocentricCoords
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val TOL = 1e-5f

class CoordinateManipulationsTest {
    // TODO(jontayler): add some real tests for this class.
    @Test
    fun testEquals() {
        val one = Vector3(1f, 2f, 3f)
        val two = Vector3(2f, 4f, 6f)
        one.timesAssign(2f)
        assertThat(one).isEqualTo(two)
        assertThat(one).isNotSameInstanceAs(two)
        assertThat(one.hashCode()).isEqualTo(two.hashCode())
    }

    @Test
    fun testGeocentryCoordinatesConversion() {
        Vector3Subject.assertThat(getGeocentricCoords(RaDec(0f, 0f))).isWithin(TOL)
            .of(Vector3.unitX())
        Vector3Subject.assertThat(getGeocentricCoords(RaDec(90f, 0f))).isWithin(TOL)
            .of(Vector3.unitY())
        Vector3Subject.assertThat(getGeocentricCoords(RaDec(0f, 90f))).isWithin(TOL)
            .of(Vector3.unitZ())
        Vector3Subject.assertThat(getGeocentricCoords(RaDec(45f, 45f))).isWithin(TOL)
            .of(
                ((Vector3.unitX() + Vector3.unitY()).normalizedCopy() + Vector3.unitZ()).normalizedCopy()
            )
    }

    private val allTestValues = arrayOf(
        floatArrayOf(0f, 0f, 1f, 0f, 0f),
        floatArrayOf(90f, 0f, 0f, 1f, 0f),
        floatArrayOf(0f, 90f, 0f, 0f, 1f),
        floatArrayOf(180f, 0f, -1f, 0f, 0f),
        floatArrayOf(0f, -90f, 0f, 0f, -1f),
        floatArrayOf(270f, 0f, 0f, -1f, 0f)
    )

    @Test
    fun testCartesiansToSphericals() {
        for (testValues in allTestValues) {
            val ra = testValues[0]
            val dec = testValues[1]
            val x = testValues[2]
            val y = testValues[3]
            val z = testValues[4]
            val (ra1, dec1) = fromGeocentricCoords(Vector3(x, y, z))
            assertThat(ra1).isWithin(TOL).of(ra)
            assertThat(dec1).isWithin(TOL).of(dec)
        }
    }

    @Test
    fun testSphericalToCartesians() {
        for (testValues in allTestValues) {
            val ra = testValues[0]
            val dec = testValues[1]
            val x = testValues[2]
            val y = testValues[3]
            val z = testValues[4]
            val raDec = RaDec(ra, dec)
            val v = getGeocentricCoords(raDec)
            Vector3Subject.assertThat(v).isWithin(TOL).of(Vector3(x, y, z))
        }
    }

}