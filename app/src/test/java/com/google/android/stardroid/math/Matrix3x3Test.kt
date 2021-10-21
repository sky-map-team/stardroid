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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Matrix3x3Test {
    @Test
    fun testDeterminant() {
        assertThat(Matrix3x3.identity.determinant).isWithin(TOL).of(1f)
    }

    @Test
    fun testIdInverse() {
        Matrix3x3Subject.assertThat(Matrix3x3.identity).isWithin(TOL)
            .of(Matrix3x3.identity.inverse!!)
    }

    @Test
    fun testMatrix33Inversion() {
        var m = Matrix3x3(
            1F,
            2f,
            0f,
            0f,
            1f,
            5f,
            0f,
            0f,
            1f
        )
        var inv = m.inverse
        var product = Geometry.matrixMultiply(m, inv!!)
        Matrix3x3Subject.assertThat(Matrix3x3.identity).isWithin(TOL).of(product)
        m = Matrix3x3(
            1f,
            2f,
            3f,
            6f,
            5f,
            4f,
            0f,
            0f,
            1f
        )
        inv = m.inverse
        product = Geometry.matrixMultiply(m, inv!!)
        Matrix3x3Subject.assertThat(Matrix3x3.identity).isWithin(TOL).of(product)
    }

    @Test
    fun testTranspose() {
        val m = Matrix3x3(
            1f,
            2f,
            3f,
            4f,
            5f,
            6f,
            7f,
            8f,
            9f
        )
        m.transpose()
        val mt = Matrix3x3(
            1f,
            4f,
            7f,
            2f,
            5f,
            8f,
            3f,
            6f,
            9f
        )
        Matrix3x3Subject.assertThat(m).isWithin(TOL).of(mt)
    }

    @Test
    fun testConstructFromColVectors() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val v3 = Vector3(7f, 8f, 9f)
        val m = Matrix3x3(
            1f, 4f, 7f,
            2f, 5f, 8f,
            3f, 6f, 9f
        )
        val mt = Matrix3x3(v1, v2, v3)
        Matrix3x3Subject.assertThat(m).isWithin(TOL).of(mt)
    }

    @Test
    fun testConstructFromRowVectors() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val v3 = Vector3(7f, 8f, 9f)
        val m = Matrix3x3(
            1f, 4f, 7f,
            2f, 5f, 8f,
            3f, 6f, 9f
        )
        m.transpose()
        val mt = Matrix3x3(v1, v2, v3, false)
        Matrix3x3Subject.assertThat(m).isWithin(TOL).of(mt)
    }

    companion object {
        private const val TOL = 0.00001f
    }
}