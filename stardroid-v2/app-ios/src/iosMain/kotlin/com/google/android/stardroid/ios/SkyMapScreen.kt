/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.time.TimeTravelState
import com.google.android.stardroid.ui.diagnostics.DiagnosticsScreen
import com.google.android.stardroid.ui.diagnostics.DiagnosticsViewModel
import com.google.android.stardroid.ui.help.HelpLink
import com.google.android.stardroid.ui.help.HelpScreen
import com.google.android.stardroid.ui.help.WhatsNewScreen
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.LocationSheet
import com.google.android.stardroid.ui.location.LocationStateDialogs
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.location.ManualLocationEntryDialog
import com.google.android.stardroid.ui.location.rememberLocationSetMessage
import com.google.android.stardroid.ui.map.LayersSheet
import com.google.android.stardroid.ui.map.MapChrome
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.map.OverflowSheet
import com.google.android.stardroid.ui.map.ReferenceFrame
import com.google.android.stardroid.ui.objectinfo.EclipseRow
import com.google.android.stardroid.ui.objectinfo.ImageExpandOverlay
import com.google.android.stardroid.ui.objectinfo.ObjectInfoCard
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.onboarding.WelcomeScreen
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.sun_wont_set_message
import com.google.android.stardroid.ui.resources.support_email
import com.google.android.stardroid.ui.search.SearchControlBar
import com.google.android.stardroid.ui.search.SearchDialog
import com.google.android.stardroid.ui.search.SearchGeometry
import com.google.android.stardroid.ui.search.SearchOverlay
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.settings.SettingsScreen
import com.google.android.stardroid.ui.settings.SettingsViewModel
import com.google.android.stardroid.ui.startup.EulaScreen
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.startup.WhatsNewDialog
import com.google.android.stardroid.ui.theme.SkyMapTheme
import com.google.android.stardroid.ui.timetravel.TimeTravelDialog
import com.google.android.stardroid.ui.timetravel.TimeTravelFlash
import com.google.android.stardroid.ui.timetravel.TimeTravelPlayer
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

/**
 * The iOS app's root, for Swift to host: the Compose Multiplatform UI (D134) over the Metal map.
 *
 * The map carries the shared chrome (MapChrome: the layer rail, the action cluster, the HUD) and
 * the shared Layers sheet, search, time travel, object info (tap the sky) and location. This is a
 * stand-in for Android's MapScreen, which composes every other screen and so moves last (D137);
 * until then the overflow menu offers only the destinations iOS has screens for. Everything here
 * is wired as Android wires it.
 *
 * Android's startup gating: the EULA blocks everything until accepted, then the warm welcome on
 * a first run, and What's New on upgrades.
 */
