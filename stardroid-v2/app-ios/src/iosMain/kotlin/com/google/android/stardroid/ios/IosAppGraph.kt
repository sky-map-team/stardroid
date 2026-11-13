/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.data.RoomCatalogRepository
import com.google.android.stardroid.data.SkyMapDatabaseFactory
import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.location.LocationSource
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.render.api.RendererInfo
import com.google.android.stardroid.satellites.SatelliteDiagnosticsState
import com.google.android.stardroid.satellites.TrackedSatellite
import com.google.android.stardroid.satellites.satelliteElementsFlow
import com.google.android.stardroid.satellites.satelliteFetchConditions
import com.google.android.stardroid.satellites.satelliteUiStatusFlow
import com.google.android.stardroid.satellites.trackedSatellites
import com.google.android.stardroid.satellites.upcomingGoodPasses
import com.google.android.stardroid.sensors.SensorConfig
import com.google.android.stardroid.sensors.ZeroMagneticDeclinationSource
import com.google.android.stardroid.settings.DataStoreSettings
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.settings.settingsDataStore
import com.google.android.stardroid.startup.DataStoreStartupState
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.FlagSource
import com.google.android.stardroid.startup.FlagSourceExperimentConfig
import com.google.android.stardroid.startup.StartupRouter
import com.google.android.stardroid.startup.StartupState
import com.google.android.stardroid.time.TimeController
import com.google.android.stardroid.ui.calibration.CompassCalibrationViewModel
import com.google.android.stardroid.ui.diagnostics.DiagnosticsViewModel
import com.google.android.stardroid.ui.gallery.GalleryViewModel
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.map.ReferenceFrame
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.satellite_description
import com.google.android.stardroid.ui.resources.satellite_search_name_iss
import com.google.android.stardroid.ui.resources.satellite_search_name_tiangong
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.settings.SettingsViewModel
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.getString
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.preferredLanguages
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * The iOS app's object graph: what Android's Hilt modules provide, built by hand. The foreground
 * drives location as Android's `onStart`/`onStop` do; the system location prompt waits for the
 * EULA and warm welcome, in place of Android's rationale dialog.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppGraph(
    private val analytics: Analytics = NoOpAnalytics,
    flags: FlagSource = NoFlagSource,
) {
    /** The experiment flags, frozen for this launch (see [FlagSourceExperimentConfig]). */
    val experiments: ExperimentConfig = iosExperiments(flags)

    private val satellitesEnabled: Boolean = experiments.isEnabled(Experiment.SATELLITES)

    private val supportDirectory: String =
        checkNotNull(
            NSFileManager.defaultManager
                .URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
                ?.path,
        ) { "no Application Support directory" }

    init {
        recordUncaughtExceptions(supportDirectory)
    }

    private val scope = MainScope()

    // DataStore does its file I/O on this scope, which lives as long as the process.
    private val settingsStore =
        settingsDataStore(
            "$supportDirectory/settings.preferences_pb",
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

    // Smoothing defaults on, as on Android while the 1€ filter is in beta (issue #1007): the
    // settings row says so, and Core Motion goes through the same filter.
    val settings: Settings = DataStoreSettings(settingsStore, smoothingEnabledDefault = true)

    val startupState: StartupState = DataStoreStartupState(settingsStore)

    val timeController = TimeController()

    private val foreground = AppForeground()

    private val satellites = IosSatellites("$supportDirectory/satellites", analytics)

    // The build number stands in for Android's versionCode in the startup gates; at least 1, the
    // warm-welcome floor below.
    private val appVersion: Long =
        (
            (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
                ?.toLongOrNull() ?: 1L
        ).coerceAtLeast(1L)

    // Android's floor is one of its own versionCodes; against it a tour seen at an iOS build
    // number would count as never seen. A floor of 1 shows it once.
    private val startupRouter =
        StartupRouter(startupState, appVersion, warmWelcomeResetVersionCode = 1L)

    /** Dims the screen in night mode, as the auto-dimness preference says. */
    private val screenDimming =
        ScreenDimming(
            settings.nightMode,
            settings.autoDimness,
            foreground.isForeground,
            scope,
        )

    private val coreLocation = CoreLocationProvider()

    // Apple asks for one CMMotionManager per app.
    private val motionManager = CMMotionManager()

    val hasCompass: Boolean get() = motionManager.magnetometerAvailable

    val hasAccelerometer: Boolean get() = motionManager.accelerometerAvailable

    val hasGyroscope: Boolean get() = motionManager.gyroAvailable

    val locationController =
        LocationController(
            provider = coreLocation,
            settings = settings,
            hasLocationPermission = { coreLocation.isAuthorized },
        )

    /** Kept current by the map's view controller; the sensors remap into this frame. */
    val displayRotation = DisplayRotationHolder()

    private val orientationSource =
        CoreMotionOrientationSource(
            motionManager = motionManager,
            config =
                combine(
                    settings.disableGyro,
                    settings.reverseMagneticZ,
                    settings.smoothingEnabled,
                    settings.steadiness,
                    settings.easeOff,
                    ::SensorConfig,
                ),
            // Core Motion corrects to true north only with the location it may then use. The
            // correction setting turns it off, as zeroing the declination does on Android.
            trueNorthAllowed =
                combine(coreLocation.authorized, settings.useMagneticCorrection) { allowed, use ->
                    allowed && use
                },
            active = foreground.isForeground,
            displayRotation = displayRotation,
        )

    /** The app's language, as the catalog reads it; iOS restarts an app whose language changes. */
    val locale: StateFlow<LocaleSpec> =
        MutableStateFlow(LocaleSpec(NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"))

    init {
        // As on Android, the opt-out drives collection for the process lifetime.
        scope.launch { settings.enableAnalytics.collect { analytics.setEnabled(it) } }
        analytics.setUserProperty(AnalyticsEvents.USER_LOCALE, locale.value.tag)
    }

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
                    satelliteElements =
                        if (satellitesEnabled) {
                            satelliteElementsFlow(satellites.repository)
                        } else {
                            emptyFlow()
                        },
                    satellitesEnabled = satellitesEnabled,
                ).also { registry = it }
        }
    }

    /** The download gates shared with Android (#1272). iOS has no widgets or alerts to count. */
    private val satelliteGates =
        satelliteFetchConditions(
            experiments = experiments,
            settings = settings,
            startupRouter = startupRouter,
            startupState = startupState,
            backgroundUse = flowOf(false),
        )

    init {
        scope.launch {
            // Latest: going to the background cancels a start still waiting on the EULA.
            foreground.isForeground.collectLatest {
                if (it) onStart() else locationController.stop()
            }
        }
        scope.launch {
            // Only a foreground the user sees counts as use, never a background refresh launch.
            foreground.isForeground.collect {
                if (it) startupState.setLastForegroundAt(Clock.System.now())
            }
        }
        scope.launch {
            // Opening the app, switching the layer on and accepting the terms all look for stale
            // elements, once every gate is open.
            combine(foreground.isForeground, satelliteGates) { inForeground, gates ->
                inForeground && gates.allowFetch(Clock.System.now())
            }.distinctUntilChanged()
                .collect { if (it) refreshSatellitesIfStale() }
        }
    }

    /**
     * The system's background refresh slot ([SatelliteBackgroundRefresh]): the same gates and
     * policy as the foreground fetch, minus the staleness test, since the system picks the time.
     * [onDone] runs however the job ends; `scheduleNext` is whether the gates were open.
     */
    internal fun startBackgroundRefresh(
        onDone: (completed: Boolean, scheduleNext: Boolean) -> Unit,
    ): Job =
        scope.launch {
            var open = false
            var completed = false
            try {
                open = satelliteRefreshWanted()
                if (open) withContext(Dispatchers.IO) { satellites.refreshNow() }
                completed = true
            } finally {
                onDone(completed, open)
            }
        }

    /** Tells [onResult] whether a background refresh would download anything now. */
    internal fun checkSatelliteRefreshWanted(onResult: (Boolean) -> Unit): Job =
        scope.launch { onResult(satelliteRefreshWanted()) }

    private suspend fun satelliteRefreshWanted(): Boolean =
        satelliteGates.first().allowFetch(Clock.System.now())

    /** Satellite data status for the Diagnostics screen; null with the flag off. */
    suspend fun satelliteDiagnostics(): SatelliteDiagnosticsState? =
        if (satellitesEnabled) withContext(Dispatchers.IO) { satellites.diagnostics() } else null

    /**
     * The caller has checked the gates. Through the repository, so the circuit breaker and the
     * two-hour minimum still apply.
     */
    private suspend fun refreshSatellitesIfStale() {
        withContext(Dispatchers.IO) { satellites.refreshIfStale() }
    }

    /**
     * Android's `MainActivity.onStart` location handling, with iOS's prompt in place of the
     * rationale dialog Android shows for a missing permission: once per foreground, as that is.
     */
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
        // (Korean Network Act art. 22-2; see eula.xml). The prompt then waits out the warm
        // welcome too, as Android's rationale waits for the map: it would cover the tour.
        combine(startupRouter.needsEula, startupRouter.needsWarmWelcome) { eula, welcome ->
            eula || welcome
        }.first { !it }
        if (!coreLocation.isAuthorized && !settings.noAutoLocate.first()) askForAutoLocation()
    }

    /**
     * Auto location as far as the user allows it: the system prompt if they have not been asked
     * (iOS asks only once), and otherwise, refused, the permanently-denied dialog's Settings or
     * manual-entry exits.
     */
    private suspend fun askForAutoLocation() {
        if (coreLocation.isAuthorized ||
            (coreLocation.canRequestAuthorization && coreLocation.requestAuthorization())
        ) {
            locationController.switchToAuto()
        } else {
            locationController.onPermissionDenied(canAsk = false)
        }
    }

    /** The location sheet's "Use Automatic Location" (Android's `requestAutoLocation`). */
    fun requestAutoLocation() {
        scope.launch { askForAutoLocation() }
    }

    /** The permanently-denied dialog's exit: the app's page in Settings. */
    fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }

    /** The startup gates: the EULA, the warm welcome and What's New. */
    fun startupViewModel(): StartupViewModel =
        StartupViewModel(startupRouter, startupState, analytics = analytics)

    /** The layer rail and sheet; alert rows stay hidden until iOS has notifications. */
    fun layersViewModel(): LayersViewModel =
        LayersViewModel(
            settings,
            satellitesEnabled = satellitesEnabled,
            notificationsEnabled = false,
            satelliteStatus =
                if (satellitesEnabled) {
                    satelliteUiStatusFlow(
                        satellites.repository,
                    )
                } else {
                    flowOf(null)
                },
            onRefreshSatellites = { withContext(Dispatchers.IO) { satellites.refreshNow() } },
            analytics = analytics,
        )

    /** Search over the catalog; [map] answers whether it is in manual mode, as on Android. */
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
            satellites = ::searchableSatellites,
            satelliteAliases = ::satelliteSearchAliases,
            analytics = analytics,
        )

    /** Search spans a hidden Satellites layer (and re-enables it for the hit), so no layer gate. */
    private suspend fun searchableSatellites() = satellitesFor(requireLayerEnabled = false)

    /** A hidden layer draws nothing to tap, so taps need the layer on. */
    private suspend fun tappableSatellites() = satellitesFor(requireLayerEnabled = true)

    private suspend fun satellitesFor(requireLayerEnabled: Boolean): List<TrackedSatellite> {
        if (!satellitesEnabled) return emptyList()
        if (requireLayerEnabled &&
            !LayerRegistry.layerEnabled(settings, SatelliteLayer.LAYER_ID).first()
        ) {
            return emptyList()
        }
        val elements = currentElements()
        val description = getString(Res.string.satellite_description)
        return withContext(Dispatchers.Default) {
            trackedSatellites(
                elements,
                locationController.locations.value,
                description,
                timeController.now(),
            )
        }
    }

    private suspend fun satelliteSearchAliases(noradId: Int): List<String> =
        when (noradId) {
            SatelliteLayer.ISS_NORAD_ID -> listOf(getString(Res.string.satellite_search_name_iss))
            SatelliteLayer.TIANGONG_NORAD_ID ->
                // "CSS" is the Chinese Space Station's official abbreviation, so it isn't translated.
                listOf(getString(Res.string.satellite_search_name_tiangong), "CSS")
            else -> emptyList()
        }

    private suspend fun currentElements() =
        withContext(Dispatchers.IO) { satellites.repository.current() }

    private suspend fun upcomingSatellitePasses(noradId: Int): List<SatellitePass> {
        val elements = currentElements()
        return withContext(Dispatchers.Default) {
            upcomingGoodPasses(
                elements,
                noradId,
                locationController.locations.value,
                timeController.now(),
            )
        }
    }

    /** Time travel over the app's one clock; computed events (sunset...) use the map's place. */
    fun timeTravelViewModel(): TimeTravelViewModel =
        TimeTravelViewModel(
            timeController,
            MeeusEphemeris,
            location = { locationController.locations.value },
            analytics = analytics,
        )

    /** The card for a tapped object; the Moon-widget promo is Android's only. */
    fun objectInfoViewModel(): ObjectInfoViewModel =
        ObjectInfoViewModel(
            catalog = ::catalog,
            locale = locale,
            ephemeris = MeeusEphemeris,
            now = timeController::now,
            settings = settings,
            location = { locationController.locations.value },
            satellites = ::tappableSatellites,
            passTimesReliable = {
                currentElements().mayShowPassTimes
            },
            upcomingPasses = ::upcomingSatellitePasses,
            analytics = analytics,
        )

    /** Core Motion is the only sensor path and already reports true north, so those rows hide. */
    fun settingsViewModel(): SettingsViewModel =
        SettingsViewModel(
            settings,
            classicSensorsAvailable = false,
            analyticsAvailable = true,
            magneticCorrectionAvailable = false,
            analytics = analytics,
            experimentConfig = experiments,
        )

    private val networkMonitor by lazy { NetworkMonitor() }

    private val sensorStatus by lazy {
        CoreMotionStatusSource(motionManager, orientationSource.deviceMotion)
    }

    /** Over Core Motion's raw sensors; the magnetic correction row reads zero by design. */
    fun diagnosticsViewModel(
        map: MapViewModel,
        rendererInfo: RendererInfo,
    ): DiagnosticsViewModel =
        DiagnosticsViewModel(
            sensorStatus = sensorStatus,
            locationStates = locationController.state,
            camera = map.camera,
            settings = settings,
            declinationSource = ZeroMagneticDeclinationSource,
            now = timeController::now,
            isLocationPermissionGranted = { coreLocation.isAuthorized },
            gpsStatus = ::iosGpsStatus,
            networkStatus = { networkMonitor.status },
            orientationSource = orientationSource,
            localFrame = map.localFrame,
            rendererInfo = { rendererInfo },
            analytics = analytics,
        )

    /** From Core Motion's calibration level; iPhone 13 measurement in screens-and-startup.md. */
    fun calibrationViewModel(): CompassCalibrationViewModel =
        CompassCalibrationViewModel(
            sensorStatus = sensorStatus,
            settings = settings,
            nowMillis = { (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong() },
            analytics = analytics,
        )

    /** The photo grid over the catalog, in the app's language. */
    fun galleryViewModel(): GalleryViewModel = GalleryViewModel(::catalog, locale)

    /** The location sheet and its dialogs, with Apple's geocoder behind manual entry. */
    fun locationViewModel(): LocationViewModel =
        LocationViewModel(
            locationController,
            IosGeocoding(),
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
            analytics = analytics,
        )
}

private const val MILLIS_PER_SECOND = 1000
