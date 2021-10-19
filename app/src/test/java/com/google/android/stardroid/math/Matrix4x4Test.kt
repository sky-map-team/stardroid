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

import com.google.android.stardroid.math.Matrix4x4.Companion.createIdentity
import com.google.android.stardroid.math.Matrix4x4.Companion.multiplyMM
import com.google.android.stardroid.math.Matrix4x4.Companion.createScaling
import com.google.android.stardroid.math.Matrix4x4.Companion.createTranslation
import com.google.android.stardroid.math.Matrix4x4.Companion.multiplyMV
import com.google.android.stardroid.math.Matrix4x4.Companion.createRotation
import com.google.android.stardroid.math.MathUtil.sqrt
import junit.framework.TestCase

class Matrix4x4Test : TestCase() {
    fun wrap(matrix: FloatArray): Array<Float?> {
        val m = arrayOfNulls<Float>(matrix.size)
        for (i in matrix.indices) {
            m[i] = matrix[i]
        }
        return m
    }

    fun assertVectorsEqual(v1: Vector3, v2: Vector3) {
        assertEquals(v1.x, v2.x, DELTA)
        assertEquals(v1.y, v2.y, DELTA)
        assertEquals(v1.z, v2.z, DELTA)
    }

    fun assertMatEqual(mat1: Matrix4x4, mat2: Matrix4x4) {
        val m1 = mat1.floatArray
        val m2 = mat2.floatArray
        assertEquals(m1[0], m2[0], DELTA)
        assertEquals(m1[1], m2[1], DELTA)
        assertEquals(m1[2], m2[2], DELTA)
        assertEquals(m1[3], m2[3], DELTA)
        assertEquals(m1[4], m2[4], DELTA)
        assertEquals(m1[5], m2[5], DELTA)
        assertEquals(m1[6], m2[6], DELTA)
        assertEquals(m1[7], m2[7], DELTA)
        assertEquals(m1[8], m2[8], DELTA)
        assertEquals(m1[9], m2[9], DELTA)
        assertEquals(m1[10], m2[10], DELTA)
        assertEquals(m1[11], m2[11], DELTA)
        assertEquals(m1[12], m2[12], DELTA)
        assertEquals(m1[13], m2[13], DELTA)
        assertEquals(m1[14], m2[14], DELTA)
        assertEquals(m1[15], m2[15], DELTA)
    }

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
        assertMatEqual(m, multiplyMM(identity, m))
        assertMatEqual(m, multiplyMM(m, identity))
    }

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
        assertMatEqual(expected, multiplyMM(scaling, m))
        assertMatEqual(expected, multiplyMM(m, scaling))
    }

    fun testMultiplyByTranslation() {
        val v = Vector3(1f, 1f, 1f)
        val trans = createTranslation(1f, 2f, 3f)
        val expected = Vector3(2f, 3f, 4f)
        assertVectorsEqual(expected, multiplyMV(trans, v))
    }

    fun testRotation3x3ParallelRotationHasNoEffect() {
        val m = createRotation(MathUtil.PI, Vector3(0f, 1f, 0f))
        val v = Vector3(0f, 2f, 0f)
        assertVectorsEqual(v, multiplyMV(m, v))
    }

    fun testRotation3x3PerpendicularRotation() {
        val m = createRotation(MathUtil.PI * 0.25f, Vector3(0f, -1f, 0f))
        val v = Vector3(1f, 0f, 0f)
        val oneOverSqrt2 = 1.0f / sqrt(2.0f)
        assertVectorsEqual(Vector3(oneOverSqrt2, 0f, oneOverSqrt2), multiplyMV(m, v))
    }

    fun testRotation3x3UnalignedAxis() {
        var axis = Vector3(1f, 1f, 1f).normalizedCopy()
        val numRotations = 5
        val m = createRotation(MathUtil.TWO_PI / numRotations, axis)
        val start = Vector3(2.34f, 3f, -17.6f)
        // float oneOverSqrt2 = 1.0f / MathUtil.sqrt(2.0f);
        var v = start
        for (i in 0..4) {
            v = multiplyMV(m, v)
        }
        assertVectorsEqual(start, v)
    }

    companion object {
        const val DELTA = 0.00001f
    }
}