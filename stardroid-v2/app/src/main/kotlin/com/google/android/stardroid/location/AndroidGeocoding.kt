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
import java.io.IOException
import java.util.Locale

/** [Geocoding] on the platform [Geocoder]. */
class AndroidGeocoding(
    private val context: Context,
) : Geocoding {
    override fun isPlaceLookupAvailable(): Boolean = Geocoder.isPresent()

    override suspend fun resolvePlace(name: String): Geocoding.PlaceResult {
        if (!isPlaceLookupAvailable()) return Geocoding.PlaceResult.NoBackend
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
            } catch (_: IOException) {
                Geocoding.PlaceResult.NetworkError
            } catch (_: Exception) {
                // Broken geocoder services on some ROMs throw more than IOException; that
                // isn't evidence of a network problem, so don't blame the user's connection.
                Geocoding.PlaceResult.Failed
            }
        }
    }

    override suspend fun reverseGeocode(location: LatLong): String? {
        if (!isPlaceLookupAvailable()) return null
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
