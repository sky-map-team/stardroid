/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import android.hardware.SensorManager
import com.google.android.stardroid.settings.SensorDamping
import com.google.android.stardroid.settings.SensorSpeed
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The preference→parameter tables. Speed is pinned to v1 `SensorOrientationController`'s
 * values; damping is v1's ladder re-expressed for [QuaternionSlerpSmoother] (issue #1007), so
 * it's the ordering and the character of each rung that's pinned, not v1's raw constants. */
class SensorOrientationSourceTest {
    @Test
    fun `damping ladder damps sub-degree jitter progressively harder`() {
        val jitter = Math.toRadians(0.5).toFloat()
        val fractions = SensorDamping.entries.map { fractionAt(it, jitter) }
        fractions.zipWithNext { looser, tighter ->
            assertThat(looser).isGreaterThan(tighter)
        }
        // Even the least-damped rung meaningfully attenuates jitter of half a degree...
        assertThat(fractions.first()).isLessThan(0.5f)
        // ...and the most-damped one all but freezes it.
        assertThat(fractions.last()).isLessThan(0.01f)
    }

    @Test
    fun `damping ladder passes real movement through`() {
        // Twenty degrees between samples is unambiguous movement, not noise: every rung should
        // track it at close to full speed, which is what the exponent law buys over a flat
        // low-pass.
        val movement = Math.toRadians(20.0).toFloat()
        SensorDamping.entries.forEach {
            assertThat(fractionAt(it, movement)).isGreaterThan(0.9f)
        }
    }

    @Test
    fun `speed ladder matches v1's sensor delays`() {
        assertThat(SensorOrientationSource.sensorDelayFor(SensorSpeed.SLOW))
            .isEqualTo(SensorManager.SENSOR_DELAY_NORMAL)
        assertThat(SensorOrientationSource.sensorDelayFor(SensorSpeed.STANDARD))
            .isEqualTo(SensorManager.SENSOR_DELAY_GAME)
        assertThat(SensorOrientationSource.sensorDelayFor(SensorSpeed.FAST))
            .isEqualTo(SensorManager.SENSOR_DELAY_FASTEST)
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
