/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.location.LocationProvider
import com.google.android.stardroid.math.LatLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * [LocationProvider] over Core Location, and the app's location authorization: the iOS edge
 * Android splits between `PlatformLocationProvider` and the activity's permission launcher.
 *
 * Kilometre accuracy, as Android asks only for the coarse permission: the sky doesn't move
 * visibly over a few kilometres. Main thread only, where Core Location calls its delegate (the
 * manager is created there) and where `LocationController` runs.
 */
@OptIn(ExperimentalForeignApi::class)
class CoreLocationProvider : LocationProvider {
    private val delegate = Delegate()
    private val manager =
        CLLocationManager().apply {
            desiredAccuracy = kCLLocationAccuracyKilometer
            delegate = this@CoreLocationProvider.delegate
        }

    private val _authorization = MutableStateFlow(manager.authorizationStatus)

    /** The app's location authorization, updated as the user answers or changes it. */
    val authorization: StateFlow<CLAuthorizationStatus> = _authorization.asStateFlow()

    val isAuthorized: Boolean
        get() = _authorization.value.isAuthorized()

    /** [isAuthorized] as it changes. */
    val authorized: Flow<Boolean> = authorization.map { it.isAuthorized() }

    /** Whether the user has yet to be asked; iOS asks once and never again. */
    val canRequestAuthorization: Boolean
        get() = _authorization.value == kCLAuthorizationStatusNotDetermined

    private var onUpdate: ((LatLong, Float?) -> Unit)? = null

    /** Shows the system prompt if the user has not been asked, and returns the answer. */
    suspend fun requestAuthorization(): Boolean {
        if (canRequestAuthorization) manager.requestWhenInUseAuthorization()
        return authorization.first { it != kCLAuthorizationStatusNotDetermined }.isAuthorized()
    }

    override fun startUpdates(
        minDistanceMetres: Float,
        onUpdate: (location: LatLong, accuracyM: Float?) -> Unit,
    ) {
        this.onUpdate = onUpdate
        manager.distanceFilter = minDistanceMetres.toDouble()
        manager.startUpdatingLocation()
    }

    override fun stopUpdates() {
        manager.stopUpdatingLocation()
        onUpdate = null
    }

    // Every iPhone has location hardware. Location services switched off system-wide surface as
    // the acquiring timeout: CLLocationManager.locationServicesEnabled() blocks the main thread.
    override fun isAvailable() = true

    private fun deliver(location: CLLocation) {
        val update = onUpdate ?: return
        val position =
            location.coordinate.useContents { LatLong(latitude, longitude) }
        // A negative accuracy marks the fix invalid; Core Location never delivers those here.
        update(position, location.horizontalAccuracy.takeIf { it >= 0 }?.toFloat())
    }

    private inner class Delegate :
        NSObject(),
        CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            _authorization.value = manager.authorizationStatus
        }

        override fun locationManager(
            manager: CLLocationManager,
            didUpdateLocations: List<*>,
        ) {
            (didUpdateLocations.lastOrNull() as? CLLocation)?.let(::deliver)
        }

        // Transient failures (no fix yet) are retried by Core Location; a lasting one leaves
        // LocationController to time out, as a silent Android provider does.
        override fun locationManager(
            manager: CLLocationManager,
            didFailWithError: NSError,
        ) = Unit
    }
}

private fun CLAuthorizationStatus.isAuthorized() =
    this == kCLAuthorizationStatusAuthorizedWhenInUse ||
        this == kCLAuthorizationStatusAuthorizedAlways
