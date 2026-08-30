/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.math.Vector3
import kotlin.math.abs

/**
 * Exponentially weighted smoothing of a 3-axis sensor stream — v1's
 * `ExponentiallyWeightedSmoother`, minus the `SensorEventListener` plumbing so it unit-tests
 * on the JVM.
 *
 * Each axis moves toward the new sample by `alpha · diff^exponent` (sign-preserving), clamped
 * to never overshoot the sample: small jitters are damped hard while large real movements pass
 * through almost immediately.
 */
class ExponentiallyWeightedSmoother(private val alpha: Float, private val exponent: Int) {
    private val current = FloatArray(3)
    private var initialized = false

    /** Feeds one sample (first three entries used) and returns the smoothed value. */
    fun update(values: FloatArray): Vector3 {
        if (!initialized) {
            // Start at the first sample rather than decaying in from zero (v1 started at zero,
            // costing a visible settle on startup).
            values.copyInto(current, endIndex = 3)
            initialized = true
        } else {
            for (i in 0..2) {
                val diff = values[i] - current[i]
                var correction = diff * alpha
                repeat(exponent - 1) { correction *= abs(diff) }
                if (abs(correction) > abs(diff)) correction = diff
                current[i] += correction
            }
        }
        return Vector3(current[0].toDouble(), current[1].toDouble(), current[2].toDouble())
    }
}
