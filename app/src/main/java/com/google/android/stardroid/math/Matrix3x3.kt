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

import kotlin.jvm.JvmOverloads

/**
 * Class for representing a 3x3 matrix explicitly, avoiding heap
 * allocation as far as possible.
 *
 * @author Dominic Widdows
 */
class Matrix3x3 : Cloneable {
    @JvmField
    var xx = 0f
    @JvmField
    var xy = 0f
    @JvmField
    var xz = 0f
    @JvmField
    var yx = 0f
    @JvmField
    var yy = 0f
    @JvmField
    var yz = 0f
    @JvmField
    var zx = 0f
    @JvmField
    var zy = 0f
    @JvmField
    var zz = 0f

    /**
     * Construct a new matrix.
     * @param xx row 1, col 1
     * @param xy row 1, col 2
     * @param xz row 1, col 3
     * @param yx row 2, col 1
     * @param yy row 2, col 2
     * @param yz row 2, col 3
     * @param zx row 3, col 1
     * @param zy row 3, col 2
     * @param zz row 3, col 3
     */
    constructor(
        xx: Float, xy: Float, xz: Float,
        yx: Float, yy: Float, yz: Float,
        zx: Float, zy: Float, zz: Float
    ) {
        this.xx = xx
        this.xy = xy
        this.xz = xz
        this.yx = yx
        this.yy = yy
        this.yz = yz
        this.zx = zx
        this.zy = zy
        this.zz = zz
    }
    /**
     * Construct a matrix from three vectors.
     * @param columnVectors true if the vectors are column vectors, otherwise
     * they're row vectors.
     */
    /**
     * Construct a matrix from three column vectors.
     */
    @JvmOverloads
    constructor(v1: Vector3, v2: Vector3, v3: Vector3, columnVectors: Boolean = true) {
        if (columnVectors) {
            xx = v1.x
            yx = v1.y
            zx = v1.z
            xy = v2.x
            yy = v2.y
            zy = v2.z
            xz = v3.x
            yz = v3.y
            zz = v3.z
        } else {
            xx = v1.x
            xy = v1.y
            xz = v1.z
            yx = v2.x
            yy = v2.y
            yz = v2.z
            zx = v3.x
            zy = v3.y
            zz = v3.z
        }
    }

    // TODO(widdows): rename this to something like copyOf().
    public override fun clone(): Matrix3x3 {
        return Matrix3x3(
            xx, xy, xz,
            yx, yy, yz,
            zx, zy, zz
        )
    }

    /**
     * Create a zero matrix.
     */
    constructor() {
        Matrix3x3(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    val determinant: Float
        get() = xx * yy * zz + xy * yz * zx + xz * yx * zy - xx * yz * zy - yy * zx * xz - zz * xy * yx
    val inverse: Matrix3x3?
        get() {
            val det = determinant
            return if (det.toDouble() == 0.0) null else Matrix3x3(
                (yy * zz - yz * zy) / det, (xz * zy - xy * zz) / det, (xy * yz - xz * yy) / det,
                (yz * zx - yx * zz) / det, (xx * zz - xz * zx) / det, (xz * yx - xx * yz) / det,
                (yx * zy - yy * zx) / det, (xy * zx - xx * zy) / det, (xx * yy - xy * yx) / det
            )
        }

    /**
     * Transpose the matrix, in place.
     */
    fun transpose() {
        var tmp: Float
        tmp = xy
        xy = yx
        yx = tmp
        tmp = xz
        xz = zx
        zx = tmp
        tmp = yz
        yz = zy
        zy = tmp
    }

    companion object {
        @JvmStatic
        val idMatrix: Matrix3x3
            get() = Matrix3x3(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}