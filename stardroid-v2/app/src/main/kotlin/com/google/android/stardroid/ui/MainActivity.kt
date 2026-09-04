/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.google.android.stardroid.CatalogAccess
import com.google.android.stardroid.R
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.SessionBucket
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.camera.SkyCameraPreview
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.locale.LocaleSource
import com.google.android.stardroid.location.AndroidGeocoding
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.location.LocationSource
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.render.AssetImageLoader
import com.google.android.stardroid.render.RenderBinder
import com.google.android.stardroid.render.RenderConnector
import com.google.android.stardroid.render.RendererInfoStore
import com.google.android.stardroid.render.gles1.GLSkyRenderer
import com.google.android.stardroid.satellites.nextVisiblePass
import com.google.android.stardroid.satellites.satelliteEntryPoint
import com.google.android.stardroid.satellites.satelliteUiStatusFlow
import com.google.android.stardroid.satellites.trackedSatellites
import com.google.android.stardroid.sensors.DisplayRotationBus
import com.google.android.stardroid.sensors.MagneticDeclinationSource
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.SensorKind
import com.google.android.stardroid.sensors.SensorStatusSource
import com.google.android.stardroid.settings.AutoDimness
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.StartupRouter
import com.google.android.stardroid.startup.StartupState
import com.google.android.stardroid.time.TimeController
import com.google.android.stardroid.ui.calibration.CompassCalibrationViewModel
import com.google.android.stardroid.ui.diagnostics.DiagnosticsViewModel
import com.google.android.stardroid.ui.diagnostics.GpsStatus
import com.google.android.stardroid.ui.diagnostics.NetworkStatus
import com.google.android.stardroid.ui.gallery.GalleryViewModel
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.map.ReferenceFrame
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.settings.SettingsViewModel
import com.google.android.stardroid.ui.startup.EulaScreen
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.startup.VersionBanner
import com.google.android.stardroid.ui.startup.WhatsNewDialog
import com.google.android.stardroid.ui.theme.SkyMapTheme
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import com.google.android.stardroid.widget.MoonWidget
import com.google.android.stardroid.widget.MoonWidgetReceiver
import com.google.android.stardroid.widget.WidgetScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import kotlin.time.Duration
import com.google.android.stardroid.settings.Settings as SkyMapSettings

