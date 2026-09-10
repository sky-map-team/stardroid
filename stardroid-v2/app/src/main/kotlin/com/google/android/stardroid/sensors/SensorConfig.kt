/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.OneEuroBeta
import com.google.android.stardroid.settings.OneEuroMinCutoff

/**
 * The sensor-preference snapshot `SensorOrientationSource` runs under (v1's `disable_gyro` and
 * `reverse_magnetic_z`, plus v2's own 1€ filter parameters). The magnetic-Z reversal only
 * affects the classic accelerometer+magnetometer path — v1 fed the fused rotation-vector sensor
 * no raw magnetometer data, and neither does v2.
 *
 * [minCutoff]/[beta] apply to *both* paths, which is new: the fused path used to have its own
 * low-pass and deadband levels and the legacy path its own damping ladder (issues #963 / #1001 /
 * #1007). One filter now serves both, so one pair of knobs does too. They stay separate rather
 * than combining into a single "smoothing" level while their useful range is still being found
 * in the field.
 */
data class SensorConfig(
    val disableGyro: Boolean = false,
    val reverseMagneticZ: Boolean = false,
    val minCutoff: OneEuroMinCutoff = OneEuroMinCutoff.MEDIUM,
    val beta: OneEuroBeta = OneEuroBeta.MEDIUM,
)
