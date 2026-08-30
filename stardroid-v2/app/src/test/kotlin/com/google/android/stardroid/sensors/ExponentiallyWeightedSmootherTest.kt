/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ExponentiallyWeightedSmootherTest {
    @Test
    fun startsAtFirstSample() {
        val smoother = ExponentiallyWeightedSmoother(alpha = 0.7f, exponent = 3)
        val first = smoother.update(floatArrayOf(1f, 2f, 3f))
        assertThat(first.x).isWithin(TOL).of(1.0)
        assertThat(first.y).isWithin(TOL).of(2.0)
        assertThat(first.z).isWithin(TOL).of(3.0)
    }

    @Test
    fun dampsSmallJitterHard() {
        val smoother = ExponentiallyWeightedSmoother(alpha = 0.7f, exponent = 3)
        smoother.update(floatArrayOf(1f, 0f, 0f))
        // A 0.1 jitter moves by 0.7 · 0.1³ = 0.0007 — essentially suppressed.
        val smoothed = smoother.update(floatArrayOf(1.1f, 0f, 0f))
        assertThat(smoothed.x).isWithin(1e-4).of(1.0007)
    }

    @Test
    fun passesLargeMovementsThrough() {
        val smoother = ExponentiallyWeightedSmoother(alpha = 0.7f, exponent = 3)
        smoother.update(floatArrayOf(0f, 0f, 0f))
        // A step of 2 yields a raw correction of 0.7 · 2³ = 5.6, clamped to the step itself.
        val smoothed = smoother.update(floatArrayOf(2f, 0f, 0f))
        assertThat(smoothed.x).isWithin(TOL).of(2.0)
    }

    @Test
    fun convergesToSteadyInput() {
        val smoother = ExponentiallyWeightedSmoother(alpha = 0.05f, exponent = 3)
        smoother.update(floatArrayOf(0f, 0f, 0f))
        var last = smoother.update(floatArrayOf(1f, 1f, 1f))
        // Cubic damping converges slowly near the target: ~1/√(αn) after n samples.
        repeat(5000) { last = smoother.update(floatArrayOf(1f, 1f, 1f)) }
        assertThat(last.x).isWithin(0.05).of(1.0)
        assertThat(last.y).isWithin(0.05).of(1.0)
        assertThat(last.z).isWithin(0.05).of(1.0)
    }

    companion object {
        private const val TOL = 1e-6
    }
}
