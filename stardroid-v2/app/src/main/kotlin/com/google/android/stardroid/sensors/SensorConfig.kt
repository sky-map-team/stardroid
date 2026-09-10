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
import com.google.android.stardroid.settings.SensorDamping

/**
 * The sensor-preference snapshot `SensorOrientationSource` runs under (v1's `disable_gyro`,
 * `sensor_damping`, `reverse_magnetic_z`, plus v2's own `rotation_low_pass` and
 * `rotation_deadband`). Damping and the magnetic-Z reversal only affect the classic
 * accelerometer+magnetometer path — v1 ran the fused rotation-vector sensor at a fixed rate and
 * fed it no raw magnetometer data, and so does v2. Both paths now sample at a fixed
 * `SENSOR_DELAY_GAME`; v1's user-selectable `sensor_speed` is gone (issue #1007).
 * [rotationLowPass]/[rotationDeadband] are the opposite: they only affect the fused path (issues #963 / #1001 — some phones' fusion is noisy
 * enough to need it), and are kept independent so their effects can be evaluated separately.
 */
data class SensorConfig(
    val disableGyro: Boolean = false,
    val damping: SensorDamping = SensorDamping.EXTRA_HIGH,
    val reverseMagneticZ: Boolean = false,
    val rotationLowPass: RotationSmoothingLevel = RotationSmoothingLevel.OFF,
    val rotationDeadband: RotationSmoothingLevel = RotationSmoothingLevel.OFF,
)
