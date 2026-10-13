/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import kotlin.math.abs

/**
 * An immutable 3x3 matrix in `Double` precision. Elements are named `[row][col]`, e.g. [xy] is
 * row 1, column 2.
 *
 * v1's `Matrix3x3` was a mutable `Float` type with in-place `transpose()`; v2 is immutable and
 * `Double`, so [transposed] and [inverse] return new matrices.
 */
data class Matrix3(
    val xx: Double,
    val xy: Double,
    val xz: Double,
    val yx: Double,
    val yy: Double,
    val yz: Double,
    val zx: Double,
    val zy: Double,
    val zz: Double,
) {
    val determinant: Double
        get() =
            xx * yy * zz + xy * yz * zx + xz * yx * zy -
                xx * yz * zy - yy * zx * xz - zz * xy * yx

    /** The inverse matrix, or `null` if the determinant is too close to zero to invert reliably. */
    val inverse: Matrix3?
        get() {
            val det = determinant
            if (abs(det) < 1e-8) return null
            return Matrix3(
                (yy * zz - yz * zy) / det,
                (xz * zy - xy * zz) / det,
                (xy * yz - xz * yy) / det,
                (yz * zx - yx * zz) / det,
                (xx * zz - xz * zx) / det,
                (xz * yx - xx * yz) / det,
                (yx * zy - yy * zx) / det,
                (xy * zx - xx * zy) / det,
                (xx * yy - xy * yx) / det,
            )
        }

    fun transposed() =
        Matrix3(
            xx, yx, zx,
            xy, yy, zy,
            xz, yz, zz,
        )

    operator fun times(m: Matrix3) =
        Matrix3(
            xx * m.xx + xy * m.yx + xz * m.zx,
            xx * m.xy + xy * m.yy + xz * m.zy,
            xx * m.xz + xy * m.yz + xz * m.zz,
            yx * m.xx + yy * m.yx + yz * m.zx,
            yx * m.xy + yy * m.yy + yz * m.zy,
            yx * m.xz + yy * m.yz + yz * m.zz,
            zx * m.xx + zy * m.yx + zz * m.zx,
            zx * m.xy + zy * m.yy + zz * m.zy,
            zx * m.xz + zy * m.yz + zz * m.zz,
        )

    operator fun times(v: Vector3) =
        Vector3(
            xx * v.x + xy * v.y + xz * v.z,
            yx * v.x + yy * v.y + yz * v.z,
            zx * v.x + zy * v.y + zz * v.z,
        )

    companion object {
        val IDENTITY =
            Matrix3(
                1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0,
            )

        /**
         * Builds a matrix from three vectors.
         *
         * @param columnVectors if true (default) the vectors become the columns, else the rows.
         */
        fun fromVectors(
            v1: Vector3,
            v2: Vector3,
            v3: Vector3,
            columnVectors: Boolean = true,
        ) = if (columnVectors) {
            Matrix3(
                v1.x, v2.x, v3.x,
                v1.y, v2.y, v3.y,
                v1.z, v2.z, v3.z,
            )
        } else {
            Matrix3(
                v1.x, v1.y, v1.z,
                v2.x, v2.y, v2.z,
                v3.x, v3.y, v3.z,
            )
        }
    }
}
