// Copyright 2008 Google Inc.
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

import com.google.android.stardroid.math.Geometry.calculateRotationMatrix
import com.google.android.stardroid.math.Geometry.getXYZ
import com.google.android.stardroid.math.Geometry.matrixMultiply
import com.google.android.stardroid.math.Geometry.matrixVectorMultiply
import com.google.android.stardroid.math.Geometry.scalarProduct
import com.google.android.stardroid.math.Geometry.vectorProduct
import com.google.android.stardroid.math.Matrix3x3Test.Companion.assertMatricesEqual
import com.google.android.stardroid.math.RaDec.Companion.fromGeocentricCoords
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val TOL = 0.00001f

class GeometryTest {
    fun assertVectorsEqual(v: Vector3, w: Vector3) {
        assertThat(w.x).isWithin(TOL).of(v.x)
        assertThat(w.y).isWithin(TOL).of(v.y)
        assertThat(w.z).isWithin(TOL).of(v.z)
    }

    private fun assertMatrixSame(m1: Matrix3x3, m2: Matrix3x3, tol: Float) {
        assertThat(m2.xx).isWithin(tol).of(m1.xx)
        assertThat(m2.xy).isWithin(tol).of(m1.xy)
        assertThat(m2.xz).isWithin(tol).of(m1.xz)
        assertThat(m2.yx).isWithin(tol).of(m1.yx)
        assertThat(m2.yy).isWithin(tol).of(m1.yy)
        assertThat(m2.yz).isWithin(tol).of(m1.yz)
        assertThat(m2.zx).isWithin(tol).of(m1.zx)
        assertThat(m2.zy).isWithin(tol).of(m1.zy)
        assertThat(m2.zz).isWithin(tol).of(m1.zz)
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
    fun testSphericalToCartesians() {
        for (testValues in allTestValues) {
            val ra = testValues[0]
            val dec = testValues[1]
            val x = testValues[2]
            val y = testValues[3]
            val z = testValues[4]
            val (x1, y1, z1) = getXYZ(RaDec(ra, dec))
            assertThat(x1).isWithin(TOL).of(x)
            assertThat(y1).isWithin(TOL).of(y)
            assertThat(z1).isWithin(TOL).of(z)
        }
    }

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
    fun testVectorProduct() {
        // Check that z is x X y
        val x = Vector3(1f, 0f, 0f)
        val y = Vector3(0f, 1f, 0f)
        val z = vectorProduct(x, y)
        assertVectorsEqual(z, Vector3(0f, 0f, 1f))

        // Check that a X b is perpendicular to a and b
        val a = Vector3(1f, -2f, 3f)
        val b = Vector3(2f, 0f, -4f)
        val c = vectorProduct(a, b)
        val aDotc = scalarProduct(a, c)
        val bDotc = scalarProduct(b, c)
        assertThat(aDotc).isWithin(TOL).of(0.0f)
        assertThat(bDotc).isWithin(TOL).of(0.0f)

        // Check that |a X b| is correct
        val v = Vector3(1f, 2f, 0f)
        val ww = vectorProduct(x, v)
        val wwDotww = scalarProduct(ww, ww)
        assertThat(wwDotww).isWithin(TOL)
            .of(Math.pow(1f * Math.sqrt(5.0) * Math.sin(Math.atan(2.0)), 2.0).toFloat())
    }

    @Test
    fun testMatrixInversion() {
        val m = Matrix3x3(1f, 2f, 0f, 0f, 1f, 5f, 0f, 0f, 1f)
        val inv = m.inverse
        val product = matrixMultiply(m, inv!!)
        val identity = Matrix3x3(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertMatrixSame(product, identity, TOL)
    }

    @Test
    fun testCalculateRotationMatrix() {
        val noRotation = calculateRotationMatrix(0f, Vector3(1f, 2f, 3f))
        val identity = Matrix3x3(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertMatrixSame(identity, noRotation, TOL)
        val rotAboutZ = calculateRotationMatrix(90f, Vector3(0f, 0f, 1f))
        assertMatrixSame(Matrix3x3(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f), rotAboutZ, TOL)
        val axis = Vector3(2f, -4f, 1f)
        axis.normalize()
        val rotA = calculateRotationMatrix(30f, axis)
        val rotB = calculateRotationMatrix(-30f, axis)
        val shouldBeIdentity = matrixMultiply(rotA, rotB)
        assertMatrixSame(identity, shouldBeIdentity, TOL)
        val axisPerpendicular = Vector3(4f, 2f, 0f)
        val rotatedAxisPerpendicular = matrixVectorMultiply(rotA, axisPerpendicular)

        // Should still be perpendicular
        assertThat(scalarProduct(axis, rotatedAxisPerpendicular)).isWithin(TOL).of(0.0f)
        // And the angle between them should be 30 degrees
        axisPerpendicular.normalize()
        rotatedAxisPerpendicular.normalize()
        assertThat(scalarProduct(axisPerpendicular, rotatedAxisPerpendicular)).isWithin(TOL)
            .of(
                Math.cos(30.0 * Geometry.DEGREES_TO_RADIANS).toFloat()
            )
    }

    @Test
    fun testMatrixMultiply() {
        val m1 = Matrix3x3(1f, 2f, 4f, -1f, -3f, 5f, 3f, 2f, 6f)
        val m2 = Matrix3x3(3f, -1f, 4f, 0f, 2f, 1f, 2f, -1f, 2f)
        val v1 = Vector3(0f, -1f, 2f)
        val v2 = Vector3(2f, -2f, 3f)
        assertMatricesEqual(
            Matrix3x3(11f, -1f, 14f, 7f, -10f, 3f, 21f, -5f, 26f),
            matrixMultiply(m1, m2), TOL.toFloat()
        )
        assertVectorsEqual(
            Vector3(6f, 13f, 10f),
            matrixVectorMultiply(m1, v1)
        )
        assertVectorsEqual(
            Vector3(10f, 19f, 20f),
            matrixVectorMultiply(m1, v2)
        )
    }
}