/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.control

import com.google.android.stardroid.math.LatLong

/**
 * Interface for components that provide location updates.
 */
interface LocationProvider {
    /**
     * Starts requesting location updates.
     *
     * @param minDistanceMetres the minimum distance in metres between updates.
     * @param onUpdate a callback to be invoked when a new location is available.
     * The callback receives:
     * - `location`: the new [LatLong] position.
     * - `accuracy`: the estimated horizontal accuracy in metres, or null if unknown.
     */
    fun startUpdates(minDistanceMetres: Float, onUpdate: (location: LatLong, accuracy: Float?) -> Unit)

    /**
     * Stops requesting location updates.
     */
    fun stopUpdates()

    /**
     * Returns true if this provider is available on the current device.
     */
    fun isAvailable(): Boolean
}
