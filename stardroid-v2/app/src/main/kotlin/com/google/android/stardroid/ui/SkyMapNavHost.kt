/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui

import android.opengl.GLSurfaceView
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.android.stardroid.R
import com.google.android.stardroid.camera.SkyCameraPreview
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.ui.calibration.CompassCalibrationScreen
import com.google.android.stardroid.ui.calibration.CompassCalibrationViewModel
import com.google.android.stardroid.ui.diagnostics.DiagnosticsScreen
import com.google.android.stardroid.ui.diagnostics.DiagnosticsViewModel
import com.google.android.stardroid.ui.gallery.GalleryScreen
import com.google.android.stardroid.ui.gallery.GalleryViewModel
import com.google.android.stardroid.ui.help.HelpScreen
import com.google.android.stardroid.ui.help.WhatsNewScreen
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.map.MapScreen
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.objectinfo.ImageExpandOverlay
import com.google.android.stardroid.ui.objectinfo.MoonWidgetPromoRow
import com.google.android.stardroid.ui.objectinfo.ObjectInfoCard
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.onboarding.WelcomeScreen
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.settings.SettingsScreen
import com.google.android.stardroid.ui.settings.SettingsViewModel
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import kotlinx.coroutines.launch

/** The Navigation-Compose destinations (screens-and-startup.md's graph). */
object Routes {
    const val MAP = "map"
    const val WELCOME = "welcome?replay={replay}"
    const val SETTINGS = "settings"
    const val GALLERY = "gallery"
    const val DIAGNOSTICS = "diagnostics"
    const val HELP = "help"
    const val WHATS_NEW = "whatsnew"
    const val CALIBRATION = "calibration/{userInitiated}"

    fun calibration(userInitiated: Boolean) = "calibration/$userInitiated"

    fun welcomeReplay() = "welcome?replay=true"
}

/** Presence of the sensors the warm welcome's third slide reports on. */
data class SensorPresence(
    val hasCompass: Boolean,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
)

/** What [ImageExpandOverlay] needs from an [ObjectInfo] — small enough to survive rotation. */
private data class ExpandedGalleryImage(val imageRef: String, val name: String, val credit: String?)

private val ExpandedGalleryImageSaver =
    Saver<ExpandedGalleryImage?, List<String?>>(
        save = { it?.let { image -> listOf(image.imageRef, image.name, image.credit) } },
        restore = { saved ->
            val imageRef = saved.getOrNull(0) ?: return@Saver null
            ExpandedGalleryImage(imageRef, requireNotNull(saved.getOrNull(1)), saved.getOrNull(2))
        },
    )

/**
 * The single-activity navigation graph (screens-and-startup.md): the map is the start
 * destination — or the warm welcome on gated fresh installs — and the former full-screen
 * overlays (settings, gallery, diagnostics, calibration) become destinations. Dialogs and
 * sheets (search, time travel, object info, location, help) stay owned by the map.
 */
