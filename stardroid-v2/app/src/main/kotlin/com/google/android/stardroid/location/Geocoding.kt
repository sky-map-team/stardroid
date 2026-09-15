/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import android.content.Context
import android.location.Geocoder
import com.google.android.stardroid.math.LatLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The geocoder edge behind the manual-entry dialog and the location toast, so
 * `LocationViewModel` stays JVM-testable. Both operations are best-effort network lookups —
 * v1 ran them on its background executor; here they suspend on IO.
 */
interface Geocoding {
    /** The outcome of resolving a typed place name. */
    sealed interface PlaceResult {
        data class Found(
            val location: LatLong,
        ) : PlaceResult

        /** The geocoder answered, but knows no such place. */
        data object NotFound : PlaceResult

        /** No geocoder on this device or no network; coordinates must be typed. */
        data object Unavailable : PlaceResult
    }

    /** Looks up coordinates for a typed place name. */
    suspend fun resolvePlace(name: String): PlaceResult

    /**
     * A short human name for a position (v1's toast preference order:
     * locality → sub-admin → admin → country), or null if the lookup fails.
     */
    suspend fun reverseGeocode(location: LatLong): String?
}

/** [Geocoding] on the platform [Geocoder]. */
class AndroidGeocoding(
    private val context: Context,
) : Geocoding {
    override suspend fun resolvePlace(name: String): Geocoding.PlaceResult {
        if (!Geocoder.isPresent()) return Geocoding.PlaceResult.Unavailable
        return withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context).getFromLocationName(name, 1)
                val first = results?.firstOrNull()
                if (first == null) {
                    Geocoding.PlaceResult.NotFound
                } else {
                    Geocoding.PlaceResult.Found(LatLong(first.latitude, first.longitude))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Broken geocoder services on some ROMs throw more than IOException.
                Geocoding.PlaceResult.Unavailable
            }
        }
    }

    override suspend fun reverseGeocode(location: LatLong): String? {
        if (!Geocoder.isPresent()) return null
        return withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitudeDeg, location.longitudeDeg, 1)
                    ?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
}
