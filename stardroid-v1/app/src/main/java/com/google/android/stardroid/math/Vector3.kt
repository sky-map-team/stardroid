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

import kotlin.math.sqrt


data class Vector3(@JvmField var x : Float, @JvmField var y : Float, @JvmField var z : Float) {

    /**
     * The square of the vector's length
     */
    val length2
        get() = x * x + y * y + z * z

    val length
        get() = sqrt(length2)

    /**
     * Constructs a Vector3 from a float[2] object.
     * Checks for length. This is probably inefficient, so if you're using this
     * you should already be questioning your use of float[] instead of Vector3.
     * @param xyz
     */
    constructor(xyz: FloatArray) : this(xyz[0], xyz[1], xyz[2]) {
        require(xyz.size == 3) { "Trying to create 3 vector from array of length: " + xyz.size }
    }

    /**
     * Assigns these values to the vector's components.
     */
    fun assign(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    /**
     * Assigns the values of the other vector to this one.
     */
    fun assign(other: Vector3) {
        x = other.x
        y = other.y
        z = other.z
    }

    /**
     * Normalizes the vector in place, i.e., map it to the corresponding unit vector.
     */
    fun normalize() : Unit {
        val norm = length
        x /= norm
        y /= norm
        z /= norm
    }

    /**
     * Scales the vector in place.
     */
    operator fun timesAssign(scale: Float) {
        x *= scale
        y *= scale
        z *= scale
    }

    /**
     * Subtracts the values of the given vector from this
     * object.
     */
    operator fun minusAssign(other: Vector3) {
        x -= other.x
        y -= other.y
        z -= other.z
    }

    /**
     * Returns the Vector dot product
     */
    infix fun dot(p2: Vector3): Float {
        return x * p2.x + y * p2.y + z * p2.z
    }

    /**
     * Returns the Vector cross product.
     */
    operator fun times(p2: Vector3): Vector3 {
        return Vector3(
            y * p2.z - z * p2.y,
            -x * p2.z + z * p2.x,
            x * p2.y - y * p2.x
        )
    }

    /**
     * Returns the distance between one vector and the next.
     */
    fun distanceFrom(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Java can't call Kotlin's copy() method due not supporting default params.
     * Temporary shim until Java is all gone.
     */
    fun copyForJ() : Vector3 {
        return copy()
    }

    operator fun plus(v2: Vector3): Vector3 {
        return Vector3(x + v2.x, y + v2.y, z + v2.z)
    }

    operator fun minus(v2: Vector3): Vector3 {
        return plus(-v2)
    }

    operator fun times(factor: Float): Vector3 {
        val scaled = copy()
        scaled *= factor
        return scaled
    }

    operator fun div(factor: Float): Vector3 {
        return this * (1 / factor)
    }

    operator fun unaryMinus(): Vector3 {
        return this * -1f
    }

    fun normalizedCopy(): Vector3 {
        return if (length < 0.000001f) {
            zero()
        } else this / length
    }

    /**
     * Projects this vector onto the given unit vector.
     */
    fun projectOnto(unitVector: Vector3): Vector3 {
        return unitVector * (this dot unitVector)
    }

    fun cosineSimilarity(
        v: Vector3
    ) = ((this dot v)
            / sqrt(
        (this dot this)
                * (v dot v)
    ))

    companion object Factory {
        fun zero() = Vector3(0f, 0f, 0f)
        fun unitX() = Vector3(1f, 0f, 0f)
        fun unitY() = Vector3(0f, 1f, 0f)
        fun unitZ() = Vector3(0f, 0f, 1f)
    }
}

