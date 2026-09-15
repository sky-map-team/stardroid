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
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.location.LocationListenerCompat
import com.google.android.stardroid.math.LatLong

/**
 * [LocationProvider] on the platform `LocationManager` — v1's fdroid implementation. Listens
 * on every enabled provider; with only the coarse permission the GPS provider throws
 * [SecurityException], which is caught per provider so the network provider still registers
 * (the same per-provider guard slice 5's last-known lookup needed).
 */
class PlatformLocationProvider(
    context: Context,
) : LocationProvider {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val activeListeners = mutableListOf<LocationListenerCompat>()

    // Callers hold the permission before starting updates (LocationController's state machine
    // guards every path here); lint can't see through the indirection.
    @SuppressLint("MissingPermission")
    override fun startUpdates(
        minDistanceMetres: Float,
        onUpdate: (LatLong, Float?) -> Unit,
    ) {
        stopUpdates()
        val manager = locationManager ?: return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            // Compat listener: on API 29 the platform interface lacks default methods, so a
            // bare lambda crashes with AbstractMethodError when a provider toggles.
            val listener =
                LocationListenerCompat { location: Location ->
                    val accuracy = if (location.hasAccuracy()) location.accuracy else null
                    onUpdate(LatLong(location.latitude, location.longitude), accuracy)
                }
            try {
                if (!manager.isProviderEnabled(provider)) continue
                manager.requestLocationUpdates(
                    provider,
                    0L,
                    minDistanceMetres,
                    listener,
                    Looper.getMainLooper(),
                )
                activeListeners.add(listener)
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Provider $provider not supported on this device")
            } catch (_: SecurityException) {
                // Coarse-only permission: the GPS provider needs fine location.
                Log.w(TAG, "Provider $provider needs a stronger permission; skipping")
            }
        }
    }

    override fun stopUpdates() {
        val manager = locationManager ?: return
        activeListeners.forEach(manager::removeUpdates)
        activeListeners.clear()
    }

    override fun isAvailable(): Boolean {
        val manager = locationManager ?: return false
        return try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            // OEM ROMs have been seen throwing more than IllegalArgumentException here.
            false
        }
    }

    private companion object {
        const val TAG = "PlatformLocationProvider"
    }
}
