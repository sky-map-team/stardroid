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

import com.google.android.stardroid.math.MathUtil.sqrt

data class Vector3(@JvmField var x : Float, @JvmField var y : Float, @JvmField var z : Float) {

    /**
     * The square of the vector's length
     */
    val length2
        get() = x * x + y * y + z * z

    val length
        get() = MathUtil.sqrt(length2)


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
    fun normalize() {
        val norm = length
        x /= norm
        y /= norm
        z /= norm
    }

    /**
     * Scales the vector in place.
     */
    fun scale(scale: Float) {
        x *= scale
        y *= scale
        z *= scale
    }

    /**
     * Subtracts the values of the given vector from this
     * object.
     */
    fun subtract(other: Vector3) {
        x -= other.x
        y -= other.y
        z -= other.z
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
}