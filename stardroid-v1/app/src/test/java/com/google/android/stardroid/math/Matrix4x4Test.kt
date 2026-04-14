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

import com.google.android.stardroid.math.MathUtils.sqrt
import com.google.android.stardroid.math.Matrix4x4.Companion.createIdentity
import com.google.android.stardroid.math.Matrix4x4.Companion.createRotation
import com.google.android.stardroid.math.Matrix4x4.Companion.createScaling
import com.google.android.stardroid.math.Matrix4x4.Companion.createTranslation
import org.junit.Test

class Matrix4x4Test {
    @Test
    fun testMultiplyByIdentity() {
        val identity = createIdentity()
        val m = Matrix4x4(
            floatArrayOf(
                1f,
                2f,
                3f,
                4f,
                5f,
                6f,
                7f,
                8f,
                9f,
                10f,
                11f,
                12f,
                13f,
                14f,
                15f,
                16f
            )
        )
        Matrix4x4Subject.assertThat(m).isWithin(TOL).of(identity * m)
        Matrix4x4Subject.assertThat(m).isWithin(TOL).of(m * identity)
    }

    @Test
    fun testMultiplyByScaling() {
        val m = Matrix4x4(
            floatArrayOf(
                1f,
                2f,
                3f,
                0f,
                5f,
                6f,
                7f,
                0f,
                9f,
                10f,
                11f,
                0f,
                0f,
                0f,
                0f,
                0f
            )
        )
        val scaling = createScaling(2f, 2f, 2f)
        val expected = Matrix4x4(
            floatArrayOf(
                2f,
                4f,
                6f,
                0f,
                10f,
                12f,
                14f,
                0f,
                18f,
                20f,
                22f,
                0f,
                0f,
                0f,
                0f,
                0f
            )
        )
        Matrix4x4Subject.assertThat(expected).isWithin(TOL).of(scaling * m)
    }

    @Test
    fun testMultiplyByTranslation() {
        val v = Vector3(1f, 1f, 1f)
        val trans = createTranslation(1f, 2f, 3f)
        val expected = Vector3(2f, 3f, 4f)
        Vector3Subject.assertThat(expected).isWithin(TOL).of(trans * v)
    }

    @Test
    fun testRotation3x3ParallelRotationHasNoEffect() {
        val m = createRotation(PI, Vector3(0f, 1f, 0f))
        val v = Vector3(0f, 2f, 0f)
        Vector3Subject.assertThat(v).isWithin(TOL).of(m * v)
    }

    @Test
    fun testRotation3x3PerpendicularRotation() {
        val m = createRotation(PI * 0.25f, Vector3(0f, -1f, 0f))
        val v = Vector3(1f, 0f, 0f)
        val oneOverSqrt2 = 1.0f / sqrt(2.0f)
        Vector3Subject.assertThat(Vector3(oneOverSqrt2, 0f, oneOverSqrt2)).isWithin(TOL)
            .of(m * v)
    }

    @Test
    fun testRotation3x3UnalignedAxis() {
        val axis = Vector3(1f, 1f, 1f).normalizedCopy()
        val numRotations = 5
        val m = createRotation(TWO_PI / numRotations, axis)
        val start = Vector3(2.34f, 3f, -17.6f)
        // float oneOverSqrt2 = 1.0f / MathUtil.sqrt(2.0f);
        var v = start
        for (i in 0..4) {
            v = m * v
        }
        Vector3Subject.assertThat(start).isWithin(TOL).of(v)
    }

    companion object {
        const val TOL = 0.00001f
    }
}