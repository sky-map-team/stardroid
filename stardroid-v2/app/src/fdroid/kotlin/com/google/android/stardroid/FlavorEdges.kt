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
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.location.LocationProvider
import com.google.android.stardroid.location.PlatformLocationProvider
import com.google.android.stardroid.startup.ExperimentConfig

/**
 * The fdroid edge set (D49): no Google dependencies — analytics is a no-op, experiments
 * are the shipped static defaults, location is the platform `LocationManager`. The gms
 * source set supplies the Play Services counterpart under the same name.
 */
object FlavorEdges {
    /** v1 fdroid parity: analytics defaults off (and the edge is a no-op regardless). */
    const val ENABLE_ANALYTICS_DEFAULT = false

    /**
     * F-Droid flags network access in its anti-features metadata and that audience expects
     * opt-in, so satellite data defaults off here (D92).
     */
    const val SATELLITE_DATA_DEFAULT = false

    /** Label shown on the Diagnostics screen to identify this build flavor. */
    const val FLAVOR_LABEL = "F-Droid"

    fun analytics(application: Application): Analytics = NoOpAnalytics

    fun experimentConfig(application: Application): ExperimentConfig = ExperimentConfig.Static

    fun locationProvider(application: Application): LocationProvider =
        PlatformLocationProvider(application)
}
