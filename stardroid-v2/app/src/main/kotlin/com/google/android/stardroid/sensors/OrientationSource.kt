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
import kotlinx.coroutines.flow.map

/**
 * One orientation update in both forms: [raw] straight off the sensor fusion, before smoothing,
 * and [smoothed] after — the same pair [SensorOrientationSource] already holds at its one call
 * to the 1€ filter, exposed rather than discarded so a consumer (diagnostics) can measure what
 * the filter is doing without a second smoother or a second sensor registration.
 */
data class OrientationSample(val raw: Matrix3, val smoothed: Matrix3)

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

    /**
     * The same stream as [orientations], with the pre-smoothing value alongside each sample.
     * Collecting this registers its own sensor listeners independent of [orientations] — a
     * second collector (diagnostics, say) pays for a second registration rather than sharing
     * the map's. Fakes that only implement [orientations] get raw == smoothed here, which is
     * indistinguishable from a real device with smoothing disabled.
     */
    fun orientationSamples(): Flow<OrientationSample> =
        orientations().map { OrientationSample(it, it) }
}
