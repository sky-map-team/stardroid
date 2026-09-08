/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.RotationSmoothingLevel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class QuaternionSlerpSmootherTest {
    @Test
    fun `first sample passes through unsmoothed`() {
        val smoother = QuaternionSlerpSmoother(alpha = 0.3f, deadbandRadians = 0f)
        val first = smoother.update(rotationAboutZ(10.0))
        assertThat(first.toList()).isEqualTo(rotationAboutZ(10.0).toList())
    }

    @Test
    fun `converges toward a steady target`() {
        val smoother = QuaternionSlerpSmoother(alpha = 0.3f, deadbandRadians = 0f)
        val target = rotationAboutZ(10.0)
        smoother.update(IDENTITY)
        var last = IDENTITY
        repeat(50) { last = smoother.update(target) }
        assertThat(angleBetweenDegrees(last, target)).isLessThan(0.01)
    }

    @Test
    fun `deadband holds steady through micro-movements`() {
        val smoother =
            QuaternionSlerpSmoother(alpha = 0.5f, deadbandRadians = Math.toRadians(0.5).toFloat())
        smoother.update(IDENTITY)
        // A 0.1 degree wobble is well under the 0.5 degree deadband.
        val held = smoother.update(rotationAboutZ(0.1))
        assertThat(held.toList()).isEqualTo(IDENTITY.toList())
    }

    @Test
    fun `movement past the deadband is not suppressed`() {
        val smoother =
            QuaternionSlerpSmoother(alpha = 0.5f, deadbandRadians = Math.toRadians(0.5).toFloat())
        smoother.update(IDENTITY)
        val moved = smoother.update(rotationAboutZ(5.0))
        assertThat(angleBetweenDegrees(moved, IDENTITY)).isGreaterThan(0.01)
    }

    @Test
    fun `output stays a unit quaternion`() {
        val smoother = QuaternionSlerpSmoother(alpha = 0.3f, deadbandRadians = 0f)
        smoother.update(IDENTITY)
        val smoothed = smoother.update(rotationAboutZ(37.0))
        val magnitude =
            kotlin.math.sqrt(
                smoothed[0] * smoothed[0] + smoothed[1] * smoothed[1] +
                    smoothed[2] * smoothed[2] + smoothed[3] * smoothed[3],
            )
        assertThat(magnitude.toDouble()).isWithin(1e-4).of(1.0)
    }

    @Test
    fun `a sign-flipped sample of the same rotation causes no movement`() {
        val smoother = QuaternionSlerpSmoother(alpha = 0.5f, deadbandRadians = 0f)
        val q = rotationAboutZ(170.0)
        smoother.update(q)
        // -q represents the exact same rotation as q; naively SLERPing toward it (instead of
        // recognizing the shorter path back to q) would spin the view nearly all the way around.
        val negated = FloatArray(4) { -q[it] }
        val result = smoother.update(negated)
        assertThat(angleBetweenDegrees(result, q)).isLessThan(0.01)
    }

    @Test
    fun `low-pass ladder is off by default and strictly decreasing in alpha`() {
        assertThat(QuaternionSlerpSmoother.alphaFor(RotationSmoothingLevel.OFF)).isEqualTo(1f)

        val low = QuaternionSlerpSmoother.alphaFor(RotationSmoothingLevel.LOW)
        val medium = QuaternionSlerpSmoother.alphaFor(RotationSmoothingLevel.MEDIUM)
        val high = QuaternionSlerpSmoother.alphaFor(RotationSmoothingLevel.HIGH)
        assertThat(low).isGreaterThan(medium)
        assertThat(medium).isGreaterThan(high)
    }

    @Test
    fun `deadband ladder is off by default and strictly increasing`() {
        assertThat(QuaternionSlerpSmoother.deadbandRadiansFor(RotationSmoothingLevel.OFF))
            .isEqualTo(0f)

        val low = QuaternionSlerpSmoother.deadbandRadiansFor(RotationSmoothingLevel.LOW)
        val medium = QuaternionSlerpSmoother.deadbandRadiansFor(RotationSmoothingLevel.MEDIUM)
        val high = QuaternionSlerpSmoother.deadbandRadiansFor(RotationSmoothingLevel.HIGH)
        assertThat(low).isLessThan(medium)
        assertThat(medium).isLessThan(high)
    }

    companion object {
        private val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f)

        private fun rotationAboutZ(degrees: Double): FloatArray {
            val halfAngle = Math.toRadians(degrees) / 2.0
            return floatArrayOf(0f, 0f, sin(halfAngle).toFloat(), cos(halfAngle).toFloat())
        }

        private fun angleBetweenDegrees(
            a: FloatArray,
            b: FloatArray,
        ): Double {
            val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
            val clamped = kotlin.math.min(1f, kotlin.math.abs(dot))
            return Math.toDegrees(2.0 * kotlin.math.acos(clamped.toDouble()))
        }
    }
}
