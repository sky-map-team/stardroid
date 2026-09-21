/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import com.google.android.stardroid.math.LatLong

/**
 * [LocationProvider] on the platform `LocationManager` — v1's fdroid implementation. Listens
 * on every enabled provider; with only the coarse permission the GPS provider throws
 * [SecurityException], which is caught per provider so the network provider still registers
 * (the same per-provider guard the last-known lookup needs).
 *
 * Every start also seeds from the OS's cached fixes ([seedFromLastKnown]), so a position some
 * other app already obtained arrives at once — on a device with no network location backend
 * that may be the only fix we ever get.
 */
class PlatformLocationProvider(
    private val context: Context,
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
        for (provider in PROVIDERS) {
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
        seedFromLastKnown(manager, onUpdate)
    }

    /**
     * Delivers the freshest cached fix (see [lastKnownFixes]). Synchronous, so it lands before
     * any live callback, which the main looper only delivers afterwards.
     */
    private fun seedFromLastKnown(
        manager: LocationManager,
        onUpdate: (LatLong, Float?) -> Unit,
    ) {
        freshestFix(lastKnownFixes(manager))?.let { onUpdate(it.location, it.accuracyM) }
    }

    /**
     * The OS's cached fix from each of the GPS and network providers plus the passive provider
     * (which holds whatever any app's request last produced). Each provider is guarded
     * separately: which of them the held permission allows varies by device and OS version.
     */
    @SuppressLint("MissingPermission")
    private fun lastKnownFixes(manager: LocationManager): List<CachedFix> {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        return (PROVIDERS + LocationManager.PASSIVE_PROVIDER).mapNotNull { provider ->
            val location =
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return@mapNotNull null
            CachedFix(
                location = LatLong(location.latitude, location.longitude),
                accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                ageMillis = (nowNanos - location.elapsedRealtimeNanos) / NANOS_PER_MILLI,
            )
        }
    }

    override fun stopUpdates() {
        val manager = locationManager ?: return
        activeListeners.forEach(manager::removeUpdates)
        activeListeners.clear()
    }

    /**
     * Whether we can get a location at all: a provider we are permitted to listen on is
     * enabled, or the OS already holds a fix to seed from. An enabled GPS provider doesn't
     * count without the fine permission (we normally hold only coarse), since registering on
     * it just throws — reporting it available would leave the user "acquiring" until the
     * timeout when we can never receive a fix.
     */
    @SuppressLint("MissingPermission")
    override fun isAvailable(): Boolean {
        val manager = locationManager ?: return false
        return try {
            canProvideLocation(
                networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER),
                gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER),
                fineLocationGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED,
                hasCachedFix = { lastKnownFixes(manager).isNotEmpty() },
            )
        } catch (_: Exception) {
            // OEM ROMs have been seen throwing more than IllegalArgumentException here.
            false
        }
    }

    private companion object {
        const val TAG = "PlatformLocationProvider"
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * The availability rule behind [PlatformLocationProvider.isAvailable], pure so it is testable.
 * [hasCachedFix] is only consulted when no live provider is usable.
 */
internal fun canProvideLocation(
    networkEnabled: Boolean,
    gpsEnabled: Boolean,
    fineLocationGranted: Boolean,
    hasCachedFix: () -> Boolean,
): Boolean = networkEnabled || (gpsEnabled && fineLocationGranted) || hasCachedFix()
