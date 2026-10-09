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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.ui.calibration.CompassCalibrationScreen
import com.google.android.stardroid.ui.calibration.CompassCalibrationViewModel
import com.google.android.stardroid.ui.diagnostics.DiagnosticsScreen
import com.google.android.stardroid.ui.diagnostics.DiagnosticsViewModel
import com.google.android.stardroid.ui.gallery.GalleryScreen
import com.google.android.stardroid.ui.gallery.GalleryViewModel
import com.google.android.stardroid.ui.help.HelpLink
import com.google.android.stardroid.ui.help.HelpScreen
import com.google.android.stardroid.ui.help.WhatsNewScreen
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.map.MapScreen
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.objectinfo.ImageExpandOverlay
import com.google.android.stardroid.ui.objectinfo.ObjectInfoCard
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.onboarding.WelcomeScreen
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.calibration_complete_toast
import com.google.android.stardroid.ui.resources.support_email
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.settings.SettingsScreen
import com.google.android.stardroid.ui.settings.SettingsViewModel
import com.google.android.stardroid.ui.startup.EulaScreen
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.startup.VersionBanner
import com.google.android.stardroid.ui.startup.WhatsNewDialog
import com.google.android.stardroid.ui.startup.appVersionName
import com.google.android.stardroid.ui.theme.SkyMapTheme
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

/**
 * The iOS app's root, for Swift to host: the Compose Multiplatform UI (D134) over the Metal map.
 *
 * The map is the shared MapScreen (D137) over the Metal sky, whose UIKit recognizers report taps
 * back to it. Around it, this host does what Android's MainActivity and nav host do: the startup
 * gating (the EULA blocks everything until accepted, then the warm welcome on a first run, and
 * What's New on upgrades) and the full-screen pages, which stand in for Android's navigation
 * destinations.
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
    val gallery = graph.galleryViewModel()
    val calibration = graph.calibrationViewModel()
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
            gallery,
            calibration,
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
    galleryViewModel: GalleryViewModel,
    calibrationViewModel: CompassCalibrationViewModel,
    startup: StartupViewModel,
    onRequestAutoLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    motionHardware: MotionHardware,
) {
    val nightMode by mapViewModel.nightMode.collectAsState()
    val gates by startup.state.collectAsState()
    val sensorWarningSuppressed by startup.suppressMissingSensorWarning.collectAsState()
    // Owned here and shown by the map, so a page that closes back to the map (calibration's
    // calibrated notice) can leave a snackbar there, as Android's nav host does.
    val snackbarHostState = remember { SnackbarHostState() }
    // The full-screen pages over the map, standing in for Android's navigation routes: a stack,
    // so back from a page opened from another (Settings from Help) returns there, as Android pops.
    var showBanner by remember { mutableStateOf(true) }
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
    val scope = rememberCoroutineScope()
    SkyMapTheme(nightMode) {
        Box(Modifier.fillMaxSize()) {
            val current = gates
            // The startup screens and the pages cover the map, and its own dialogs wait for them
            // (on Android the pages are separate destinations, where the map isn't composed).
            val covered =
                current == null || current.needsEula || current.needsWarmWelcome || page != null
            MapScreen(
                snackbarHostState,
                mapViewModel,
                layersViewModel,
                timeTravelViewModel,
                searchViewModel,
                objectInfoViewModel,
                locationViewModel,
                calibrationViewModel,
                sensorWarningSuppressed = sensorWarningSuppressed,
                onOpenSettings = { open(Page.SETTINGS) },
                onOpenGallery = { open(Page.GALLERY) },
                onOpenTutorial = { open(Page.TUTORIAL) },
                onOpenHelp = { open(Page.HELP) },
                onOpenWhatsNew = { open(Page.WHATS_NEW) },
                onOpenCalibration = { userInitiated ->
                    open(if (userInitiated) Page.CALIBRATION else Page.CALIBRATION_PROMPT)
                },
                // The system prompt, shown after the EULA, is iOS's rationale.
                onRequestLocationPermission = null,
                onRequestAutoLocation = onRequestAutoLocation,
                onOpenAppSettings = onOpenAppSettings,
                covered = covered,
                // Calibrate lives in Diagnostics (and Help links to it).
                calibrationInMenu = false,
                mapApiKey = GEOAPIFY_MAPS_API_KEY.takeUnless { it == "unset" || it.isEmpty() },
            ) { onTap, onDoubleTap ->
                MetalSky(map, onTap, onDoubleTap)
            }
            // The edge swipe closes a page, as Android's system back pops its destination.
            BackHandler(enabled = page != null) { back() }
            when (page) {
                Page.HELP ->
                    HelpScreen(
                        nightMode = nightMode,
                        onBack = ::back,
                        onNavigate = { destination ->
                            when (destination) {
                                HelpLink.Destination.SETTINGS -> open(Page.SETTINGS)
                                HelpLink.Destination.DIAGNOSTICS -> open(Page.DIAGNOSTICS)
                                HelpLink.Destination.CALIBRATE -> open(Page.CALIBRATION)
                                HelpLink.Destination.GALLERY -> open(Page.GALLERY)
                                HelpLink.Destination.APP_SETTINGS -> onOpenAppSettings()
                                HelpLink.Destination.TUTORIAL -> open(Page.TUTORIAL)
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
                Page.GALLERY ->
                    GalleryPage(
                        galleryViewModel,
                        objectInfoViewModel,
                        nightMode = nightMode,
                        onFind = { hit ->
                            pages = emptyList()
                            searchViewModel.select(hit)
                        },
                        onBack = ::back,
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
                        onOpenCalibration = { open(Page.CALIBRATION) },
                    )
                Page.CALIBRATION, Page.CALIBRATION_PROMPT -> {
                    val calibrated = stringResource(Res.string.calibration_complete_toast)
                    CompassCalibrationScreen(
                        calibrationViewModel,
                        nightMode = nightMode,
                        userInitiated = page == Page.CALIBRATION,
                        demoVideoUrl = CALIBRATION_VIDEO_URL,
                        // The prompt closes itself once the compass reads High, as on Android.
                        onCalibrated = {
                            scope.launch { snackbarHostState.showSnackbar(calibrated) }
                            back()
                        },
                        onBack = ::back,
                    )
                }
                // A replay: no analytics funnel, and nothing re-marked as seen (as on Android).
                // Finishing or skipping lands on the map, taking Help with it if Help opened
                // it, as Android's replay pops to the map; back still returns to Help.
                Page.TUTORIAL ->
                    WelcomeScreen(
                        hasCompass = motionHardware.hasCompass,
                        hasAccelerometer = motionHardware.hasAccelerometer,
                        hasGyroscope = motionHardware.hasGyroscope,
                        nightMode = nightMode,
                        satellitesEnabled = false,
                        onFinished = { pages = emptyList() },
                    )
                null -> Unit
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
            // The branded version banner, once per launch, over everything while the app loads,
            // as on Android; the launch screen is the same navy, so the banner continues it.
            if (showBanner) {
                VersionBanner(versionName = appVersionName(), onFinished = { showBanner = false })
            }
        }
    }
}

/**
 * The Metal sky, for MapScreen's sky slot. Its own UIKit recognizers take every gesture and drive
 * the ViewModel directly; a still tap and a double tap come back to the map screen.
 */
