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

import com.google.android.stardroid.util.MathUtil

// TODO(jontayler): Convert to a data class once things don't inherit from it.
open class Vector3 {
    @JvmField
    var x: Float

    @JvmField
    var y: Float

    @JvmField
    var z: Float

    /**
     * The square of the vector's length
     */
    val length2
        get() = x * x + y * y + z * z

    val length
        get() = MathUtil.sqrt(length2)

    constructor(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    /**
     * Constructs a Vector3 from a float[2] object.
     * Checks for length. This is probably inefficient, so if you're using this
     * you should already be questioning your use of float[] instead of Vector3.
     * @param xyz
     */
    constructor(xyz: FloatArray) {
        require(xyz.size == 3) { "Trying to create 3 vector from array of length: " + xyz.size }
        x = xyz[0]
        y = xyz[1]
        z = xyz[2]
    }

    open fun copy(): Vector3 {
        return Vector3(x, y, z)
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
     * Normalize the vector in place, i.e., map it to the corresponding unit vector.
     */
    fun normalize() {
        val norm = length
        x /= norm
        y /= norm
        z /= norm
    }

    /**
     * Scale the vector in place.
     */
    fun scale(scale: Float) {
        x *= scale
        y *= scale
        z *= scale
    }

    open fun toFloatArray(): FloatArray {
        return floatArrayOf(x, y, z)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Vector3) return false
        // float equals is a bit of a dodgy concept
        return other.x == x && other.y == y && other.z == z
    }

    override fun hashCode(): Int {
        // This is dumb, but it will do for now.
        return java.lang.Float.floatToIntBits(x) + java.lang.Float.floatToIntBits(y) + java.lang.Float.floatToIntBits(
            z
        )
    }

    override fun toString(): String {
        return String.format("x=%f, y=%f, z=%f", x, y, z)
    }
}