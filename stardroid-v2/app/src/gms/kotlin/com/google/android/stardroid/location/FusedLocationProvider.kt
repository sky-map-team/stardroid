/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.stardroid.math.LatLong

/**
 * [LocationProvider] over Play Services' fused provider — v1's gms `FusedLocationProvider`.
 * Interval 0 means "as fast as possible", tempered by the minimum-distance requirement.
 */
class FusedLocationProvider(
    private val client: FusedLocationProviderClient,
) : LocationProvider {
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun startUpdates(
        minDistanceMetres: Float,
        onUpdate: (location: LatLong, accuracyM: Float?) -> Unit,
    ) {
        stopUpdates()
        val request =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 0L)
                .setMinUpdateDistanceMeters(minDistanceMetres)
                .build()
        val cb =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    onUpdate(
                        LatLong(location.latitude, location.longitude),
                        if (location.hasAccuracy()) location.accuracy else null,
                    )
                }
            }
        callback = cb
        try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // The permission may have been revoked between the caller's check and this call.
        }
    }

    override fun stopUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    override fun isAvailable(): Boolean = true
}