@Composable
private fun BoxScope.MetalSky(
    map: MapViewController,
    onTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
) {
    // UIKit reports points; Compose pixels are points at the screen's scale, which is its
    // density here.
    val density = LocalDensity.current.density
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    DisposableEffect(map) {
        map.onTap = { xPoints, yPoints ->
            currentOnTap(Offset((xPoints * density).toFloat(), (yPoints * density).toFloat()))
        }
        map.onDoubleTap = { currentOnDoubleTap() }
        onDispose {
            map.onTap = null
            map.onDoubleTap = null
        }
    }
    // NonCooperative: the map's own recognizers get every touch at once, without Compose's
    // ~150 ms cooperative hold (D134's spike).
    UIKitViewController(
        factory = { map.viewController },
        modifier = Modifier.matchParentSize(),
        properties =
            UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
            ),
    )
}

/**
 * The gallery page, with its own object card over the grid, as Android's gallery destination
 * has; the map's card waits while a page covers it. Find lands on the map with the object found
 * (Android's gallery→search route, D46).
 */
@Composable
private fun GalleryPage(
    galleryViewModel: GalleryViewModel,
    objectInfoViewModel: ObjectInfoViewModel,
    nightMode: Boolean,
    onFind: (SearchHit) -> Unit,
    onBack: () -> Unit,
) {
    GalleryScreen(
        galleryViewModel,
        nightMode = nightMode,
        onItemClick = { objectInfoViewModel.show(it.id) },
        onBack = onBack,
    )
    val card by objectInfoViewModel.card.collectAsState()
    val riseSet by objectInfoViewModel.riseSet.collectAsState()
    var expandedImage by remember { mutableStateOf<ObjectInfo?>(null) }
    card?.let { info ->
        ObjectInfoCard(
            info = info,
            riseSet = riseSet,
            nightMode = nightMode,
            onSeeAlso = { link -> objectInfoViewModel.show(link.id) },
            onFind =
                if (objectInfoViewModel.isFindable(info)) {
                    {
                        val hit = objectInfoViewModel.asSearchHit(it)
                        objectInfoViewModel.dismiss()
                        onFind(hit)
                    }
                } else {
                    null
                },
            onImageTap = { tapped -> if (tapped.imageRef != null) expandedImage = tapped },
            onDismiss = { objectInfoViewModel.dismiss() },
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
    // A card left open belongs to the gallery; don't let it linger over the map.
    DisposableEffect(Unit) { onDispose { objectInfoViewModel.dismiss() } }
}

/** The full-screen pages the iOS host shows over the map (Android's navigation routes). */
private enum class Page {
    HELP,
    WHATS_NEW,
    SETTINGS,

    /** Reached from Settings, as on Android. */
    DIAGNOSTICS,
    GALLERY,

    /** The warm welcome, replayed from the overflow sheet or Help. */
    TUTORIAL,

    /** Compass calibration, opened from Diagnostics or Help. */
    CALIBRATION,

    /**
     * Compass calibration, opened by the low-accuracy monitor: it offers the opt-out, and closes
     * itself once the compass reads High.
     */
    CALIBRATION_PROMPT,
}

/** Android's figure-eight video for now; an iPhone one is to replace it. */
private const val CALIBRATION_VIDEO_URL = "https://www.youtube.com/watch?v=-Uq7AmSAjt8"

/** What the welcome's sensor check reports, from Core Motion. */
private data class MotionHardware(
    val hasCompass: Boolean,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
)