fun skyMapViewController(): UIViewController {
    val graph = IosAppGraph()
    val mapViewModel = graph.mapViewModel()
    val map = MapViewController(graph, mapViewModel)
    val startup = graph.startupViewModel()
    val layers = graph.layersViewModel()
    val search = graph.searchViewModel(mapViewModel)
    val timeTravel = graph.timeTravelViewModel()
    val objectInfo = graph.objectInfoViewModel()
    val location = graph.locationViewModel()
    val settings = graph.settingsViewModel()
    val diagnostics = graph.diagnosticsViewModel(mapViewModel, map.rendererInfo)
    return ComposeUIViewController {
        SkyMapScreen(
            map,
            mapViewModel,
            layers,
            search,
            timeTravel,
            objectInfo,
            location,
            settings,
            diagnostics,
            startup,
            onRequestAutoLocation = graph::requestAutoLocation,
            onOpenAppSettings = graph::openAppSettings,
            motionHardware =
                MotionHardware(graph.hasCompass, graph.hasAccelerometer, graph.hasGyroscope),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkyMapScreen(
    map: MapViewController,
    mapViewModel: MapViewModel,
    layersViewModel: LayersViewModel,
    searchViewModel: SearchViewModel,
    timeTravelViewModel: TimeTravelViewModel,
    objectInfoViewModel: ObjectInfoViewModel,
    locationViewModel: LocationViewModel,
    settingsViewModel: SettingsViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    startup: StartupViewModel,
    onRequestAutoLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    motionHardware: MotionHardware,
) {
    val nightMode by mapViewModel.nightMode.collectAsState()
    val gates by startup.state.collectAsState()
    val hudState by mapViewModel.hudState.collectAsState()
    val hudEnabled by layersViewModel.hudEnabled.collectAsState()
    val toggles by layersViewModel.toggles.collectAsState()
    val referenceFrame by mapViewModel.referenceFrame.collectAsState()
    var showLayersSheet by remember { mutableStateOf(false) }
    var expandLayer by remember { mutableStateOf<LayerId?>(null) }
    val camera by mapViewModel.camera.collectAsState()
    val searchTarget by searchViewModel.target.collectAsState()
    var showSearchDialog by remember { mutableStateOf(false) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val timeTravelState by timeTravelViewModel.state.collectAsState()
    var showTimeTravelDialog by remember { mutableStateOf(false) }
    var timeTravelPlayerHeightPx by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val objectInfoCard by objectInfoViewModel.card.collectAsState()
    val objectRiseSet by objectInfoViewModel.riseSet.collectAsState()
    val showingMoon by objectInfoViewModel.showingMoon.collectAsState()
    val lunarEclipse by objectInfoViewModel.lunarEclipse.collectAsState()
    var expandedImage by remember { mutableStateOf<ObjectInfo?>(null) }
    val locationState by locationViewModel.state.collectAsState()
    val manualLocationMode by locationViewModel.manualMode.collectAsState()
    var showOverflowSheet by remember { mutableStateOf(false) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    // The full-screen pages over the map, standing in for Android's navigation routes: a stack,
    // so back from a page opened from another (Settings from Help) returns there, as Android pops.
    var pages by remember { mutableStateOf(listOf<Page>()) }
    val page = pages.lastOrNull()

    fun open(next: Page) {
        pages = pages + next
    }

    fun back() {
        pages = pages.dropLast(1)
    }
    val diagnosticsPlatform = remember { iosDiagnosticsPlatform() }
    val supportEmail = stringResource(Res.string.support_email)
    // A still tap on the sky identifies what is there, as on Android. UIKit reports points;
    // Compose pixels are points at the screen's scale, which is its density here.
    val density = LocalDensity.current.density
    // The lambda reads the camera, frame and size states when the tap arrives, so it is set once.
    DisposableEffect(map) {
        map.onTap = { xPoints, yPoints ->
            objectInfoViewModel.onSkyTap(
                xPx = (xPoints * density).toFloat(),
                yPx = (yPoints * density).toFloat(),
                widthPx = screenSize.width,
                heightPx = screenSize.height,
                camera = camera,
                sensorFrame = referenceFrame == ReferenceFrame.SENSOR,
                densityDpPerPx = density,
            )
        }
        onDispose { map.onTap = null }
    }
    val scope = rememberCoroutineScope()
    // The per-event search target, aimed once time travel's clock has arrived.
    LaunchedEffect(Unit) {
        timeTravelViewModel.searchTargets.collect { searchViewModel.selectById(it) }
    }
    // v1's "Location set to X" toast, as a snackbar, on every fresh fix or manual entry.
    val locationSetMessage = rememberLocationSetMessage()
    LaunchedEffect(Unit) {
        locationViewModel.toasts.collect { toast ->
            launch { snackbarHostState.showSnackbar(locationSetMessage(toast)) }
        }
    }
    // Choosing a result closes the dialog and, in manual mode, turns the sky to it (v1).
    LaunchedEffect(searchTarget) {
        searchTarget?.let { target ->
            showSearchDialog = false
            mapViewModel.aimAt(target.direction, target.fovDeg)
        }
    }
    SkyMapTheme(nightMode) {
        Box(Modifier.fillMaxSize().onSizeChanged { screenSize = it }) {
            // NonCooperative: the map's own recognizers get every touch at once, without
            // Compose's ~150 ms cooperative hold (D134's spike).
            UIKitViewController(
                factory = { map.viewController },
                modifier = Modifier.fillMaxSize(),
                properties =
                    UIKitInteropProperties(
                        interactionMode = UIKitInteropInteractionMode.NonCooperative,
                    ),
            )
            // Over the sky and under the controls, as on Android.
            TimeTravelFlash(
                effects = timeTravelViewModel.effects,
                onNotice = { snackbarHostState.showSnackbar(it) },
                modifier = Modifier.matchParentSize(),
            )
            // As on Android: the overlay points the way to the target without taking touches, the
            // bar owns the bottom edge, and the chrome steps aside until the search ends.
            searchTarget?.let { target ->
                val found =
                    SearchGeometry.isTargetFound(
                        camera,
                        screenSize.width,
                        screenSize.height,
                        target.direction,
                    )
                SearchOverlay(
                    camera = camera,
                    target = target,
                    nightMode = nightMode,
                    found = found,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    SearchControlBar(
                        targetName = target.name,
                        found = found,
                        onCancel = searchViewModel::cancelSearch,
                    )
                }
            }
            if (searchTarget == null) {
                MapChrome(
                    toggles = toggles,
                    nightMode = nightMode,
                    referenceFrame = referenceFrame,
                    sensorsAvailable = mapViewModel.sensorsAvailable,
                    onToggleLayer = { id, enabled -> layersViewModel.setEnabled(id, enabled) },
                    onToggleReferenceFrame = mapViewModel::toggleReferenceFrame,
                    onToggleNightMode = { mapViewModel.setNightMode(!nightMode) },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenTimeTravel = { showTimeTravelDialog = true },
                    onOpenLayersSheet = {
                        expandLayer = null
                        showLayersSheet = true
                    },
                    onCustomizeLayer = { id ->
                        expandLayer = id
                        showLayersSheet = true
                    },
                    onOpenOverflow = { showOverflowSheet = true },
                    // Reset has no undo snackbar yet; the correction it clears only arises in AR
                    // mode, which iOS does not have.
                    hudState = hudState.takeIf { hudEnabled },
                    // The player spans the top in portrait (the app's only orientation), so the HUD
                    // steps down below it while travel is engaged.
                    hudTopClearance =
                        if (timeTravelState != TimeTravelState.REAL_TIME) {
                            with(LocalDensity.current) { timeTravelPlayerHeightPx.toDp() } + 8.dp
                        } else {
                            0.dp
                        },
                    onResetAlignment = mapViewModel::resetAlignment,
                    shareEnabled = false,
                )
            }
            // The player shows from the moment travel engages and hides when the user heads home.
            if (timeTravelState != TimeTravelState.REAL_TIME) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(8.dp)
                        .onSizeChanged { timeTravelPlayerHeightPx = it.height },
                ) {
                    TimeTravelPlayer(timeTravelViewModel)
                }
            }
            if (showTimeTravelDialog) {
                val sunWontSet = stringResource(Res.string.sun_wont_set_message)
                TimeTravelDialog(
                    timeTravelViewModel,
                    onSunWontSet = { scope.launch { snackbarHostState.showSnackbar(sunWontSet) } },
                    onDismiss = { showTimeTravelDialog = false },
                )
            }
            objectInfoCard?.let { info ->
                ObjectInfoCard(
                    info = info,
                    riseSet = objectRiseSet,
                    nightMode = nightMode,
                    onSeeAlso = { link -> objectInfoViewModel.show(link.id) },
                    // No Find: a card opened by tapping the sky is an object already found.
                    onFind = null,
                    onImageTap = { tapped -> if (tapped.imageRef != null) expandedImage = tapped },
                    onDismiss = { objectInfoViewModel.dismiss() },
                    eclipseRow =
                        if (showingMoon) {
                            { EclipseRow(circumstances = lunarEclipse) }
                        } else {
                            null
                        },
                )
            }
            expandedImage?.let { image ->
                ImageExpandOverlay(
                    imageRef = checkNotNull(image.imageRef),
                    name = image.name,
                    credit = image.imageCredit,
                    nightMode = nightMode,
                    onDismiss = { expandedImage = null },
                )
            }
            // Above the chrome row, as on Android, so a snackbar never covers the action buttons.
            SnackbarHost(
                snackbarHostState,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
            )
            if (showSearchDialog) {
                SearchDialog(
                    searchViewModel,
                    onDismiss = {
                        showSearchDialog = false
                        // A dismissed dialog abandons the session; reopening starts clean.
                        searchViewModel.setQuery("")
                    },
                )
            }
            if (showLayersSheet) {
                LayersSheet(
                    layersViewModel = layersViewModel,
                    onDismiss = { showLayersSheet = false },
                    sensorsAvailable = mapViewModel.sensorsAvailable,
                    expandLayer = expandLayer,
                )
            }
            if (showOverflowSheet) {
                OverflowSheet(
                    onShareSky = null,
                    shareEnabled = false,
                    onOpenGallery = null,
                    onOpenWidgets = null,
                    widgetsEnabled = false,
                    onOpenLocation = {
                        showOverflowSheet = false
                        showLocationSheet = true
                    },
                    onOpenCalibration = null,
                    onOpenTutorial = {
                        showOverflowSheet = false
                        open(Page.TUTORIAL)
                    },
                    onOpenHelp = {
                        showOverflowSheet = false
                        open(Page.HELP)
                    },
                    onOpenWhatsNew = {
                        showOverflowSheet = false
                        open(Page.WHATS_NEW)
                    },
                    onOpenSettings = {
                        showOverflowSheet = false
                        open(Page.SETTINGS)
                    },
                    onDismiss = { showOverflowSheet = false },
                )
            }
            if (showLocationSheet) {
                LocationSheet(
                    locationViewModel,
                    nightMode = nightMode,
                    mapApiKey = GEOAPIFY_MAPS_API_KEY.takeUnless { it == "unset" || it.isEmpty() },
                    onRequestAutoLocation = onRequestAutoLocation,
                    onEnterManually = {
                        locationViewModel.resetManualEntry()
                        showManualLocationDialog = true
                        showLocationSheet = false
                    },
                    onDismiss = { showLocationSheet = false },
                )
            }
            if (showManualLocationDialog) {
                ManualLocationEntryDialog(
                    locationViewModel,
                    onDismiss = { showManualLocationDialog = false },
                )
            }
            // The edge swipe closes a page, as Android's system back pops its destination.
            BackHandler(enabled = page != null) { back() }
            when (page) {
                Page.HELP ->
                    HelpScreen(
                        nightMode = nightMode,
                        onBack = ::back,
                        // Links to screens iOS doesn't have yet stay inert until they arrive.
                        onNavigate = { destination ->
                            when (destination) {
                                HelpLink.Destination.SETTINGS -> open(Page.SETTINGS)
                                HelpLink.Destination.APP_SETTINGS -> onOpenAppSettings()
                                HelpLink.Destination.TUTORIAL -> open(Page.TUTORIAL)
                                else -> Unit
                            }
                        },
                    )
                Page.WHATS_NEW -> WhatsNewScreen(nightMode = nightMode, onBack = ::back)
                Page.SETTINGS ->
                    SettingsScreen(
                        settingsViewModel,
                        onBack = ::back,
                        onOpenDiagnostics = { open(Page.DIAGNOSTICS) },
                    )
                Page.DIAGNOSTICS ->
                    DiagnosticsScreen(
                        diagnosticsViewModel,
                        nightMode = nightMode,
                        experimentConfig = ExperimentConfig.Static,
                        platform = diagnosticsPlatform,
                        onBack = ::back,
                        onSendReport = { subject, body ->
                            sendDiagnosticsReport(supportEmail, subject, body)
                        },
                    )
                // A replay: no analytics funnel, and nothing re-marked as seen (as on Android).
                Page.TUTORIAL ->
                    WelcomeScreen(
                        hasCompass = motionHardware.hasCompass,
                        hasAccelerometer = motionHardware.hasAccelerometer,
                        hasGyroscope = motionHardware.hasGyroscope,
                        nightMode = nightMode,
                        satellitesEnabled = false,
                        onFinished = ::back,
                    )
                null -> Unit
            }
            val current = gates
            // The location dialogs belong to the map, which the startup screens and the pages
            // cover (on Android they are separate destinations, where the map isn't composed).
            val onMap =
                current != null && !current.needsEula && !current.needsWarmWelcome && page == null
            if (onMap) {
                LocationStateDialogs(
                    locationState = locationState,
                    manualLocationMode = manualLocationMode,
                    locationViewModel = locationViewModel,
                    // The system prompt, shown after the EULA, is iOS's rationale.
                    onRequestLocationPermission = null,
                    onOpenAppSettings = onOpenAppSettings,
                    onEnterManually = {
                        locationViewModel.resetManualEntry()
                        showManualLocationDialog = true
                    },
                )
            }
            when {
                // Android holds its splash for these first values; black, until the EULA or
                // the map can show.
                current == null -> Box(Modifier.fillMaxSize().background(Color.Black))
                current.needsEula ->
                    EulaScreen(
                        nightMode = nightMode,
                        onAccept = startup::acceptEula,
                        // iOS apps don't quit themselves: Accept is the only way on.
                        onDecline = null,
                    )
                else -> {
                    if (current.needsWarmWelcome) {
                        WelcomeScreen(
                            hasCompass = motionHardware.hasCompass,
                            hasAccelerometer = motionHardware.hasAccelerometer,
                            hasGyroscope = motionHardware.hasGyroscope,
                            nightMode = nightMode,
                            satellitesEnabled = false,
                            onFinished = startup::completeWarmWelcome,
                            onSkip = startup::skipWarmWelcome,
                            onStarted = startup::warmWelcomeStarted,
                            onSlideViewed = startup::warmWelcomeSlideViewed,
                        )
                    }
                    // Upgrades only, and ahead of a still-pending tour, as on Android.
                    if (current.needsWhatsNew &&
                        (!current.needsWarmWelcome || current.needsWhatsNewDuringWarmWelcome)
                    ) {
                        WhatsNewDialog(nightMode = nightMode, onDismiss = startup::dismissWhatsNew)
                    }
                }
            }
        }
    }
}

/** The full-screen pages the iOS host shows over the map (Android's navigation routes). */
private enum class Page {
    HELP,
    WHATS_NEW,
    SETTINGS,

    /** Reached from Settings, as on Android. */
    DIAGNOSTICS,

    /** The warm welcome, replayed from the overflow sheet or Help. */
    TUTORIAL,
}

/** What the welcome's sensor check reports, from Core Motion. */
private data class MotionHardware(
    val hasCompass: Boolean,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
)
