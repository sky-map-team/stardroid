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
package com.google.android.stardroid.units

import com.google.android.stardroid.util.Geometry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Matrix33Test {
    @Test
    fun testDeterminant() {
        assertThat(Matrix33.getIdMatrix().determinant).isWithin(TOL).of(1f)
    }

    @Test
    fun testIdInverse() {
        assertMatricesEqual(Matrix33.getIdMatrix(), Matrix33.getIdMatrix().inverse, TOL)
    }

    @Test
    fun testMatrix33Inversion() {
        var m = Matrix33(1F, 2f, 0f, 0f, 1f, 5f, 0f, 0f, 1f)
        var inv = m.inverse
        var product = Geometry.matrixMultiply(m, inv)
        assertMatricesEqual(Matrix33.getIdMatrix(), product, TOL)
        m = Matrix33(1f, 2f, 3f, 6f, 5f, 4f, 0f, 0f, 1f)
        inv = m.inverse
        product = Geometry.matrixMultiply(m, inv)
        assertMatricesEqual(Matrix33.getIdMatrix(), product, TOL)
    }

    @Test
    fun testTranspose() {
        val m = Matrix33(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)
        m.transpose()
        val mt = Matrix33(1f, 4f, 7f, 2f, 5f, 8f, 3f, 6f, 9f)
        assertMatricesEqual(m, mt, TOL)
    }

    @Test
    fun testConstructFromColVectors() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val v3 = Vector3(7f, 8f, 9f)
        val m = Matrix33(1f, 4f, 7f,
                2f, 5f, 8f,
                3f, 6f, 9f)
        val mt = Matrix33(v1, v2, v3)
        assertMatricesEqual(m, mt, TOL)
    }

    @Test
    fun testConstructFromRowVectors() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val v3 = Vector3(7f, 8f, 9f)
        val m = Matrix33(1f, 4f, 7f,
                2f, 5f, 8f,
                3f, 6f, 9f)
        m.transpose()
        val mt = Matrix33(v1, v2, v3, false)
        assertMatricesEqual(m, mt, TOL)
    }

    companion object {
        private const val TOL = 0.00001f
        @JvmStatic
        fun assertMatricesEqual(m1: Matrix33, m2: Matrix33, TOL: Float) {
            assertThat(m1.xx).isWithin(TOL).of(m2.xx)
            assertThat(m1.xy).isWithin(TOL).of(m2.xy)
            assertThat(m1.xz).isWithin(TOL).of(m2.xz)
            assertThat(m1.yx).isWithin(TOL).of(m2.yx)
            assertThat(m1.yy).isWithin(TOL).of(m2.yy)
            assertThat(m1.yz).isWithin(TOL).of(m2.yz)
            assertThat(m1.zx).isWithin(TOL).of(m2.zx)
            assertThat(m1.zy).isWithin(TOL).of(m2.zy)
            assertThat(m1.zz).isWithin(TOL).of(m2.zz)
        }
    }
}