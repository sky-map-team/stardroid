/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.SensorDamping
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The damping ladder — v1's smoothing tables re-expressed for [QuaternionSlerpSmoother]
 * (issue #1007), so what's pinned here is the ordering and the character of each rung, not
 * v1's raw constants, which acted on different units entirely.
 */
class SensorOrientationSourceTest {
    @Test
    fun `damping ladder damps sub-degree jitter progressively harder`() {
        val jitter = Math.toRadians(0.5).toFloat()
        val fractions = SensorDamping.entries.map { fractionAt(it, jitter) }
        fractions.zipWithNext { looser, tighter ->
            assertThat(looser).isGreaterThan(tighter)
        }
        // Even the least-damped rung meaningfully attenuates jitter of half a degree...
        assertThat(fractions.first()).isLessThan(0.01f)
        // ...and the most-damped one all but freezes it.
        assertThat(fractions.last()).isLessThan(0.01f)
    }

    @Test
    fun `every rung responds far more to real movement than to jitter`() {
        // The point of the exponent law, and what a flat low-pass cannot do: the same rung
        // that all but freezes a half-degree wobble tracks a twenty-degree sweep readily. The
        // heaviest rung deliberately still lags a sweep somewhat — that's what it's for — so
        // this asserts the ratio rather than an absolute fraction.
        val jitter = Math.toRadians(0.5).toFloat()
        val movement = Math.toRadians(20.0).toFloat()
        SensorDamping.entries.forEach {
            assertThat(fractionAt(it, movement)).isGreaterThan(100 * fractionAt(it, jitter))
        }
    }

    private companion object {
        /** The SLERP fraction a rung yields for a sample [angle] radians away. */
        fun fractionAt(
            damping: SensorDamping,
            angle: Float,
        ): Float {
            val settings = SensorOrientationSource.dampingSettingsFor(damping)
            var fraction = settings.alpha
            repeat(settings.exponent - 1) { fraction *= angle }
            return minOf(1f, fraction)
        }
    }
}
