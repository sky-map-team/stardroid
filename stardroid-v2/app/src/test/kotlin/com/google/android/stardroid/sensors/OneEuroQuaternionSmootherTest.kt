/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.OneEuroEaseOff
import com.google.android.stardroid.settings.OneEuroSteadiness
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class OneEuroQuaternionSmootherTest {
    @Test
    fun `first sample passes through unsmoothed`() {
        val smoother = smoother()
        val first = smoother.update(rotationAboutZ(10.0), t(0))
        assertThat(first.toList()).isEqualTo(rotationAboutZ(10.0).toList())
    }

    @Test
    fun `converges toward a steady target`() {
        val smoother = smoother()
        smoother.update(IDENTITY, t(0))
        var last = IDENTITY
        repeat(200) { last = smoother.update(rotationAboutZ(10.0), t(it + 1)) }
        assertThat(angleBetweenDegrees(last, rotationAboutZ(10.0))).isLessThan(0.05)
    }

    @Test
    fun `damps jitter hard while the view is held still`() {
        val smoother = smoother()
        smoother.update(IDENTITY, t(0))
        // A half-degree wobble alternating either side of centre, as a shaky hand produces.
        var last = IDENTITY
        repeat(20) { last = smoother.update(rotationAboutZ(if (it % 2 == 0) 0.5 else -0.5), t(it + 1)) }
        assertThat(angleBetweenDegrees(last, IDENTITY)).isLessThan(0.2)
    }

    @Test
    fun `keeps up with sustained movement far better than with jitter`() {
        // The whole point of the adaptive cutoff: the same parameters that crush the wobble
        // above track a steady sweep closely. A flat low-pass cannot do both.
        val smoother = smoother()
        smoother.update(IDENTITY, t(0))
        var last = IDENTITY
        // 4 degrees per sample at 50 Hz — a brisk but ordinary pan.
        repeat(20) { last = smoother.update(rotationAboutZ(4.0 * (it + 1)), t(it + 1)) }
        val lagDegrees = 80.0 - angleBetweenDegrees(last, IDENTITY)
        assertThat(lagDegrees).isLessThan(8.0)
    }

    @Test
    fun `a higher beta lags a sweep less`() {
        fun lagAfterSweep(beta: OneEuroEaseOff): Double {
            val smoother =
                OneEuroQuaternionSmoother(
                    minCutoff = OneEuroQuaternionSmoother.minCutoffFor(OneEuroSteadiness.MEDIUM),
                    beta = OneEuroQuaternionSmoother.betaFor(beta),
                )
            smoother.update(IDENTITY, t(0))
            var last = IDENTITY
            repeat(20) { last = smoother.update(rotationAboutZ(4.0 * (it + 1)), t(it + 1)) }
            return 80.0 - angleBetweenDegrees(last, IDENTITY)
        }
        assertThat(lagAfterSweep(OneEuroEaseOff.HIGH)).isLessThan(lagAfterSweep(OneEuroEaseOff.LOW))
        assertThat(lagAfterSweep(OneEuroEaseOff.LOW)).isLessThan(lagAfterSweep(OneEuroEaseOff.NONE))
    }

    @Test
    fun `a sign-flipped sample of the same rotation causes no movement`() {
        val smoother = smoother()
        val q = rotationAboutZ(170.0)
        smoother.update(q, t(0))
        // -q is the same rotation as q; SLERPing toward it naively spins nearly all the way
        // around instead of recognizing the shorter path back.
        val result = smoother.update(FloatArray(4) { -q[it] }, t(1))
        assertThat(angleBetweenDegrees(result, q)).isLessThan(0.01)
    }

    @Test
    fun `restarts rather than catching up across a long gap`() {
        val smoother = smoother()
        smoother.update(IDENTITY, t(0))
        // Two seconds of nothing — the app was backgrounded. Resuming from the stale
        // orientation would drag the view across the sky; starting fresh costs one frame.
        val resumed = smoother.update(rotationAboutZ(90.0), t(0) + 2_000_000_000L)
        // Compared component-wise, not by angle: acos against two near-identical quaternions
        // sits right up at 1, where a single float ulp is worth a few hundredths of a degree.
        assertThat(resumed.toList()).isEqualTo(rotationAboutZ(90.0).toList())
    }

    @Test
    fun `a non-advancing timestamp does not produce a broken quaternion`() {
        val smoother = smoother()
        smoother.update(IDENTITY, t(5))
        val repeated = smoother.update(rotationAboutZ(3.0), t(5))
        val magnitude =
            kotlin.math.sqrt(
                repeated[0] * repeated[0] + repeated[1] * repeated[1] +
                    repeated[2] * repeated[2] + repeated[3] * repeated[3],
            )
        assertThat(magnitude.toDouble()).isWithin(1e-4).of(1.0)
    }

    @Test
    fun `ladders are ordered and beta starts at zero`() {
        assertThat(OneEuroQuaternionSmoother.betaFor(OneEuroEaseOff.NONE)).isEqualTo(0f)
        val betas = OneEuroEaseOff.entries.map { OneEuroQuaternionSmoother.betaFor(it) }
        betas.zipWithNext { lower, higher -> assertThat(lower).isLessThan(higher) }
        // Steadier means a lower cutoff, so this ladder descends as the rungs climb.
        val cutoffs = OneEuroSteadiness.entries.map { OneEuroQuaternionSmoother.minCutoffFor(it) }
        cutoffs.zipWithNext { looser, steadier -> assertThat(looser).isGreaterThan(steadier) }
    }

    private companion object {
        val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f)

        fun smoother() =
            OneEuroQuaternionSmoother(
                minCutoff = OneEuroQuaternionSmoother.minCutoffFor(OneEuroSteadiness.MEDIUM),
                beta = OneEuroQuaternionSmoother.betaFor(OneEuroEaseOff.MEDIUM),
            )

        /** Timestamp for sample [index] at the 50 Hz the sensors are registered at, in nanos. */
        fun t(index: Int): Long = 1_000_000_000L + index * 20_000_000L

        fun rotationAboutZ(degrees: Double): FloatArray {
            val halfAngle = Math.toRadians(degrees) / 2.0
            return floatArrayOf(0f, 0f, sin(halfAngle).toFloat(), cos(halfAngle).toFloat())
        }

        fun angleBetweenDegrees(
            a: FloatArray,
            b: FloatArray,
        ): Double {
            val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
            val clamped = kotlin.math.min(1f, kotlin.math.abs(dot))
            return Math.toDegrees(2.0 * kotlin.math.acos(clamped.toDouble()))
        }
    }
}
