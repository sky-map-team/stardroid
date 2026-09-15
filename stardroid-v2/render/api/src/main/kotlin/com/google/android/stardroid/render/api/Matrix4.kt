/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import kotlin.math.tan

/**
 * An immutable 4x4 matrix for rendering, stored **column-major** to match OpenGL's
 * `glLoadMatrixf` / shader uniform layout. A port of v1's mutable `Float` `Matrix4x4`, made
 * immutable and `Double` to match the `:core:math` value types; [toFloatArray] produces the buffer
 * GL actually uploads.
 *
 * It carries only what the projection pipeline needs for the port — [times], element access, the
 * [view] / [perspective] builders, and [toFloatArray]. Affine builders (translation/scaling/
 * rotation) and a perspective-divide helper are deliberately deferred to the GL backend slice,
 * where a concrete caller will pin their shapes.
 */
class Matrix4(values: DoubleArray) {
    /** Column-major elements: index `col * 4 + row`. */
    private val m: DoubleArray

    init {
        require(values.size == 16) { "A 4x4 matrix needs 16 elements, got ${values.size}" }
        m = values.copyOf()
    }

    /** Element at 0-based [row], [col]; stored column-major internally. */
    operator fun get(
        row: Int,
        col: Int,
    ): Double = m[col * 4 + row]

    /** Matrix product `this × other`. */
    operator fun times(other: Matrix4): Matrix4 {
        val a = m
        val b = other.m
        val out = DoubleArray(16)
        for (col in 0 until 4) {
            val bc = col * 4
            val b0 = b[bc]
            val b1 = b[bc + 1]
            val b2 = b[bc + 2]
            val b3 = b[bc + 3]
            for (row in 0 until 4) {
                out[bc + row] = a[row] * b0 + a[4 + row] * b1 + a[8 + row] * b2 + a[12 + row] * b3
            }
        }
        return Matrix4(out)
    }

    /** The column-major elements as floats, ready for GL upload. */
    fun toFloatArray(): FloatArray = FloatArray(16) { m[it].toFloat() }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Matrix4 && m.contentEquals(other.m))

    override fun hashCode(): Int = m.contentHashCode()

    override fun toString(): String =
        buildString {
            append("Matrix4(")
            for (row in 0 until 4) {
                append("[")
                for (col in 0 until 4) {
                    append(this@Matrix4[row, col])
                    if (col < 3) append(", ")
                }
                append("]")
                if (row < 3) append(", ")
            }
            append(")")
        }

    companion object {
        // Near/far merely bracket the unit sphere; depth ordering is the painter's algorithm with
        // the depth buffer off (D18), so these are not load-bearing. Values carried over from v1.
        private const val NEAR = 0.01
        private const val FAR = 10000.0

        val IDENTITY =
            Matrix4(
                doubleArrayOf(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, 1.0, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                ),
            )

        /**
         * The view (rotation-only) matrix for an eye at the origin looking along [lineOfSight] with
         * the given [up]. Reproduces v1's `createView(lookDir, up, right)` with `right = lookDir ×
         * up`; [up] is re-orthogonalized so the basis is exactly orthonormal even if the inputs
         * drift slightly. Throws if [lineOfSight] and [up] are collinear (an invalid [SkyCamera]),
         * which would otherwise collapse every point to the screen centre.
         */
        fun view(
            lineOfSight: Vector3,
            up: Vector3,
        ): Matrix4 {
            val f = lineOfSight.normalized()
            val ortho = f cross up
            require(ortho.length2 >= 1e-12) { "lineOfSight and up must not be collinear" }
            val right = ortho.normalized()
            val u = right cross f
            return Matrix4(
                doubleArrayOf(
                    right.x, u.x, -f.x, 0.0,
                    right.y, u.y, -f.y, 0.0,
                    right.z, u.z, -f.z, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                ),
            )
        }

        /**
         * A perspective projection for a [widthPx] × [heightPx] surface. [fovDeg] is the full
         * field of view across the **shorter** viewport side, so the sky's apparent scale is
         * unchanged when the display rotates between portrait and landscape — the longer side
         * simply shows more sky. For landscape and square surfaces this equals the vertical FOV.
         * (v1 passed the half-angle "radius of view" and used `1/tan` directly, and never
         * rotated; here the full FOV is halved before the tangent.)
         */
        fun perspective(
            widthPx: Double,
            heightPx: Double,
            fovDeg: Double,
        ): Matrix4 {
            require(widthPx > 0.0 && heightPx > 0.0) {
                "viewport dimensions must be positive, got ${widthPx}x$heightPx"
            }
            require(fovDeg > 0.0 && fovDeg < 180.0) {
                "fovDeg must be in (0, 180), got $fovDeg"
            }
            val cot = 1.0 / tan(fovDeg * DEGREES_TO_RADIANS / 2.0)
            val shortPx = minOf(widthPx, heightPx)
            return Matrix4(
                doubleArrayOf(
                    cot * shortPx / widthPx, 0.0, 0.0, 0.0,
                    0.0, cot * shortPx / heightPx, 0.0, 0.0,
                    0.0, 0.0, -(FAR + NEAR) / (FAR - NEAR), -1.0,
                    0.0, 0.0, -2.0 * FAR * NEAR / (FAR - NEAR), 0.0,
                ),
            )
        }
    }
}
