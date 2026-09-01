/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.di

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.google.android.stardroid.FlavorEdges
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.data.satellites.HttpUrlConnectionCelesTrakClient
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import com.google.android.stardroid.data.satellites.TleStore
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.sensors.DisplayRotationBus
import com.google.android.stardroid.sensors.GeomagneticDeclinationSource
import com.google.android.stardroid.sensors.MagneticDeclinationSource
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.SensorConfig
import com.google.android.stardroid.sensors.SensorManagerStatusSource
import com.google.android.stardroid.sensors.SensorOrientationSource
import com.google.android.stardroid.sensors.SensorStatusSource
import com.google.android.stardroid.settings.DataStoreSettings
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.DataStoreStartupState
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.StartupRouter
import com.google.android.stardroid.startup.StartupState
import com.google.android.stardroid.time.TimeController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

// v1 `ApplicationConstants.READ_TOS_PREF_VERSION`: set once v1's EULA is accepted, so its
// presence means this device ran v1. Read-only, detection-only — never written from v2.
private const val V1_READ_TOS_PREF_KEY = "read_tos_version"

/**
 * The app's singleton object graph, moved verbatim from the hand-wired `AppGraph` when Hilt
 * landed (D59; D38 recorded the deferral). Flavor edges stay the source-set-swapped
 * [FlavorEdges] object (D49) called from providers — one seam, no per-flavor modules yet.
 * Suspend-initialized state (catalog, layer registry) lives in
 * [com.google.android.stardroid.CatalogAccess].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun settings(application: Application): Settings =
        DataStoreSettings(
            application.settingsDataStore,
            enableAnalyticsDefault = FlavorEdges.ENABLE_ANALYTICS_DEFAULT,
            satelliteDataDefault = FlavorEdges.SATELLITE_DATA_DEFAULT,
        )

    /**
     * The satellite element sets (D92/D95). The store lives under `filesDir`, not `cacheDir`:
     * the OS may evict `cacheDir` at any time, and losing the elements silently kills the feature
     * for exactly the offline users who most need a cached copy.
     */
    @Provides
    @Singleton
    fun satelliteElementsRepository(application: Application): SatelliteElementsRepository =
        SatelliteElementsRepository(
            store = TleStore(File(application.filesDir, "satellites")),
            client = HttpUrlConnectionCelesTrakClient(),
        )

    /** First-run bookkeeping for the startup gates, in the same DataStore file. */
    @Provides
    @Singleton
    fun startupState(application: Application): StartupState =
        DataStoreStartupState(application.settingsDataStore)

    /** v1's Remote Config edge: fetched on gms, the shipped static defaults on fdroid (D49). */
    @Provides
    @Singleton
    fun experimentConfig(application: Application): ExperimentConfig =
        FlavorEdges.experimentConfig(application)

    /**
     * The usage-analytics edge — Firebase on gms, a no-op on fdroid (D49). The
     * `enable_analytics` preference drives the collection switch for the process lifetime,
     * v1's `setUpAnalytics` + settings-change reapplication in one collector.
     */
    @Provides
    @Singleton
    fun analytics(
        application: Application,
        settings: Settings,
    ): Analytics =
        FlavorEdges.analytics(application).also { edge ->
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                settings.enableAnalytics.collect { edge.setEnabled(it) }
            }
        }

    /** The v1 startup gating rules (EULA → warm welcome → What's New). */
    @Provides
    @Singleton
    fun startupRouter(
        application: Application,
        startupState: StartupState,
    ): StartupRouter {
        // The manifest's versionCode, the version every startup gate compares against.
        val appVersion =
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .longVersionCode
        // v1 wrote this into its own default SharedPreferences file under the shared
        // applicationId; D1 deliberately doesn't migrate the value, but reading it read-only
        // is enough to tell a v1 upgrader from a true fresh install. Read directly rather
        // than pulling in androidx.preference for one lookup — PreferenceManager's
        // getDefaultSharedPreferences is just this file-naming convention.
        val isV1Upgrade =
            application
                .getSharedPreferences(
                    "${application.packageName}_preferences",
                    Context.MODE_PRIVATE,
                )
                .contains(V1_READ_TOS_PREF_KEY)
        return StartupRouter(startupState, appVersion, isV1Upgrade = isV1Upgrade)
    }

    /**
     * The shared clock bus (D13, D42): every time consumer — layers, the map's local frame
     * and sky gradient, the time-travel UI — reads this one clock, so time travel moves the
     * whole sky together.
     */
    @Provides
    @Singleton
    fun timeController(): TimeController = TimeController()

    /**
     * The shared location bus (D44): the v1 `LocationController` state machine, app-scoped
     * like [timeController] so the map's local frame, the horizon layer, and the location UI
     * all read one observer position.
     */
    @Provides
    @Singleton
    fun locationController(
        application: Application,
        settings: Settings,
    ): LocationController =
        LocationController(
            provider = FlavorEdges.locationProvider(application),
            settings = settings,
            hasLocationPermission = {
                application.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            },
        )

    @Provides
    @Singleton
    fun declinationSource(): MagneticDeclinationSource = GeomagneticDeclinationSource()

    /**
     * App-scoped so ViewModels can hold it across activity recreation; gated to the process's
     * STARTED state so sensor listeners unregister while the app is backgrounded (v1 parity).
     */
    @Provides
    @Singleton
    fun orientationSource(
        application: Application,
        settings: Settings,
        displayRotation: DisplayRotationBus,
    ): OrientationSource {
        val sensorConfigs =
            combine(
                settings.disableGyro,
                settings.sensorSpeed,
                settings.sensorDamping,
                settings.reverseMagneticZ,
                ::SensorConfig,
            )
        val delegate =
            SensorOrientationSource(
                application.getSystemService(SensorManager::class.java),
                sensorConfigs,
            ) {
                displayRotation.rotation.value
            }
        return object : OrientationSource {
            override val available = delegate.available

            override fun orientations(): Flow<Matrix3> =
                delegate.orientations().flowWithLifecycle(
                    ProcessLifecycleOwner.get().lifecycle,
                    Lifecycle.State.STARTED,
                )
        }
    }

    /**
     * Raw per-sensor presence/readings for the diagnostics and calibration screens; the
     * flows are cold, so nothing registers until a screen collects.
     */
    @Provides
    @Singleton
    fun sensorStatusSource(application: Application): SensorStatusSource =
        SensorManagerStatusSource(application.getSystemService(SensorManager::class.java))
}
