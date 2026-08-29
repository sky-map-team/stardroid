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

/** The preference→parameter tables, pinned to v1 `SensorOrientationController`'s values. */
class SensorOrientationSourceTest {
    @Test
    fun `damping ladder matches v1's smoothing tables`() {
        val standard = SensorOrientationSource.dampingSettingsFor(SensorDamping.STANDARD)
        assertThat(standard.first).isEqualTo(SensorOrientationSource.DampingSettings(0.7f, 3))
        assertThat(standard.second).isEqualTo(SensorOrientationSource.DampingSettings(0.05f, 3))

        val high = SensorOrientationSource.dampingSettingsFor(SensorDamping.HIGH)
        assertThat(high.first).isEqualTo(SensorOrientationSource.DampingSettings(0.7f, 3))
        assertThat(high.second).isEqualTo(SensorOrientationSource.DampingSettings(0.001f, 4))

        val extraHigh = SensorOrientationSource.dampingSettingsFor(SensorDamping.EXTRA_HIGH)
        assertThat(extraHigh.first).isEqualTo(SensorOrientationSource.DampingSettings(0.1f, 3))
        assertThat(extraHigh.second)
            .isEqualTo(SensorOrientationSource.DampingSettings(0.0001f, 5))

        val reallyHigh = SensorOrientationSource.dampingSettingsFor(SensorDamping.REALLY_HIGH)
        assertThat(reallyHigh.first).isEqualTo(SensorOrientationSource.DampingSettings(0.1f, 3))
        assertThat(reallyHigh.second)
            .isEqualTo(SensorOrientationSource.DampingSettings(0.000001f, 5))
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
}
