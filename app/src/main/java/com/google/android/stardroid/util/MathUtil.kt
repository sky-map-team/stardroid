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
package com.google.android.stardroid.util

/**
 * Methods for doing mathematical operations with floats.
 *
 * @author Brent Bryan
 */
// TODO(jontayler): eliminate this class if we can eliminate floats.
object MathUtil {
    const val PI = Math.PI.toFloat()
    const val TWO_PI = 2f * PI
    const val DEGREES_TO_RADIANS = PI / 180
    const val RADIANS_TO_DEGREES = 180 / PI
    @JvmStatic
    fun abs(x: Float): Float {
        return Math.abs(x)
    }

    @JvmStatic
    fun sqrt(x: Float): Float {
        return Math.sqrt(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun floor(x: Float): Float {
        return Math.floor(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun ceil(x: Float): Float {
        return Math.ceil(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun sin(x: Float): Float {
        return Math.sin(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun cos(x: Float): Float {
        return Math.cos(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun tan(x: Float): Float {
        return sin(x) / cos(x)
    }

    @JvmStatic
    fun asin(x: Float): Float {
        return Math.asin(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun acos(x: Float): Float {
        return Math.acos(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun atan(x: Float): Float {
        return Math.atan(x.toDouble()).toFloat()
    }

    @JvmStatic
    fun atan2(y: Float, x: Float): Float {
        return Math.atan2(y.toDouble(), x.toDouble()).toFloat()
    }

    @JvmStatic
    fun log10(x: Float): Float {
        return Math.log10(x.toDouble()).toFloat()
    }
}

fun flooredMod(a: Float, n: Float): Float {
    return (if (a < 0) a % n + n else a) % n
}