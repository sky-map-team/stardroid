/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.math.Matrix3
import kotlinx.coroutines.flow.Flow

/**
 * The phone-orientation edge: a stream of phone→world rotation matrices in Android's
 * rotation-matrix convention (rows are world East, magnetic North, Up in phone coordinates),
 * ready for `SkyModel.pointing`.
 *
 * An interface so ViewModels and the test activity can be driven by fakes; the real
 * implementation is [SensorOrientationSource].
 */
interface OrientationSource {
    /** False on devices with neither a rotation-vector sensor nor an accelerometer+magnetometer
     * pair — callers fall back to manual mode (v1's no-sensor warning). */
    val available: Boolean

    /**
     * Orientation samples while collected; registers the underlying sensor listeners on
     * collection and unregisters them on cancellation. Emits nothing when [available] is false.
     */
    fun orientations(): Flow<Matrix3>
}
