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

import com.google.android.stardroid.math.MathUtils.sin
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.tan

/**
 * Represents a 4x4 matrix, specifically for use in rendering code. Consequently includes
 * functions for creating matrices for doing things like rotations.
 */
data class Matrix4x4(private val contents: FloatArray) {
    val floatArray = FloatArray(16)

    init {
        assert(contents.size == 16)
        System.arraycopy(contents, 0, floatArray, 0, 16)
    }

    operator fun times(mat2 : Matrix4x4): Matrix4x4 {
        val m = this.floatArray
        val n = mat2.floatArray
        return Matrix4x4(
            floatArrayOf(
                m[0] * n[0] + m[4] * n[1] + m[8] * n[2] + m[12] * n[3],
                m[1] * n[0] + m[5] * n[1] + m[9] * n[2] + m[13] * n[3],
                m[2] * n[0] + m[6] * n[1] + m[10] * n[2] + m[14] * n[3],
                m[3] * n[0] + m[7] * n[1] + m[11] * n[2] + m[15] * n[3],
                m[0] * n[4] + m[4] * n[5] + m[8] * n[6] + m[12] * n[7],
                m[1] * n[4] + m[5] * n[5] + m[9] * n[6] + m[13] * n[7],
                m[2] * n[4] + m[6] * n[5] + m[10] * n[6] + m[14] * n[7],
                m[3] * n[4] + m[7] * n[5] + m[11] * n[6] + m[15] * n[7],
                m[0] * n[8] + m[4] * n[9] + m[8] * n[10] + m[12] * n[11],
                m[1] * n[8] + m[5] * n[9] + m[9] * n[10] + m[13] * n[11],
                m[2] * n[8] + m[6] * n[9] + m[10] * n[10] + m[14] * n[11],
                m[3] * n[8] + m[7] * n[9] + m[11] * n[10] + m[15] * n[11],
                m[0] * n[12] + m[4] * n[13] + m[8] * n[14] + m[12] * n[15],
                m[1] * n[12] + m[5] * n[13] + m[9] * n[14] + m[13] * n[15],
                m[2] * n[12] + m[6] * n[13] + m[10] * n[14] + m[14] * n[15],
                m[3] * n[12] + m[7] * n[13] + m[11] * n[14] + m[15] * n[15]
            )
        )
    }

    operator fun times(v : Vector3): Vector3 {
        val m = this.floatArray
        return Vector3(
            m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12],
            m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13],
            m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
        )
    }

    companion object {
        @JvmStatic
        fun createIdentity(): Matrix4x4 {
            return createScaling(1f, 1f, 1f)
        }

        @JvmStatic
        fun createScaling(x: Float, y: Float, z: Float): Matrix4x4 {
            return Matrix4x4(
                floatArrayOf(
                    x, 0f, 0f, 0f, 0f, y, 0f, 0f, 0f, 0f, z, 0f, 0f, 0f, 0f, 1f
                )
            )
        }

        @JvmStatic
        fun createTranslation(x: Float, y: Float, z: Float): Matrix4x4 {
            return Matrix4x4(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f,
                    x, y, z, 1f
                )
            )
        }

        // axis MUST be normalized.
        @JvmStatic
        fun createRotation(angleRadians: Float, unitAxis: Vector3): Matrix4x4 {
            val m = FloatArray(16)
            val xSqr = unitAxis.x * unitAxis.x
            val ySqr = unitAxis.y * unitAxis.y
            val zSqr = unitAxis.z * unitAxis.z
            val sinAngle = sin(angleRadians)
            val cosAngle = cos(angleRadians)
            val oneMinusCosAngle = 1 - cosAngle
            val xSinAngle = unitAxis.x * sinAngle
            val ySinAngle = unitAxis.y * sinAngle
            val zSinAngle = unitAxis.z * sinAngle
            val zOneMinusCosAngle = unitAxis.z * oneMinusCosAngle
            val xyOneMinusCosAngle = unitAxis.x * unitAxis.y * oneMinusCosAngle
            val xzOneMinusCosAngle = unitAxis.x * zOneMinusCosAngle
            val yzOneMinusCosAngle = unitAxis.y * zOneMinusCosAngle
            m[0] = xSqr + (ySqr + zSqr) * cosAngle
            m[1] = xyOneMinusCosAngle + zSinAngle
            m[2] = xzOneMinusCosAngle - ySinAngle
            m[3] = 0f
            m[4] = xyOneMinusCosAngle - zSinAngle
            m[5] = ySqr + (xSqr + zSqr) * cosAngle
            m[6] = yzOneMinusCosAngle + xSinAngle
            m[7] = 0f
            m[8] = xzOneMinusCosAngle + ySinAngle
            m[9] = yzOneMinusCosAngle - xSinAngle
            m[10] = zSqr + (xSqr + ySqr) * cosAngle
            m[11] = 0f
            m[12] = 0f
            m[13] = 0f
            m[14] = 0f
            m[15] = 1f
            return Matrix4x4(m)
        }

        @JvmStatic
        fun createPerspectiveProjection(
            width: Float,
            height: Float,
            fovyInRadians: Float
        ): Matrix4x4 {
            val near = 0.01f
            val far = 10000.0f
            val inverseAspectRatio = height / width
            val oneOverTanHalfRadiusOfView = 1.0f / tan(fovyInRadians)
            return Matrix4x4(
                floatArrayOf(
                    inverseAspectRatio * oneOverTanHalfRadiusOfView, 0f, 0f, 0f, 0f,
                    oneOverTanHalfRadiusOfView, 0f, 0f, 0f, 0f,
                    -(far + near) / (far - near), -1f, 0f, 0f,
                    -2 * far * near / (far - near), 0f
                )
            )
        }

        @JvmStatic
        fun createView(lookDir: Vector3, up: Vector3, right: Vector3): Matrix4x4 {
            return Matrix4x4(
                floatArrayOf(
                    right.x,
                    up.x,
                    -lookDir.x, 0f,
                    right.y,
                    up.y,
                    -lookDir.y, 0f,
                    right.z,
                    up.z,
                    -lookDir.z, 0f, 0f, 0f, 0f, 1f
                )
            )
        }

        // TODO(johntaylor): inline and remove this once we're fully on Kotlin.
        @JvmStatic
        fun times(mat1: Matrix4x4, mat2: Matrix4x4): Matrix4x4 {
            return mat1 * mat2
        }

        // TODO(johntaylor): inline and remove this once we're fully on Kotlin.
        @JvmStatic
        fun multiplyMV(mat: Matrix4x4, v: Vector3): Vector3 {
            return mat * v
        }

        /**
         * Used to perform a perspective transformation.  This multiplies the given
         * vector by the matrix, but also divides the x and y components by the w
         * component of the result, as needed when doing perspective projections.
         */
        @JvmStatic
        fun transformVector(mat: Matrix4x4, v: Vector3): Vector3 {
            val trans = multiplyMV(mat, v)
            val m = mat.floatArray
            val w = m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15]
            val oneOverW = 1.0f / w
            trans.x *= oneOverW
            trans.y *= oneOverW
            // Don't transform z, we just leave it as a "pseudo-depth".
            return trans
        }
    }
}