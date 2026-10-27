/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import kotlin.math.PI

/** 2π, in radians. */
const val TWO_PI: Double = 2.0 * PI

/**
 * `a` modulo `n`, with the result always in `[0, n)` even for negative `a`
 * (unlike Kotlin's `%`, which keeps the sign of the dividend).
 */
fun floorMod(
    a: Double,
    n: Double,
): Double {
    val r = a % n
    return if (r < 0) r + n else r
}

/** Reduces an angle in radians to `[0, 2π)`. (v1 called this `mod2pi`.) */
fun normalizeRadians(radians: Double): Double = floorMod(radians, TWO_PI)

/** Reduces an angle in degrees to `[0, 360)`. */
fun normalizeDegrees(degrees: Double): Double = floorMod(degrees, 360.0)
