/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid

import android.app.Application
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.FirebaseAnalyticsAdapter
import com.google.android.stardroid.location.FusedLocationProvider
import com.google.android.stardroid.location.LocationProvider
import com.google.android.stardroid.location.PlatformLocationProvider
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.RemoteConfigExperimentConfig

/**
 * The gms edge set (D49): Firebase Analytics, Firebase Remote Config, and the fused
 * location provider. the Hilt `AppModule` providers call this object; the fdroid source set supplies the
 * open-source counterpart under the same name.
 */
object FlavorEdges {
    /** v1 gms parity: analytics is opt-out (`analytics_enabled` resValue "true"). */
    const val ENABLE_ANALYTICS_DEFAULT = true

    /**
     * Fetching public orbital data is unremarkable for users already running a build that
     * talks to Firebase, so satellite data defaults on here (D92).
     */
    const val SATELLITE_DATA_DEFAULT = true

    /** Label shown on the Diagnostics screen to identify this build flavor. */
    const val FLAVOR_LABEL = "Gms"

    fun analytics(application: Application): Analytics = FirebaseAnalyticsAdapter(application)

    fun experimentConfig(application: Application): ExperimentConfig =
        RemoteConfigExperimentConfig()

    /**
     * The fused provider when Play Services is up, else the platform fallback — v1 instead
     * prompted the user to fix Play Services (`GooglePlayServicesChecker`); falling back
     * keeps location working without the dialog (D49).
     */
    fun locationProvider(application: Application): LocationProvider =
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(application) ==
            ConnectionResult.SUCCESS
        ) {
            FusedLocationProvider(LocationServices.getFusedLocationProviderClient(application))
        } else {
            PlatformLocationProvider(application)
        }
}
