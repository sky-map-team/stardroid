/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import kotlin.math.sqrt

/**
 * An immutable 3-vector in `Double` precision.
 *
 * v1's `Vector3` was a mutable `Float` struct (a 2009 Dalvik performance choice) with in-place
 * `normalize()`/`assign()`/`timesAssign`. v2 makes it immutable and `Double` (see
 * docs/design/core-math-astronomy.md); the cross product is the explicit [cross] infix rather
 * than v1's overloaded `operator times`.
 */
data class Vector3(val x: Double, val y: Double, val z: Double) {
    /** The square of the vector's length. Cheaper than [length] when only comparing magnitudes. */
    val length2: Double get() = x * x + y * y + z * z

    val length: Double get() = sqrt(length2)

    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Double) = Vector3(x * scale, y * scale, z * scale)

    operator fun div(factor: Double) = this * (1.0 / factor)

    operator fun unaryMinus() = Vector3(-x, -y, -z)

    /** Vector dot product. */
    infix fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

    /** Vector cross product (this × other). */
    infix fun cross(other: Vector3) =
        Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )

    /** Euclidean distance between the two points. */
    fun distanceTo(other: Vector3): Double = (this - other).length

    /**
     * The corresponding unit vector, or [ZERO] for a vector too short to normalize reliably
     * (matching v1's `normalizedCopy` guard).
     */
    fun normalized(): Vector3 = if (length < 1e-6) ZERO else this / length

    /** Projects this vector onto the given unit vector. */
    fun projectOnto(unitVector: Vector3): Vector3 = unitVector * (this dot unitVector)

    /** Cosine of the angle between this vector and [other]. */
    fun cosineSimilarity(other: Vector3): Double =
        (this dot other) / sqrt((this dot this) * (other dot other))

    companion object {
        val ZERO = Vector3(0.0, 0.0, 0.0)
        val UNIT_X = Vector3(1.0, 0.0, 0.0)
        val UNIT_Y = Vector3(0.0, 1.0, 0.0)
        val UNIT_Z = Vector3(0.0, 0.0, 1.0)
    }
}