/**
 * The single-activity Compose shell (layers-and-app.md): builds the GL surface, wires the
 * layer/camera/render-state flows into it through [RenderBinder], and hosts the
 * [SkyMapNavHost] graph behind the startup gates (splash → EULA → warm welcome/What's New,
 * screens-and-startup.md).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var glSurfaceView: GLSurfaceView

    /** Filled in by the GL backend once its surface exists; read by the diagnostics screen. */
    private val rendererInfoStore = RendererInfoStore()

    @Inject lateinit var settings: SkyMapSettings

    @Inject lateinit var analytics: Analytics

    @Inject lateinit var timeController: TimeController

    @Inject lateinit var locationController: LocationController

    @Inject lateinit var orientationSource: OrientationSource

    @Inject lateinit var declinationSource: MagneticDeclinationSource

    @Inject lateinit var sensorStatusSource: SensorStatusSource

    @Inject lateinit var experimentConfig: ExperimentConfig

    @Inject lateinit var startupRouter: StartupRouter

    @Inject lateinit var startupState: StartupState

    @Inject lateinit var localeSource: LocaleSource

    @Inject lateinit var displayRotation: DisplayRotationBus

    @Inject lateinit var catalogAccess: CatalogAccess

    /** v1's session anchor: foreground time between onStart and onStop. */
    private var sessionStartTimeMillis = 0L

    private val mapViewModel: MapViewModel by viewModels {
        viewModelFactory {
            initializer {
                MapViewModel(
                    orientationSource = orientationSource,
                    declinationSource = declinationSource,
                    locations = locationController.locations,
                    settings = settings,
                    ephemeris = MeeusEphemeris,
                    timeFlow = timeController.times,
                    now = timeController::now,
                    analytics = analytics,
                )
            }
        }
    }

    private val layersViewModel: LayersViewModel by viewModels {
        viewModelFactory {
            initializer {
                LayersViewModel(
                    settings,
                    analytics,
                    satellitesEnabled = experimentConfig.isEnabled(Experiment.SATELLITES),
                    satelliteStatus =
                        satelliteUiStatusFlow(
                            satelliteEntryPoint(this@MainActivity)
                                .satelliteElementsRepository(),
                        ),
                    // The ordinary policy-respecting refresh, never the debug force path.
                    // Returns the remaining wait when policy refused, so the card can say so.
                    onRefreshSatellites = {
                        withContext(Dispatchers.IO) {
                            val repository =
                                satelliteEntryPoint(this@MainActivity)
                                    .satelliteElementsRepository()
                            when (val result = repository.refresh()) {
                                is RefreshResult.Skipped -> result.retryAfter
                                is RefreshResult.Completed -> Duration.ZERO
                            }
                        }
                    },
                )
            }
        }
    }

    private val timeTravelViewModel: TimeTravelViewModel by viewModels {
        viewModelFactory {
            initializer {
                TimeTravelViewModel(
                    timeController = timeController,
                    ephemeris = MeeusEphemeris,
                    analytics = analytics,
                    location = { locationController.locations.value },
                )
            }
        }
    }

    private val searchViewModel: SearchViewModel by viewModels {
        viewModelFactory {
            initializer {
                SearchViewModel(
                    catalog = catalogAccess::repository,
                    locale = localeSource.specs,
                    ephemeris = MeeusEphemeris,
                    now = timeController::now,
                    settings = settings,
                    analytics = analytics,
                    isManualMode = {
                        mapViewModel.referenceFrame.value == ReferenceFrame.MANUAL
                    },
                    location = { locationController.locations.value },
                )
            }
        }
    }

    private val objectInfoViewModel: ObjectInfoViewModel by viewModels {
        viewModelFactory {
            initializer {
                ObjectInfoViewModel(
                    catalog = catalogAccess::repository,
                    locale = localeSource.specs,
                    ephemeris = MeeusEphemeris,
                    now = timeController::now,
                    settings = settings,
                    analytics = analytics,
                    location = { locationController.locations.value },
                    experimentConfig = experimentConfig,
                    moonWidgetPlaced = {
                        WidgetScheduler.hasInstances(
                            this@MainActivity,
                            MoonWidgetReceiver::class.java,
                        )
                    },
                    satellites = {
                        trackedSatellites(
                            this@MainActivity,
                            locationController.locations.value,
                            getString(R.string.satellite_description),
                            timeController.now(),
                        )
                    },
                    passTimesReliable = {
                        satelliteEntryPoint(this@MainActivity)
                            .satelliteElementsRepository()
                            .current()
                            .mayShowPassTimes
                    },
                    nextPass = { norad ->
                        nextVisiblePass(
                            this@MainActivity,
                            norad,
                            locationController.locations.value,
                            timeController.now(),
                        )
                    },
                )
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer { SettingsViewModel(settings, analytics, experimentConfig) }
        }
    }

    private val galleryViewModel: GalleryViewModel by viewModels {
        viewModelFactory {
            initializer { GalleryViewModel(catalogAccess::repository, localeSource.specs) }
        }
    }

    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = applicationContext
                DiagnosticsViewModel(
                    sensorStatus = sensorStatusSource,
                    locationStates = locationController.state,
                    camera = mapViewModel.camera,
                    settings = settings,
                    declinationSource = declinationSource,
                    now = timeController::now,
                    isLocationPermissionGranted = {
                        appContext.checkSelfPermission(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                    },
                    gpsStatus = { gpsStatus(appContext) },
                    networkStatus = { networkStatus(appContext) },
                    rendererInfo = rendererInfoStore::get,
                )
            }
        }
    }

    private val calibrationViewModel: CompassCalibrationViewModel by viewModels {
        viewModelFactory {
            initializer {
                CompassCalibrationViewModel(
                    sensorStatus = sensorStatusSource,
                    settings = settings,
                    nowMillis = System::currentTimeMillis,
                    analytics = analytics,
                )
            }
        }
    }

    private val startupViewModel: StartupViewModel by viewModels {
        viewModelFactory {
            initializer {
                StartupViewModel(startupRouter, startupState, analytics)
            }
        }
    }

    private val locationViewModel: LocationViewModel by viewModels {
        viewModelFactory {
            initializer {
                LocationViewModel(locationController, AndroidGeocoding(applicationContext))
            }
        }
    }

    // v1 DynamicStarMapActivity's launcher: grant switches to auto; denial reports whether the
    // system will let us ask again, driving the denied vs permanently-denied states.
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                locationController.switchToAuto()
            } else {
                locationController.onPermissionDenied(
                    shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }

    /** The through-camera preview's hardware edge; bound/released by the map screen. */
    private val skyCameraPreview by lazy { SkyCameraPreview(this) }

    // The camera layer's permission launcher (same shape as location's): grant turns the
    // layer on; denial reaches the map's snackbar with the can-ask-again verdict.
    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                mapViewModel.setArMode(true)
            } else {
                mapViewModel.arPermissionDenials.tryEmit(
                    shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The themed splash (AndroidX SplashScreen) covers the startup-state read; there is
        // no splash activity (screens-and-startup.md).
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { startupViewModel.state.value == null }
        // v1 kept the screen alive unconditionally while the map is up (stargazers stare,
        // they don't touch); the flag replaces v1's belt-and-braces SCREEN_BRIGHT_WAKE_LOCK.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The postSplashScreenTheme's windowFullscreen hides the status bar, but leaves the
        // window fitting itself around the navigation bar — opaque nav bar on API <35, where
        // the OS doesn't yet force edge-to-edge (R2.2/R2.4's cutout fixes already assume the
        // sky extends under both bars). This is what makes every navigationBarsPadding() call
        // in MapScreen/MapChrome mean something instead of resolving to ~0.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false
        displayRotation.rotation.value = currentDisplayRotation()
        // v1 StardroidApplication's per-process start snapshot; rotation recreates this
        // activity, so only the first creation logs.
        if (savedInstanceState == null) {
            logStartupSnapshot()
            // A widget tap (D75) lands on the tapped object's info card once the map is up;
            // the card state waits out any startup gates in ObjectInfoViewModel.
            intent.getStringExtra(MoonWidget.EXTRA_SHOW_OBJECT_ID)?.let {
                objectInfoViewModel.show(CelestialObjectId(it))
            }
        } else {
            sessionStartTimeMillis = savedInstanceState.getLong(SAVED_SESSION_START_TIME_KEY, 0L)
        }

        val imageLoader = AssetImageLoader(assets)
        val glRenderer =
            GLSkyRenderer(
                resources.displayMetrics.density,
                imageLoader::load,
                rendererInfoStore::set,
            )
        glSurfaceView =
            GLSurfaceView(this).apply {
                setEGLContextClientVersion(1)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                setRenderer(glRenderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                // Through-camera mode (camera-ar-mode.md/D64, Option A): the surface holds
                // alpha and composites above the CameraX preview plane but below the window.
                // Costless when the renderer clears opaque (the non-AR default).
                holder.setFormat(PixelFormat.TRANSLUCENT)
                setZOrderMediaOverlay(true)
            }
        val connector = RenderConnector(glRenderer, glSurfaceView)
        val binder = RenderBinder(connector)
        lifecycleScope.launch {
            try {
                val layers = catalogAccess.layerRegistry().layers
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    binder.bindCamera(this, mapViewModel.camera)
                    binder.bindRenderState(this, mapViewModel.renderState)
                    for (layer in layers) {
                        binder.bindLayer(this, layer, settings::layerEnabled)
                    }
                }
            } catch (e: Exception) {
                // TODO: Handle database/registry load failure gracefully per D24
            }
        }

        // v1 ActivityLightLevelChanger: night mode dims the screen by the auto-dimness
        // preference; day mode always restores the system brightness.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    settings.nightMode,
                    settings.autoDimness,
                    ::Pair,
                ).collect { (night, dimness) -> applyScreenDimming(night, dimness) }
            }
        }

        val appVersionName =
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                .getOrNull()
                .orEmpty()

        setContent {
            val nightMode by mapViewModel.nightMode.collectAsStateWithLifecycle()
            val startup by startupViewModel.state.collectAsStateWithLifecycle()
            val sensorWarningSuppressed by startupViewModel
                .suppressMissingSensorWarning
                .collectAsStateWithLifecycle()
            SkyMapTheme(nightMode) {
                // The splash holds until this first non-null state (v1's splash covered the
                // same routing decision), so the gate values are ready before anything shows.
                val gates = startup ?: return@SkyMapTheme
                // The start destination is decided once; the gates keep flowing after.
                val startOnWelcome = remember { gates.needsWarmWelcome }
                val sensorPresence = remember { sensorPresence() }
                SkyMapNavHost(
                    navController = rememberNavController(),
                    startOnWelcome = startOnWelcome,
                    glSurfaceView = glSurfaceView,
                    mapViewModel = mapViewModel,
                    layersViewModel = layersViewModel,
                    timeTravelViewModel = timeTravelViewModel,
                    searchViewModel = searchViewModel,
                    objectInfoViewModel = objectInfoViewModel,
                    locationViewModel = locationViewModel,
                    settingsViewModel = settingsViewModel,
                    galleryViewModel = galleryViewModel,
                    diagnosticsViewModel = diagnosticsViewModel,
                    calibrationViewModel = calibrationViewModel,
                    sensorPresence = sensorPresence,
                    sensorWarningSuppressed = sensorWarningSuppressed,
                    onWelcomeFinished = startupViewModel::completeWarmWelcome,
                    onWelcomeSkipped = startupViewModel::skipWarmWelcome,
                    onWelcomeStarted = startupViewModel::warmWelcomeStarted,
                    onWelcomeSlideViewed = startupViewModel::warmWelcomeSlideViewed,
                    onRequestLocationPermission = ::requestLocationPermission,
                    onRequestAutoLocation = ::requestAutoLocation,
                    onOpenAppSettings = ::openAppSettings,
                    arCamera = skyCameraPreview,
                    hasCameraPermission = {
                        checkSelfPermission(Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    },
                    onRequestCameraPermission = {
                        cameraPermissionRequest.launch(Manifest.permission.CAMERA)
                    },
                    experimentConfig = experimentConfig,
                )
                // v1 SplashScreenActivity's gating order: the EULA blocks everything until
                // accepted (declining exits); What's New then shows on upgrades only. A true
                // fresh install never gets it (nothing to report); anyone with real
                // prior-version history who also needs the warm welcome gets What's New first,
                // as an overlay ahead of the still-pending tour underneath
                // (needsWhatsNewDuringWarmWelcome).
                if (gates.needsEula) {
                    EulaScreen(
                        nightMode = nightMode,
                        onAccept = startupViewModel::acceptEula,
                        onDecline = {
                            startupViewModel.rejectEula()
                            finish()
                        },
                    )
                } else if (gates.needsWhatsNew &&
                    (!gates.needsWarmWelcome || gates.needsWhatsNewDuringWarmWelcome)
                ) {
                    WhatsNewDialog(
                        nightMode = nightMode,
                        onDismiss = startupViewModel::dismissWhatsNew,
                    )
                }
                // The branded version banner: shown once per cold start, covering the app while
                // it loads, then faded out to reveal the map (survives rotation via
                // rememberSaveable, so it plays once).
                var showBanner by rememberSaveable { mutableStateOf(true) }
                if (showBanner) {
                    VersionBanner(
                        versionName = appVersionName,
                        onFinished = { showBanner = false },
                    )
                }
            }
        }
    }

    private fun sensorPresence(): SensorPresence {
        val sensors = sensorStatusSource
        return SensorPresence(
            hasCompass = sensors.hasSensor(SensorKind.MAGNETOMETER),
            hasAccelerometer = sensors.hasSensor(SensorKind.ACCELEROMETER),
            hasGyroscope = sensors.hasSensor(SensorKind.GYROSCOPE),
        )
    }

    private fun applyScreenDimming(
        nightMode: Boolean,
        dimness: AutoDimness,
    ) {
        val brightness =
            if (!nightMode) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                when (dimness) {
                    AutoDimness.SYSTEM -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    AutoDimness.DIM -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
                    // v1's hand-tuned level: dimmer than most system settings but visible on
                    // every device it was ever tried on (20/255).
                    AutoDimness.CLASSIC -> 20f / 255f
                }
            }
        window.attributes = window.attributes.also { it.screenBrightness = brightness }
    }

    /**
     * v1's start-of-session event and device user properties (`setUpAnalytics` +
     * `performFeatureCheck`): what sensors exist, which sensor path the map will take, the
     * local hour/day, and whether the user runs in night mode.
     */
    private fun logStartupSnapshot() {
        // The opt-out preference loads from DataStore asynchronously (AppModule's
        // enableAnalytics collector), racing this synchronous onCreate call — wait for it
        // first so an opted-out user's device/session details never reach Firebase.
        lifecycleScope.launch {
            if (!settings.enableAnalytics.first()) return@launch
            val analytics = analytics
            val sensors = sensorStatusSource
            val present =
                buildList {
                    if (sensors.hasSensor(SensorKind.ACCELEROMETER)) {
                        add(AnalyticsEvents.DEVICE_SENSORS_ACCELEROMETER)
                    }
                    if (sensors.hasSensor(SensorKind.GYROSCOPE)) {
                        add(AnalyticsEvents.DEVICE_SENSORS_GYRO)
                    }
                    if (sensors.hasSensor(SensorKind.MAGNETOMETER)) {
                        add(AnalyticsEvents.DEVICE_SENSORS_MAGNETIC)
                    }
                    if (sensors.hasSensor(SensorKind.ROTATION_VECTOR)) {
                        add(AnalyticsEvents.DEVICE_SENSORS_ROTATION)
                    }
                }
            analytics.setUserProperty(
                AnalyticsEvents.DEVICE_SENSORS,
                present.ifEmpty { listOf(AnalyticsEvents.DEVICE_SENSORS_NONE) }.joinToString("|"),
            )
            analytics.setUserProperty(
                AnalyticsEvents.HAS_GYRO,
                sensors.hasSensor(SensorKind.GYROSCOPE).toString(),
            )
            analytics.setUserProperty(
                AnalyticsEvents.HAS_ROTATION_VECTOR,
                sensors.hasSensor(SensorKind.ROTATION_VECTOR).toString(),
            )
            analytics.setUserProperty(AnalyticsEvents.USER_LOCALE, localeSource.current.tag)
            val nightMode = settings.nightMode.first()
            val disableGyro = settings.disableGyro.first()
            val sensorPath =
                when {
                    !orientationSource.available -> AnalyticsEvents.SENSOR_PATH_NONE
                    sensors.hasSensor(SensorKind.ROTATION_VECTOR) && !disableGyro ->
                        AnalyticsEvents.SENSOR_PATH_ROTATION_VECTOR
                    else -> AnalyticsEvents.SENSOR_PATH_ACCEL_MAG
                }
            val calendar = Calendar.getInstance()
            analytics.trackEvent(
                AnalyticsEvents.START_EVENT,
                mapOf(
                    AnalyticsEvents.START_EVENT_HOUR to calendar[Calendar.HOUR_OF_DAY],
                    // 0-based like v1 (Calendar.DAY_OF_WEEK - 1; Sunday = 0).
                    AnalyticsEvents.START_EVENT_DAY_OF_WEEK to
                        calendar[Calendar.DAY_OF_WEEK] - 1,
                    AnalyticsEvents.START_EVENT_NIGHT_MODE to nightMode,
                    AnalyticsEvents.START_EVENT_SENSOR_PATH to sensorPath,
                ),
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (sessionStartTimeMillis == 0L) {
            sessionStartTimeMillis = SystemClock.elapsedRealtime()
        }
        // v1 checkLocationPermissionOnResume: the user may have revoked the permission in
        // system settings while we were backgrounded.
        val state = locationController.state.value
        if (state is LocationState.Confirmed &&
            state.source == LocationSource.AUTO &&
            !hasLocationPermission()
        ) {
            locationController.onPermissionRevoked()
        }
        locationController.start()
    }

    override fun onStop() {
        // v1's session definition: map-foreground time, bucketed (`SessionBucketLength`).
        // Rotation tears this activity down and immediately recreates it, so skip logging
        // when a config change is in flight to avoid a spurious short session.
        if (!isChangingConfigurations && sessionStartTimeMillis != 0L) {
            val sessionLengthSeconds =
                ((SystemClock.elapsedRealtime() - sessionStartTimeMillis) / 1000)
                    .coerceAtLeast(0L)
                    .toInt()
            analytics.trackEvent(
                AnalyticsEvents.SESSION_LENGTH_EVENT,
                mapOf(
                    AnalyticsEvents.SESSION_LENGTH_TIME_VALUE to sessionLengthSeconds,
                    AnalyticsEvents.SESSION_BUCKET to
                        SessionBucket.forSeconds(sessionLengthSeconds).name,
                ),
            )
            sessionStartTimeMillis = 0L
        }
        locationController.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Called before onStop, so only persist across an actual recreation — otherwise a
        // background-then-killed process would restore this stale start time later and
        // inflate the next session's logged length.
        if (isChangingConfigurations) {
            outState.putLong(SAVED_SESSION_START_TIME_KEY, sessionStartTimeMillis)
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    private fun currentDisplayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /** The sheet's "Use Automatic Location": straight to auto if permitted, else ask first. */
    private fun requestAutoLocation() {
        if (hasLocationPermission()) {
            locationController.switchToAuto()
        } else {
            requestLocationPermission()
        }
    }

    /** The permanently-denied dialog's exit: this app's system settings page (v1). */
    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    companion object {
        private const val SAVED_SESSION_START_TIME_KEY = "saved_session_start_time"

        /** v1 `DiagnosticActivity.updateLocation`'s GPS-provider probe. */
        private fun gpsStatus(context: Context): GpsStatus {
            val manager =
                context.getSystemService(LocationManager::class.java)
                    ?: return GpsStatus.NO_GPS
            return try {
                when {
                    LocationManager.GPS_PROVIDER !in manager.allProviders -> GpsStatus.NO_GPS
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> GpsStatus.ENABLED
                    else -> GpsStatus.DISABLED
                }
            } catch (e: SecurityException) {
                GpsStatus.PERMISSION_DISABLED
            }
        }

        /** v1's `updateNetwork`, on the non-deprecated `NetworkCapabilities` API. */
        private fun networkStatus(context: Context): NetworkStatus {
            val manager =
                context.getSystemService(ConnectivityManager::class.java)
                    ?: return NetworkStatus.DISCONNECTED
            return try {
                val activeNetwork = manager.activeNetwork ?: return NetworkStatus.DISCONNECTED
                val capabilities =
                    manager.getNetworkCapabilities(activeNetwork)
                        ?: return NetworkStatus.DISCONNECTED
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                        NetworkStatus.CONNECTED_WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                        NetworkStatus.CONNECTED_CELL
                    else -> NetworkStatus.CONNECTED
                }
            } catch (e: SecurityException) {
                NetworkStatus.DISCONNECTED
            }
        }
    }
}
