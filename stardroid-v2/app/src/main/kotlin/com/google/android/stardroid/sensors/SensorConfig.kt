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
import com.google.android.stardroid.settings.SensorSpeed

/**
 * The sensor-preference snapshot `SensorOrientationSource` runs under (v1's `disable_gyro`,
 * `sensor_speed`, `sensor_damping`, `reverse_magnetic_z`). Speed, damping, and the magnetic-Z
 * reversal only affect the classic accelerometer+magnetometer path — v1 ran the fused
 * rotation-vector sensor at a fixed rate and fed it no raw magnetometer data, and so does v2.
 */
data class SensorConfig(
    val disableGyro: Boolean = false,
    val speed: SensorSpeed = SensorSpeed.STANDARD,
    val damping: SensorDamping = SensorDamping.EXTRA_HIGH,
    val reverseMagneticZ: Boolean = false,
)