@Composable
fun SkyMapNavHost(
    navController: NavHostController,
    startOnWelcome: Boolean,
    glSurfaceView: GLSurfaceView,
    mapViewModel: MapViewModel,
    layersViewModel: LayersViewModel,
    timeTravelViewModel: TimeTravelViewModel,
    searchViewModel: SearchViewModel,
    objectInfoViewModel: ObjectInfoViewModel,
    locationViewModel: LocationViewModel,
    settingsViewModel: SettingsViewModel,
    galleryViewModel: GalleryViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    calibrationViewModel: CompassCalibrationViewModel,
    sensorPresence: SensorPresence,
    sensorWarningSuppressed: Boolean,
    onWelcomeFinished: () -> Unit,
    onWelcomeSkipped: () -> Unit = onWelcomeFinished,
    onWelcomeStarted: () -> Unit = {},
    onWelcomeSlideViewed: (Int) -> Unit = {},
    onRequestLocationPermission: () -> Unit,
    onRequestAutoLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    arCamera: SkyCameraPreview,
    hasCameraPermission: () -> Boolean,
    onRequestCameraPermission: () -> Unit,
    experimentConfig: ExperimentConfig = ExperimentConfig.Static,
) {
    val nightMode by mapViewModel.nightMode.collectAsStateWithLifecycle()
    // Owned here, hosted by the map screen: destinations that pop back to the map (e.g. the
    // calibration-complete notice) can enqueue a snackbar that shows once the map is up.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    NavHost(
        navController = navController,
        startDestination = if (startOnWelcome) Routes.WELCOME else Routes.MAP,
    ) {
        composable(Routes.MAP) {
            MapScreen(
                glSurfaceView,
                snackbarHostState,
                mapViewModel,
                layersViewModel,
                timeTravelViewModel,
                searchViewModel,
                objectInfoViewModel,
                locationViewModel,
                // The map keeps collecting the calibration nudge (v1 ran the monitor for
                // the map activity's life).
                calibrationViewModel,
                sensorWarningSuppressed = sensorWarningSuppressed,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
                onOpenTutorial = { navController.navigate(Routes.welcomeReplay()) },
                onOpenHelp = { navController.navigate(Routes.HELP) },
                onOpenWhatsNew = { navController.navigate(Routes.WHATS_NEW) },
                onOpenCalibration = { userInitiated ->
                    navController.navigate(Routes.calibration(userInitiated))
                },
                onRequestLocationPermission = onRequestLocationPermission,
                onRequestAutoLocation = onRequestAutoLocation,
                onOpenAppSettings = onOpenAppSettings,
                arCamera = arCamera,
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = onRequestCameraPermission,
                experimentConfig = experimentConfig,
            )
        }

        composable(
            Routes.WELCOME,
            arguments =
                listOf(
                    navArgument("replay") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
        ) { entry ->
            // Two ways in: the first-run flow starts here with no map below (navigate to the
            // map, dropping the welcome), and the overflow sheet's Tutorial replays it over a
            // live map (just pop back to it). Replays stay out of the D49 first-run funnel —
            // no analytics, no re-marking the seen preference.
            val replay = entry.arguments?.getBoolean("replay") == true
            val leaveWelcome = {
                if (!navController.popBackStack(Routes.MAP, inclusive = false)) {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            }
            WelcomeScreen(
                hasCompass = sensorPresence.hasCompass,
                hasAccelerometer = sensorPresence.hasAccelerometer,
                hasGyroscope = sensorPresence.hasGyroscope,
                nightMode = nightMode,
                onFinished = {
                    if (!replay) onWelcomeFinished()
                    leaveWelcome()
                },
                onSkip = {
                    if (!replay) onWelcomeSkipped()
                    leaveWelcome()
                },
                onStarted = if (replay) ({}) else onWelcomeStarted,
                onSlideViewed = if (replay) ({}) else onWelcomeSlideViewed,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsViewModel,
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                galleryViewModel,
                nightMode = nightMode,
                onItemClick = { objectInfoViewModel.show(it.id) },
                onBack = { navController.popBackStack() },
            )
            // The info card and its photo expansion are dialogs, so they float above the
            // grid; Find lands on the map with the search engaged (v1's gallery→search
            // intent, D46).
            val card by objectInfoViewModel.card.collectAsStateWithLifecycle()
            val riseSet by objectInfoViewModel.riseSet.collectAsStateWithLifecycle()
            val moonWidgetPromo by objectInfoViewModel.moonWidgetPromo.collectAsStateWithLifecycle()
            var expandedImage by
                rememberSaveable(stateSaver = ExpandedGalleryImageSaver) {
                    mutableStateOf<ExpandedGalleryImage?>(null)
                }
            card?.let { info ->
                ObjectInfoCard(
                    info = info,
                    riseSet = riseSet,
                    nightMode = nightMode,
                    onSeeAlso = { link -> objectInfoViewModel.show(link.id) },
                    onFind =
                        if (objectInfoViewModel.isFindable(info)) {
                            {
                                objectInfoViewModel.dismiss()
                                navController.popBackStack(Routes.MAP, inclusive = false)
                                searchViewModel.select(objectInfoViewModel.asSearchHit(it))
                            }
                        } else {
                            null
                        },
                    onImageTap = { tapped ->
                        tapped.imageRef?.let { imageRef ->
                            expandedImage =
                                ExpandedGalleryImage(imageRef, tapped.name, tapped.imageCredit)
                        }
                    },
                    onDismiss = { objectInfoViewModel.dismiss() },
                    promoRow =
                        if (moonWidgetPromo) {
                            {
                                MoonWidgetPromoRow(
                                    onDone = objectInfoViewModel::dismissMoonWidgetPromo,
                                )
                            }
                        } else {
                            null
                        },
                )
            }
            expandedImage?.let { image ->
                ImageExpandOverlay(
                    imageRef = image.imageRef,
                    name = image.name,
                    credit = image.credit,
                    nightMode = nightMode,
                    onDismiss = { expandedImage = null },
                )
            }
            // A card left open belongs to this screen; don't let it linger over the map.
            // Keyed on the Activity (not Unit) so a config change like rotation doesn't
            // fire onDispose and prematurely close a card the user never navigated away from.
            val objectInfoContext = LocalContext.current
            DisposableEffect(objectInfoContext) {
                onDispose {
                    var current = objectInfoContext
                    while (current is android.content.ContextWrapper) {
                        if (current is android.app.Activity) break
                        current = current.baseContext
                    }
                    val activity = current as? android.app.Activity
                    if (activity == null || !activity.isChangingConfigurations) {
                        objectInfoViewModel.dismiss()
                    }
                }
            }
        }

        composable(Routes.HELP) {
            HelpScreen(nightMode = nightMode, onBack = { navController.popBackStack() })
        }

        composable(Routes.WHATS_NEW) {
            WhatsNewScreen(nightMode = nightMode, onBack = { navController.popBackStack() })
        }

        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                diagnosticsViewModel,
                nightMode = nightMode,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.CALIBRATION,
            arguments =
                listOf(
                    navArgument("userInitiated") { type = NavType.BoolType },
                ),
        ) { entry ->
            val userInitiated = entry.arguments?.getBoolean("userInitiated") ?: true
            val context = LocalContext.current
            CompassCalibrationScreen(
                calibrationViewModel,
                nightMode = nightMode,
                userInitiated = userInitiated,
                onCalibrated = {
                    // v1 AUTO_DISMISSABLE: a compass back at HIGH closes the nudge itself;
                    // the notice queues on the shared host and shows once the map is back.
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.calibration_complete_toast),
                        )
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
