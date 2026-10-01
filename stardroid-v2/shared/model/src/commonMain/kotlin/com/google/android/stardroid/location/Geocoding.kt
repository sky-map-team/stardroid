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

        /**
         * This device has no geocoder backend (typically no Google Play Services, e.g.
         * de-Googled ROMs), so no lookup was attempted; coordinates must be typed.
         */
        data object NoBackend : PlaceResult

        /** The backend couldn't reach its service, typically because the device is offline. */
        data object NetworkError : PlaceResult

        /** The backend failed in some way other than the network; coordinates must be typed. */
        data object Failed : PlaceResult
    }

    /**
     * Whether place lookup can work at all on this device. False means there is no geocoder
     * backend, independent of connectivity, so the UI can say so up front.
     */
    fun isPlaceLookupAvailable(): Boolean

    /** Looks up coordinates for a typed place name. */
    suspend fun resolvePlace(name: String): PlaceResult

    /**
     * A short human name for a position (v1's toast preference order:
     * locality → sub-admin → admin → country), or null if the lookup fails.
     */
    suspend fun reverseGeocode(location: LatLong): String?
}
