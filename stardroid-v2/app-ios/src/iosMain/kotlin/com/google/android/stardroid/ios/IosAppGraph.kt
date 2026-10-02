/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.data.RoomCatalogRepository
import com.google.android.stardroid.data.SkyMapDatabaseFactory
import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.location.LocationProvider
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.ZeroMagneticDeclinationSource
import com.google.android.stardroid.settings.DataStoreSettings
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.settings.settingsDataStore
import com.google.android.stardroid.startup.DataStoreStartupState
import com.google.android.stardroid.startup.StartupState
import com.google.android.stardroid.time.TimeController
import com.google.android.stardroid.ui.map.MapViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDomainMask
import platform.Foundation.preferredLanguages

/**
 * The iOS app's object graph: what Android's Hilt modules and `CatalogAccess` provide, built by
 * hand. Everything here is shared code; only the platform edges are iOS's.
 *
 * Phase 4b runs without sensors or location — the map starts in manual mode at the default
 * location (or the saved one). Core Motion and Core Location replace the two stubs in 4c.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppGraph {
    private val supportDirectory: String =
        checkNotNull(
            NSFileManager.defaultManager
                .URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
                ?.path,
        ) { "no Application Support directory" }

    private val settingsStore = settingsDataStore("$supportDirectory/settings.preferences_pb")

    val settings: Settings = DataStoreSettings(settingsStore)

    val startupState: StartupState = DataStoreStartupState(settingsStore)

    val timeController = TimeController()

    val locationController =
        LocationController(
            provider = NoLocationProvider,
            settings = settings,
            hasLocationPermission = { false },
        )

    /** The app's language, as the catalog reads it; iOS restarts an app whose language changes. */
    val locale: StateFlow<LocaleSpec> =
        MutableStateFlow(LocaleSpec(NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"))

    private val catalogMutex = Mutex()
    private var catalog: CatalogRepository? = null

    /** Opens the bundled catalog (copied to Application Support, D24 recovery) on first use. */
    suspend fun catalog(): CatalogRepository =
        catalogMutex.withLock {
            catalog
                ?: RoomCatalogRepository(
                    SkyMapDatabaseFactory.createWithRecovery(
                        bundledPath =
                            checkNotNull(NSBundle.mainBundle.pathForResource("skymap", "db")) {
                                "skymap.db is missing from the app bundle"
                            },
                        databasePath = "$supportDirectory/${SkyMapDatabaseFactory.DATABASE_NAME}",
                    ),
                ).also { catalog = it }
        }

    private val registryMutex = Mutex()
    private var registry: LayerRegistry? = null

    /** Every layer, over [catalog]; built once. */
    suspend fun layerRegistry(): LayerRegistry {
        val repository = catalog()
        return registryMutex.withLock {
            registry
                ?: LayerRegistry.create(
                    catalog = repository,
                    locale = locale,
                    strings = flowOf(EnglishLayerStrings),
                    clock = timeController.times,
                    location = locationController.locations,
                    ephemeris = MeeusEphemeris,
                    settings = settings,
                    satelliteElements = emptyFlow(),
                    satellitesEnabled = false,
                ).also { registry = it }
        }
    }

    fun mapViewModel(): MapViewModel =
        MapViewModel(
            orientationSource = NoOrientationSource,
            declinationSource = ZeroMagneticDeclinationSource,
            locations = locationController.locations,
            settings = settings,
            ephemeris = MeeusEphemeris,
            timeFlow = timeController.times,
            now = timeController::now,
            frameTicker = DisplayLinkFrameTicker(),
        )
}

/** Phase 4b's stand-in until Core Motion: no sensors, so the map starts in manual mode. */
private object NoOrientationSource : OrientationSource {
    override val available = false

    override fun orientations(): Flow<Matrix3> = emptyFlow()
}

/** Phase 4b's stand-in until Core Location: never available, so the saved or default location. */
private object NoLocationProvider : LocationProvider {
    override fun startUpdates(
        minDistanceMetres: Float,
        onUpdate: (location: LatLong, accuracyM: Float?) -> Unit,
    ) = Unit

    override fun stopUpdates() = Unit

    override fun isAvailable() = false
}
