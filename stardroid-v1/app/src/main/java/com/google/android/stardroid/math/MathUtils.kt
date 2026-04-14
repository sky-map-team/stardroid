// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.math

/**
 * Basic methods for doing mathematical operations with floats.
 *
 * @author Brent Bryan
 */
const val PI = Math.PI.toFloat()
const val TWO_PI = 2f * PI
const val DEGREES_TO_RADIANS = PI / 180.0f
const val RADIANS_TO_DEGREES = 180.0f / PI

fun flooredMod(a: Float, n: Float) = (if (a < 0) a % n + n else a) % n

/**
 * Returns the modulo the given value by 2\pi. Returns an angle in the range 0
 * to 2\pi radians.
 */
fun mod2pi(x: Float) = positiveMod(x, TWO_PI)

/**
 * Calculates x modulus y, but ensures that the result lies in [0, y)
 */
fun positiveMod(x: Double, y: Double): Double {
    var remainder = x % y
    if (remainder < 0) remainder += y
    return remainder
}

/**
 * Calculates x modulus y, but ensures that the result lies in [0, y)
 */
// Sadly we can't use generics for this.
private fun positiveMod(x: Float, y: Float): Float {
    var remainder = x % y
    if (remainder < 0) remainder += y
    return remainder
}

/** Calculates the length of the vector [x, y, z] */
fun norm(x: Float, y: Float, z: Float) = kotlin.math.sqrt(x * x + y * y + z * z)


// TODO(jontayler): eliminate this class if we can eliminate floats.
object MathUtils {
    // TODO(jontayler): just inline these when everything is Kotlin
    @JvmStatic
    fun abs(x: Float) = kotlin.math.abs(x)

    @JvmStatic
    fun sqrt(x: Float) = kotlin.math.sqrt(x)

    @JvmStatic
    fun sin(x: Float) = kotlin.math.sin(x)

    @JvmStatic
    fun cos(x: Float) = kotlin.math.cos(x)

    @JvmStatic
    fun tan(x: Float) = kotlin.math.tan(x)

    @JvmStatic
    fun asin(x: Float) = kotlin.math.asin(x)

    @JvmStatic
    fun acos(x: Float) = kotlin.math.acos(x)

    @JvmStatic
    fun atan2(y: Float, x: Float) = kotlin.math.atan2(y, x)

}