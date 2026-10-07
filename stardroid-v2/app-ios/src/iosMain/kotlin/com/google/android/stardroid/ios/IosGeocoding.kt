/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.location.Geocoding
import com.google.android.stardroid.math.LatLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocodeCompletionHandler
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import platform.CoreLocation.kCLErrorGeocodeFoundNoResult
import platform.CoreLocation.kCLErrorNetwork
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [Geocoding] on Core Location's `CLGeocoder`, Android's `AndroidGeocoding`. Apple's geocoder is
 * always there (it is a network service, so being offline shows as a network error), and it
 * answers in the user's language.
 */
@OptIn(ExperimentalForeignApi::class)
class IosGeocoding : Geocoding {
    override fun isPlaceLookupAvailable() = true

    override suspend fun resolvePlace(name: String): Geocoding.PlaceResult =
        try {
            placemarks { geocodeAddressString(name, it) }
                .firstNotNullOfOrNull { it.location }
                ?.coordinate
                ?.useContents { Geocoding.PlaceResult.Found(LatLong(latitude, longitude)) }
                ?: Geocoding.PlaceResult.NotFound
        } catch (e: GeocodeError) {
            // Android's Log.e; stdout reaches the device console (devicectl --console).
            println("IosGeocoding: resolvePlace failed: ${e.message}")
            when (e.code) {
                kCLErrorGeocodeFoundNoResult -> Geocoding.PlaceResult.NotFound
                kCLErrorNetwork -> Geocoding.PlaceResult.NetworkError
                else -> Geocoding.PlaceResult.Failed
            }
        }

    override suspend fun reverseGeocode(location: LatLong): String? =
        try {
            val position = CLLocation(location.latitudeDeg, location.longitudeDeg)
            placemarks { reverseGeocodeLocation(position, it) }
                .firstOrNull()
                ?.let {
                    it.locality ?: it.subAdministrativeArea ?: it.administrativeArea ?: it.country
                }
        } catch (e: GeocodeError) {
            println("IosGeocoding: reverseGeocode failed: ${e.message}")
            null
        }

    /** Runs one request on a geocoder of its own, which is cancelled with the caller. */
    private suspend fun placemarks(
        request: CLGeocoder.(CLGeocodeCompletionHandler) -> Unit,
    ): List<CLPlacemark> =
        suspendCancellableCoroutine { continuation ->
            val geocoder = CLGeocoder()
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.request { placemarks, error ->
                // A cancelled continuation ignores the cancelled request's answer.
                if (error != null) {
                    continuation.resumeWithException(
                        GeocodeError(
                            error.code,
                            "${error.domain} ${error.code}: ${error.localizedDescription}",
                        ),
                    )
                } else {
                    continuation.resume(placemarks.orEmpty().filterIsInstance<CLPlacemark>())
                }
            }
        }

    private class GeocodeError(
        val code: Long,
        message: String,
    ) : Exception(message)
}
