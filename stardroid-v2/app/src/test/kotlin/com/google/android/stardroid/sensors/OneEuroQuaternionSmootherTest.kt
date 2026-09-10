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
    fun `noise on a stationary signal must not open the cutoff`() {
        // The legacy accelerometer+magnetometer path feeds in raw sensor noise scattered in
        // every direction. Keying the cutoff off the *size* of each sample's rotation reads
        // that as fast movement — a size is always positive, so it never averages out — which
        // opens the cutoff and passes the noise straight through. Steering by a low-passed
        // velocity *vector* lets it cancel, so a stationary phone should filter essentially as
        // hard with ease-off engaged as with it switched off. Asserted as a ratio because
        // that's the property; the absolute jitter depends on the noise amplitude.
        fun worstDeviation(easeOff: OneEuroEaseOff): Double {
            val smoother =
                OneEuroQuaternionSmoother(
                    minCutoff = OneEuroQuaternionSmoother.minCutoffFor(OneEuroSteadiness.MEDIUM, legacyPath = false),
                    beta = OneEuroQuaternionSmoother.betaFor(easeOff, legacyPath = false),
                )
            smoother.update(IDENTITY, t(0))
            var worst = 0.0
            repeat(300) {
                val out = smoother.update(noiseAbout(IDENTITY, it), t(it + 1))
                worst = maxOf(worst, angleBetweenDegrees(out, IDENTITY))
            }
            return worst
        }
        val withEaseOff = worstDeviation(OneEuroEaseOff.HIGH)
        val withoutEaseOff = worstDeviation(OneEuroEaseOff.NONE)
        // Measured at 1.4x with the velocity vector and 4.0x when keyed off the unsigned
        // angle, so 2x separates them with room either side. Not 1x: some residual coupling
        // is inherent, since noise leaves a little velocity behind however it's estimated.
        assertThat(withEaseOff).isLessThan(withoutEaseOff * 2.0)
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
                    minCutoff = OneEuroQuaternionSmoother.minCutoffFor(OneEuroSteadiness.MEDIUM, legacyPath = false),
                    beta = OneEuroQuaternionSmoother.betaFor(beta, legacyPath = false),
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
    fun `the legacy tables are gentler than the fused ones at every rung`() {
        // The legacy path's noise floor is far coarser, so both its tables sit lower. Beta
        // especially: `beta * speed` is added to the cutoff, so a beta sized for the fused
        // sensor swamps the minimum on the legacy path and makes steadiness do nothing.
        OneEuroSteadiness.entries.forEach {
            assertThat(OneEuroQuaternionSmoother.minCutoffFor(it, legacyPath = true))
                .isLessThan(OneEuroQuaternionSmoother.minCutoffFor(it, legacyPath = false))
        }
        OneEuroEaseOff.entries.filter { it != OneEuroEaseOff.NONE }.forEach {
            assertThat(OneEuroQuaternionSmoother.betaFor(it, legacyPath = true))
                .isLessThan(OneEuroQuaternionSmoother.betaFor(it, legacyPath = false))
        }
    }

    @Test
    fun `ladders are ordered and beta starts at zero`() {
        assertThat(OneEuroQuaternionSmoother.betaFor(OneEuroEaseOff.NONE, legacyPath = false)).isEqualTo(0f)
        val betas = OneEuroEaseOff.entries.map { OneEuroQuaternionSmoother.betaFor(it, legacyPath = false) }
        betas.zipWithNext { lower, higher -> assertThat(lower).isLessThan(higher) }
        // Steadier means a lower cutoff, so this ladder descends as the rungs climb.
        val cutoffs = OneEuroSteadiness.entries.map { OneEuroQuaternionSmoother.minCutoffFor(it, legacyPath = false) }
        cutoffs.zipWithNext { looser, steadier -> assertThat(looser).isGreaterThan(steadier) }
    }

    private companion object {
        val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f)

        fun smoother() =
            OneEuroQuaternionSmoother(
                minCutoff = OneEuroQuaternionSmoother.minCutoffFor(OneEuroSteadiness.MEDIUM, legacyPath = false),
                beta = OneEuroQuaternionSmoother.betaFor(OneEuroEaseOff.MEDIUM, legacyPath = false),
            )

        /** Timestamp for sample [index] at the 50 Hz the sensors are registered at, in nanos. */
        fun t(index: Int): Long = 1_000_000_000L + index * 20_000_000L

        /**
         * [about], displaced by roughly a degree in a direction that varies with [step] — a
         * stand-in for raw sensor noise, deterministic so the test can't flake.
         */
        fun noiseAbout(
            about: FloatArray,
            step: Int,
        ): FloatArray {
            val half = Math.toRadians(2.0) / 2.0
            val phase = step * 2.399963
            val axis =
                doubleArrayOf(cos(phase), sin(phase), cos(phase * 0.5))
                    .let { a ->
                        val len = kotlin.math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
                        doubleArrayOf(a[0] / len, a[1] / len, a[2] / len)
                    }
            val n =
                floatArrayOf(
                    (axis[0] * sin(half)).toFloat(),
                    (axis[1] * sin(half)).toFloat(),
                    (axis[2] * sin(half)).toFloat(),
                    cos(half).toFloat(),
                )
            // n ⊗ about
            return floatArrayOf(
                n[3] * about[0] + n[0] * about[3] + n[1] * about[2] - n[2] * about[1],
                n[3] * about[1] - n[0] * about[2] + n[1] * about[3] + n[2] * about[0],
                n[3] * about[2] + n[0] * about[1] - n[1] * about[0] + n[2] * about[3],
                n[3] * about[3] - n[0] * about[0] - n[1] * about[1] - n[2] * about[2],
            )
        }

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
