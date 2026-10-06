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
import com.google.android.stardroid.location.LocationSource
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.SensorConfig
import com.google.android.stardroid.sensors.ZeroMagneticDeclinationSource
import com.google.android.stardroid.settings.DataStoreSettings
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.settings.settingsDataStore
import com.google.android.stardroid.startup.DataStoreStartupState
import com.google.android.stardroid.startup.StartupRouter
import com.google.android.stardroid.startup.StartupState
import com.google.android.stardroid.time.TimeController
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.map.ReferenceFrame
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
 * The app's foreground drives location the way Android's activity `onStart`/`onStop` do. With no
 * screens yet, the system location prompt shows once the EULA is accepted, in place of Android's
 * rationale sheet (phase 5 brings the location screens).
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppGraph {
    private val supportDirectory: String =
        checkNotNull(
            NSFileManager.defaultManager
                .URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
                ?.path,
        ) { "no Application Support directory" }

    init {
        recordUncaughtExceptions(supportDirectory)
    }

    private val settingsStore = settingsDataStore("$supportDirectory/settings.preferences_pb")

    val settings: Settings = DataStoreSettings(settingsStore)

    val startupState: StartupState = DataStoreStartupState(settingsStore)

    val timeController = TimeController()

    private val foreground = AppForeground()

    /** The bundle's build number, which the startup gates compare as Android's versionCode. */
    private val appVersion: Long =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.toLongOrNull() ?: 0L

    private val startupRouter = StartupRouter(startupState, appVersion)

    /** Dims the screen in night mode, as the auto-dimness preference says. */
    private val screenDimming =
        ScreenDimming(
            settings.nightMode,
            settings.autoDimness,
            foreground.isForeground,
            MainScope(),
        )

    private val coreLocation = CoreLocationProvider()

    val locationController =
        LocationController(
            provider = coreLocation,
            settings = settings,
            hasLocationPermission = { coreLocation.isAuthorized },
        )

    private val orientationSource: OrientationSource =
        CoreMotionOrientationSource(
            config =
                combine(
                    settings.disableGyro,
                    settings.reverseMagneticZ,
                    settings.smoothingEnabled,
                    settings.steadiness,
                    settings.easeOff,
                    ::SensorConfig,
                ),
            // Core Motion corrects to true north only with the location it may then use.
            trueNorthAllowed = coreLocation.authorized,
            active = foreground.isForeground,
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

    init {
        MainScope().launch {
            // Latest: going to the background cancels a start still waiting on the EULA.
            foreground.isForeground.collectLatest {
                if (it) onStart() else locationController.stop()
            }
        }
    }

    /** Android's `MainActivity.onStart` location handling, plus the first-launch prompt. */
    private suspend fun onStart() {
        // The user can revoke the permission in Settings while the app is backgrounded.
        val state = locationController.state.value
        if (state is LocationState.Confirmed &&
            state.source == LocationSource.AUTO &&
            !coreLocation.isAuthorized
        ) {
            locationController.onPermissionRevoked()
        }
        locationController.start()
        // The terms carry the permissions notice, which must come before any permission prompt
        // (Korean Network Act art. 22-2; see eula.xml), as it does on Android.
        startupRouter.needsEula.first { !it }
        if (coreLocation.canRequestAuthorization && !settings.noAutoLocate.first()) {
            if (coreLocation.requestAuthorization()) {
                locationController.switchToAuto()
            } else {
                // iOS never shows the prompt twice: only Settings can grant it now.
                locationController.onPermissionDenied(canAsk = false)
            }
        }
    }

    /** The EULA gate; the warm welcome and What's New wait for their screens (phase 5). */
    fun startupViewModel(): StartupViewModel = StartupViewModel(startupRouter, startupState)

    /**
     * The layer rail and sheet. iOS has no satellite data and no notifications yet, so their
     * layer and alert rows stay hidden, as Android hides them with the experiments off.
     */
    fun layersViewModel(): LayersViewModel =
        LayersViewModel(settings, satellitesEnabled = false, notificationsEnabled = false)

    /** Search over the catalog, aimed as Android's is: [map] answers whether it is in manual mode. */
    fun searchViewModel(map: MapViewModel): SearchViewModel =
        SearchViewModel(
            catalog = ::catalog,
            locale = locale,
            ephemeris = MeeusEphemeris,
            now = timeController::now,
            settings = settings,
            // Android's Log.e; stdout reaches the device console (devicectl --console).
            logError = { message, cause -> println("SearchViewModel: $message: $cause") },
            isManualMode = { map.referenceFrame.value == ReferenceFrame.MANUAL },
            location = { locationController.locations.value },
        )

    /** Time travel over the app's one clock; computed events (sunset...) use the map's place. */
    fun timeTravelViewModel(): TimeTravelViewModel =
        TimeTravelViewModel(
            timeController,
            MeeusEphemeris,
            location = { locationController.locations.value },
        )

    fun mapViewModel(): MapViewModel =
        MapViewModel(
            orientationSource = orientationSource,
            // Core Motion turns to true north itself once location is allowed; until then the
            // map is uncorrected magnetic north (see CoreMotionOrientationSource).
            declinationSource = ZeroMagneticDeclinationSource,
            locations = locationController.locations,
            settings = settings,
            ephemeris = MeeusEphemeris,
            timeFlow = timeController.times,
            now = timeController::now,
            frameTicker = DisplayLinkFrameTicker(),
        )
}
