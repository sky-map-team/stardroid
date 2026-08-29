/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import com.google.android.stardroid.math.LatLong

/**
 * The device-location edge, v1's `LocationProvider` shape: the gms build binds Play Services'
 * fused provider (`FusedLocationProvider`, falling back to [PlatformLocationProvider] when
 * Play Services is unavailable), the fdroid build the platform `LocationManager`
 * (D43, D49).
 */
interface LocationProvider {
    /**
     * Starts location updates. [onUpdate] receives each fix and its horizontal accuracy in
     * metres (null if unknown); the OS filters updates to at least [minDistanceMetres] apart.
     */
    fun startUpdates(
        minDistanceMetres: Float,
        onUpdate: (location: LatLong, accuracyM: Float?) -> Unit,
    )

    /** Stops location updates; idempotent. */
    fun stopUpdates()

    /** Whether any location provider is enabled on this device. */
    fun isAvailable(): Boolean
}
