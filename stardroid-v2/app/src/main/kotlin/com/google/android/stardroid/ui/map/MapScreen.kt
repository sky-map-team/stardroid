/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import android.content.res.Configuration
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.camera.SkyCameraPreview
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.sensors.CalibrationPrompt
import com.google.android.stardroid.share.SkyShare
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.time.TimeTravelState
import com.google.android.stardroid.ui.calibration.CompassCalibrationViewModel
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.AcquiringTimeoutDialog
import com.google.android.stardroid.ui.location.LocationPermanentlyDeniedDialog
import com.google.android.stardroid.ui.location.LocationRationaleDialog
import com.google.android.stardroid.ui.location.LocationSheet
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.location.ManualLocationEntryDialog
import com.google.android.stardroid.ui.objectinfo.EclipseRow
import com.google.android.stardroid.ui.objectinfo.ImageExpandOverlay
import com.google.android.stardroid.ui.objectinfo.MoonWidgetPromoRow
import com.google.android.stardroid.ui.objectinfo.ObjectInfoCard
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.objectinfo.SatellitePassRow
import com.google.android.stardroid.ui.search.SearchControlBar
import com.google.android.stardroid.ui.search.SearchDialog
import com.google.android.stardroid.ui.search.SearchGeometry
import com.google.android.stardroid.ui.search.SearchOverlay
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.timetravel.TimeTravelDialog
import com.google.android.stardroid.ui.timetravel.TimeTravelPlayer
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import com.google.android.stardroid.ui.timetravel.TravelEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min

/** What [ImageExpandOverlay] needs from an [ObjectInfo] — small enough to survive rotation. */
private data class ExpandedImage(val imageRef: String, val name: String, val credit: String?)

private val ExpandedImageSaver =
    Saver<ExpandedImage?, List<String?>>(
        save = { it?.let { image -> listOf(image.imageRef, image.name, image.credit) } },
        restore = { saved ->
            val imageRef = saved.getOrNull(0) ?: return@Saver null
            ExpandedImage(imageRef, requireNotNull(saved.getOrNull(1)), saved.getOrNull(2))
        },
    )

/**
 * The map screen: the GL sky behind a Compose control overlay. Gestures reach [MapViewModel]
 * as v1-shaped deltas (`MapMover`); the camera and scene flows reach the GL surface through
 * the `RenderBinder` wiring in `MainActivity`, not through composition.
 */
@Composable
fun MapScreen(
    glSurfaceView: GLSurfaceView,
    snackbarHostState: SnackbarHostState,
    mapViewModel: MapViewModel,
    layersViewModel: LayersViewModel,
    timeTravelViewModel: TimeTravelViewModel,
    searchViewModel: SearchViewModel,
    objectInfoViewModel: ObjectInfoViewModel,
    locationViewModel: LocationViewModel,
    calibrationViewModel: CompassCalibrationViewModel,
    sensorWarningSuppressed: Boolean,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenCalibration: (userInitiated: Boolean) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestAutoLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    // Through-camera mode (camera-ar-mode.md/D64): the CameraX edge and permission hooks.
    arCamera: SkyCameraPreview,
    hasCameraPermission: () -> Boolean,
    onRequestCameraPermission: () -> Unit,
    experimentConfig: ExperimentConfig = ExperimentConfig.Static,
) {
    // Through-camera mode and sharing are independently flagged. Camera-on is session state
    // (D64) and sharing is a one-shot action, so neither leaves anything stranded when a flag
    // flips off mid-flight: the entry points simply stop being offered.
    val cameraArEnabled = experimentConfig.isEnabled(Experiment.CAMERA_AR)
    val shareEnabled = experimentConfig.isEnabled(Experiment.SHARE_SKY)
    val referenceFrame by mapViewModel.referenceFrame.collectAsStateWithLifecycle()
    val nightMode by mapViewModel.nightMode.collectAsStateWithLifecycle()
    val timeTravelState by timeTravelViewModel.state.collectAsStateWithLifecycle()
    val camera by mapViewModel.camera.collectAsStateWithLifecycle()
    val searchTarget by searchViewModel.target.collectAsStateWithLifecycle()
    val objectInfoCard by objectInfoViewModel.card.collectAsStateWithLifecycle()
    val objectRiseSet by objectInfoViewModel.riseSet.collectAsStateWithLifecycle()
    val moonWidgetPromo by objectInfoViewModel.moonWidgetPromo.collectAsStateWithLifecycle()
    val locationState by locationViewModel.state.collectAsStateWithLifecycle()
    val manualLocationMode by locationViewModel.manualMode.collectAsStateWithLifecycle()
    // Saveable so the sheet/dialogs survive rotation — otherwise the dialog dismisses and the
    // rememberSaveable date/time inside it is thrown away with it. Settings, gallery,
    // diagnostics, and calibration are no longer local booleans here — they're Navigation
    // destinations (D48), reached through the onOpenX callbacks below.
    var showLayersSheet by rememberSaveable { mutableStateOf(false) }
    var showOverflowSheet by rememberSaveable { mutableStateOf(false) }
    var showTimeTravelDialog by rememberSaveable { mutableStateOf(false) }
    var showSearchDialog by rememberSaveable { mutableStateOf(false) }
    var showLocationSheet by rememberSaveable { mutableStateOf(false) }
    var showManualLocationDialog by rememberSaveable { mutableStateOf(false) }
    // Saveable via its own imageRef/name/credit strings — ObjectInfo itself isn't parcelable
    // and the full card doesn't need to survive rotation, just what the overlay renders.
    var expandedImage by rememberSaveable(stateSaver = ExpandedImageSaver) {
        mutableStateOf<ExpandedImage?>(null)
    }
    // v1 FullscreenControlsManager: the buttons flash briefly on entry so users learn where
    // they live, then hide; a tap on the sky toggles them so the map stays unobscured.
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var chromeToggledByUser by rememberSaveable { mutableStateOf(false) }
    // Null while the stored value is still loading — the timer waits rather than guessing.
    val chromeEverToggled by mapViewModel.chromeEverToggled.collectAsStateWithLifecycle()
    LaunchedEffect(chromeToggledByUser, chromeEverToggled) {
        // Until someone has worked the toggle at least once, the chrome stays up: a first-run
        // user who has never seen it hide has no way to know a sky tap brings it back. Once
        // they have done it themselves, the auto-hide resumes for every later run.
        if (!chromeToggledByUser && chromeEverToggled == true) {
            delay(INITIAL_CHROME_FLASH_MS)
            chromeVisible = false
        }
    }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    var timeTravelPlayerHeightPx by remember { mutableIntStateOf(0) }
    val screenShortSidePx by remember {
        derivedStateOf { min(screenSize.width, screenSize.height) }
    }
    // Read here rather than inside the gesture handler: `detectSkyGestures` runs outside any
    // Density scope, and identify needs px-per-dp to size its label-inclusive tap tolerance.
    val tapDensity = LocalDensity.current.density

    // Activating a target closes the dialog and, in manual mode, teleports to it (v1).
    LaunchedEffect(searchTarget) {
        searchTarget?.let { target ->
            showSearchDialog = false
            mapViewModel.aimAt(target.direction, target.fovDeg)
        }
    }
    // v1: the hardware BACK key ends an active search.
    BackHandler(enabled = searchTarget != null) { searchViewModel.cancelSearch() }

    // v1 ran SensorAccuracyMonitor for the life of the map activity: a badly calibrated
    // compass opens the calibration screen in its auto-dismissable form (or nudges via
    // snackbar, if the user opted out there — v1 toasted).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var gracePeriodPassed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!gracePeriodPassed) {
            delay(CALIBRATION_STARTUP_GRACE_MS)
            gracePeriodPassed = true
        }
    }
    LaunchedEffect(calibrationViewModel, lifecycleOwner, gracePeriodPassed) {
        if (gracePeriodPassed) {
            calibrationViewModel.prompts
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { prompt ->
                    when (prompt) {
                        CalibrationPrompt.SCREEN -> onOpenCalibration(false)
                        CalibrationPrompt.TOAST -> {
                            launch {
                                val result =
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.calibration_low_accuracy_toast,
                                        ),
                                        actionLabel =
                                            context.getString(R.string.snackbar_action_open),
                                        duration = SnackbarDuration.Long,
                                    )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onOpenCalibration(false)
                                }
                            }
                        }
                    }
                }
        }
    }

    // v1's time-travel theatrics: the translucent purple flash (`view_mask` +
    // `timetravelflash`, a 0 → 0.7 → 0 alpha pulse over 2 s) and the travel/return notices
    // (v1 toasted; snackbars follow the theme and red-shift in night mode) — then the
    // per-event search target, aimed once the clock has arrived.
    val flashAlpha = remember { Animatable(0f) }
    // Composition sees only this boolean (two recompositions per flash); the per-frame alpha
    // is read in the draw phase below.
    var flashVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch {
            val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            var flashJob: Job? = null
            timeTravelViewModel.effects.collect { effect ->
                val message =
                    when (effect) {
                        is TravelEffect.Travel ->
                            context.getString(
                                R.string.time_travel_start_message,
                                formatter.format(
                                    Date(effect.destination.toEpochMilliseconds()),
                                ),
                            )
                        TravelEffect.Return ->
                            context.getString(R.string.time_travel_close_message)
                    }
                // A child launch: showSnackbar suspends for the snackbar's lifetime, and
                // a back-to-back travel must still restart the flash below immediately.
                launch { snackbarHostState.showSnackbar(message) }
                // Restart the pulse from scratch on back-to-back travels: joining the old
                // job first keeps its finally from hiding the overlay under the new pulse.
                flashJob?.cancelAndJoin()
                flashJob =
                    launch {
                        flashVisible = true
                        try {
                            flashAlpha.snapTo(0f)
                            flashAlpha.animateTo(
                                FLASH_PEAK_ALPHA,
                                tween(FLASH_RAMP_MS, easing = LinearEasing),
                            )
                            flashAlpha.animateTo(0f, tween(FLASH_RAMP_MS, easing = LinearEasing))
                        } finally {
                            flashVisible = false
                        }
                    }
            }
        }
        launch {
            timeTravelViewModel.searchTargets.collect { searchViewModel.selectById(it) }
        }
    }

    // v1's "Location set to X" toast, shown on every fresh fix or manual entry — now a
    // snackbar so night mode can tint it.
    LaunchedEffect(locationViewModel, lifecycleOwner) {
        locationViewModel.toasts
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { toast ->
                val name =
                    toast.placeName
                        ?: context.getString(
                            R.string.location_long_lat,
                            toast.location.longitudeDeg,
                            toast.location.latitudeDeg,
                        )
                launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.location_set_toast, name),
                    )
                }
            }
    }

    val arModeOn by mapViewModel.arMode.collectAsStateWithLifecycle()
    // The two AR share composites awaiting a layout pick; bitmaps, so deliberately not
    // saveable — a rotation simply drops the sheet and the user shares again.
    var shareLayouts by remember { mutableStateOf<SkyShare.ShareLayouts?>(null) }
    val startShare: () -> Unit = {
        mapViewModel.logMenuItem(AnalyticsEvents.SHARE_SKY_LABEL)
        scope.launch {
            val map = if (arModeOn) SkyShare.captureMap(glSurfaceView) else null
            val still = if (map != null) arCamera.takeStill() else null
            if (map != null && still != null) {
                // The composites use the scrim exactly as rendered (night floor included).
                val scrim = mapViewModel.renderState.first().cameraScrim
                shareLayouts = SkyShare.arShareLayouts(context, map, still, scrim)
            } else if (!SkyShare.shareSky(context, glSurfaceView)) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.share_failed_message),
                )
            }
            // We own both captures (SkyShare never recycles what it is handed) and nothing
            // reads them after the composites are built. The null-still path matters too:
            // it falls back to shareSky, which takes its own capture, so this map would
            // otherwise be leaked outright.
            map?.recycle()
            still?.recycle()
        }
    }
    // The camera layer's plumbing lives while the layer is on: the preview binds under the
    // GL surface, the view model's zoom/exposure decisions reach the hardware, and leaving
    // composition (toggle off, navigation away) unbinds and releases the camera.
    if (arModeOn) {
        LaunchedEffect(Unit) {
            mapViewModel.arZoomRatio.collect { arCamera.setZoomRatio(it) }
        }
        LaunchedEffect(Unit) {
            mapViewModel.arExposureIndex.collect { arCamera.setExposureIndex(it) }
        }
        LaunchedEffect(Unit) {
            mapViewModel.arManualExposure.collect { arCamera.setManualExposure(it) }
        }
    }
    // Drag-to-align receipts (D64): every finished alignment gesture gets a visible
    // acknowledgement with a whole-gesture undo.
    LaunchedEffect(Unit) {
        mapViewModel.arAlignmentReceipts.collect {
            val result =
                snackbarHostState.showSnackbar(
                    context.getString(R.string.ar_alignment_adjusted_message),
                    actionLabel = context.getString(R.string.snackbar_action_undo),
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) mapViewModel.undoAlignmentDrag()
        }
    }
    // The once-ever label-size hint, fired on the first deep zoom: answers "why didn't the
    // names get bigger?" at the moment it is being asked, and hands over the setting that
    // would otherwise go unfound. Long duration — it carries an action worth reading.
    LaunchedEffect(Unit) {
        mapViewModel.labelSizeHints.collect {
            // Drop the replayed value before showing: this collector restarts every time the
            // map re-enters composition (back from Settings or the gallery), and a retained
            // replay cache would re-deliver the hint on each return.
            mapViewModel.labelSizeHints.resetReplayCache()
            val result =
                snackbarHostState.showSnackbar(
                    context.getString(R.string.label_size_hint_message),
                    actionLabel = context.getString(R.string.label_size_hint_action),
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) onOpenSettings()
        }
    }
    // Permission denials from the activity's launcher: a plain snackbar when the system
    // will ask again, an app-settings action when permanently denied (v1's location shape).
    LaunchedEffect(Unit) {
        mapViewModel.arPermissionDenials.collect { canAskAgain ->
            val result =
                snackbarHostState.showSnackbar(
                    context.getString(R.string.ar_permission_denied),
                    actionLabel =
                        if (canAskAgain) {
                            null
                        } else {
                            context.getString(R.string.ar_permission_settings_action)
                        },
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) onOpenAppSettings()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it },
    ) {
        // The camera plane sits under the (translucent, media-overlay) GL surface — the
        // stacked-surface compositing model of camera-ar-mode.md/D64, Option A.
        if (arModeOn) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).also { view ->
                        val metrics = viewContext.resources.displayMetrics
                        val long = maxOf(metrics.widthPixels, metrics.heightPixels).toDouble()
                        val short = minOf(metrics.widthPixels, metrics.heightPixels).toDouble()
                        arCamera.bind(
                            previewView = view,
                            lifecycleOwner = lifecycleOwner,
                            viewAspectLongOverShort = long / short,
                            onReady = mapViewModel::onArCameraReady,
                        )
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
            DisposableEffect(Unit) {
                onDispose { arCamera.unbind() }
            }
        }
        AndroidView(factory = { glSurfaceView }, modifier = Modifier.matchParentSize())

        // Transparent gesture layer above the GL surface, active in both frames: pinch-zoom
        // works even in sensor mode (v1 never disables its ZoomController), while the view
        // model ignores drag/rotate/fling until the user takes the camera manual. A still
        // finger is a tap — v1's onSingleTapUp — which toggles the control chrome (v1's
        // FullscreenControlsManager) and asks object info to identify the spot; a double tap
        // in manual mode flips horizon auto-leveling.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(screenShortSidePx) {
                    detectSkyGestures(
                        mapViewModel,
                        screenShortSidePx = { screenShortSidePx },
                        onTap = { offset ->
                            chromeToggledByUser = true
                            // Persisted, so later runs get v1's auto-hide: they have now seen
                            // the chrome go away and come back by their own hand.
                            mapViewModel.onChromeToggledByUser()
                            chromeVisible = !chromeVisible
                            objectInfoViewModel.onSkyTap(
                                xPx = offset.x,
                                yPx = offset.y,
                                widthPx = screenSize.width,
                                heightPx = screenSize.height,
                                camera = camera,
                                sensorFrame = referenceFrame == ReferenceFrame.SENSOR,
                                densityDpPerPx = tapDensity,
                            )
                        },
                        onDoubleTap = {
                            if (referenceFrame == ReferenceFrame.MANUAL) {
                                val enabled = mapViewModel.toggleAutoLevelHorizon()
                                scope.launch {
                                    val result =
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                if (enabled) {
                                                    R.string.auto_level_on_toast
                                                } else {
                                                    R.string.auto_level_off_toast
                                                },
                                            ),
                                            actionLabel =
                                                context.getString(
                                                    R.string.snackbar_action_undo,
                                                ),
                                            duration = SnackbarDuration.Short,
                                        )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        mapViewModel.toggleAutoLevelHorizon()
                                    }
                                }
                            }
                        },
                    )
                },
        )

        // The time-travel flash over the sky, under the controls, exactly where v1's
        // `view_mask` sat in the layout. Never composed while idle; the alpha is read in the
        // draw phase (graphicsLayer) so the ramp redraws without recomposing the screen.
        if (flashVisible) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = flashAlpha.value }
                    .background(FLASH_COLOR),
            )
        }

        // The search overlay draws above the sky but never intercepts touch: the sky stays
        // draggable mid-search, as in v1 (only BACK or the cancel button end search mode).
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
            // The chrome row hides during a search, so the bar owns the bottom edge.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                SearchControlBar(
                    targetName = target.name,
                    found = found,
                    onCancel = { searchViewModel.cancelSearch() },
                )
            }
        }

        // The chrome shows only when tapped up (and never during a search, where it would
        // fight the search control bar for the bottom edge — v1 hid its panels the same way).
        // The three-zone chrome (D56): layer rail left, primary actions bottom-right, ⋮
        // overflow. The sheets live outside this container so the auto-hide timer can't
        // dismiss an open sheet.
        //
        // Two nested gates, and the split matters. This outer one only controls *composition*:
        // its exit is a no-op animation that merely lasts as long as the zones' slide-out, so
        // the chrome stays composed long enough to animate away and is then dropped — keeping
        // the HUD flow cold while hidden (WhileSubscribed). The visible/hidden *look* is
        // MapChrome's per-zone `visible` below.
        val chromeShown = chromeVisible && searchTarget == null
        AnimatedVisibility(
            visible = chromeShown,
            enter = EnterTransition.None,
            exit = fadeOut(snap(delayMillis = CHROME_ZONE_EXIT_MS)),
            modifier = Modifier.matchParentSize(),
        ) {
            // The zones' own show/hide signal. `transition.targetState` is false on the frame
            // the outer gate starts its exit and true once entering, so the zones animate in
            // on appear and slide out before this container drops them.
            val zonesVisible = transition.targetState == EnterExitState.Visible
            val layerToggles by layersViewModel.toggles.collectAsStateWithLifecycle()
            // Collected here, not at screen level: when the chrome is hidden this content
            // leaves composition and the HUD flow goes cold (WhileSubscribed).
            val hudState by mapViewModel.hudState.collectAsStateWithLifecycle()
            // Null hides the HUD in MapChrome, so the Display toggle simply withholds it; when
            // on, it stays part of the chrome and shows/hides with everything else.
            val hudEnabled by layersViewModel.hudEnabled.collectAsStateWithLifecycle()
            val arUi by mapViewModel.arUi.collectAsStateWithLifecycle()
            val arSpecs by mapViewModel.arCameraSpecs.collectAsStateWithLifecycle()
            // In portrait the near-screen-wide time-travel player reaches into the HUD's
            // top-right corner, so the HUD steps down below it while travel is engaged.
            val portrait =
                LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
            val hudTopClearance =
                if (portrait && timeTravelState != TimeTravelState.REAL_TIME) {
                    with(LocalDensity.current) { timeTravelPlayerHeightPx.toDp() } + 8.dp
                } else {
                    0.dp
                }
            // The rail's teaching labels (D83). This content leaves composition whenever the
            // chrome hides (the same property that lets the HUD flow go cold above), so
            // entering it *is* a reveal and the count belongs here.
            //
            // The state is *latched* for the duration of the reveal rather than followed
            // live: recording the reveal immediately advances the stored count, which would
            // otherwise flip this same reveal's `visible` to false a frame later — the
            // farewell showing would vanish instead of fading, and the labels would appear to
            // skip their last outing entirely. Waiting for the first non-null value also keeps
            // a reveal that beats DataStore from being missed.
            val liveRailLabels by mapViewModel.railLabelState.collectAsStateWithLifecycle()
            // Seeded from the flow's current value so the labels can paint on this reveal's
            // very first frame; only a reveal that genuinely beats DataStore starts null and
            // waits. Without the seed the farewell reveal's first composition already carries
            // fadingOut, leaving no full-opacity frame to dissolve from.
            var railLabels by remember { mutableStateOf(liveRailLabels) }
            LaunchedEffect(Unit) {
                val thisReveal = snapshotFlow { liveRailLabels }.filterNotNull().first()
                railLabels = thisReveal
                if (thisReveal.visible) mapViewModel.onRailLabelsRevealed()
            }
            MapChrome(
                visible = zonesVisible,
                railLabels = railLabels,
                hudTopClearance = hudTopClearance,
                toggles = layerToggles,
                onToggleLayer = { id, enabled -> layersViewModel.setEnabled(id, enabled) },
                nightMode = nightMode,
                referenceFrame = referenceFrame,
                sensorsAvailable = mapViewModel.sensorsAvailable,
                arUi = arUi,
                arSpecs = arSpecs,
                onArScrimChange = mapViewModel::setArScrim,
                onArExposureChange = mapViewModel::setArExposureIndex,
                onArIsoFractionChange = mapViewModel::setArIsoFraction,
                onArShutterFractionChange = mapViewModel::setArShutterFraction,
                onShareShutter = startShare,
                shareEnabled = shareEnabled,
                hudState = hudState.takeIf { hudEnabled },
                onResetAlignment = {
                    mapViewModel.resetAlignment()
                    scope.launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.hud_alignment_reset_message),
                                actionLabel =
                                    context.getString(R.string.snackbar_action_undo),
                                duration = SnackbarDuration.Short,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            mapViewModel.undoAlignmentReset()
                        }
                    }
                },
                onToggleReferenceFrame = { mapViewModel.toggleReferenceFrame() },
                onToggleNightMode = { mapViewModel.setNightMode(!nightMode) },
                onOpenSearch = {
                    mapViewModel.logMenuItem(AnalyticsEvents.SEARCH_REQUESTED_LABEL)
                    showSearchDialog = true
                },
                onOpenTimeTravel = {
                    mapViewModel.logMenuItem(AnalyticsEvents.TIME_TRAVEL_OPENED_LABEL)
                    showTimeTravelDialog = true
                },
                onOpenLayersSheet = { showLayersSheet = true },
                onOpenOverflow = { showOverflowSheet = true },
            )
        }

        // The player shows from the moment travel engages (v1 showed it before the sweep
        // finished) and hides the moment the user heads home.
        if (timeTravelState != TimeTravelState.REAL_TIME) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    // The fullscreen theme hides the status bar, so statusBarsPadding() alone
                    // resolves to ~0 and the player slides under a camera cutout. Add the
                    // cutout inset so it clears the notch on every device.
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(8.dp)
                    // Measured after the padding, so this is the player itself; the HUD uses
                    // it to step out of the way in portrait.
                    .onSizeChanged { timeTravelPlayerHeightPx = it.height },
            ) {
                TimeTravelPlayer(timeTravelViewModel)
            }
        }

        shareLayouts?.let { layouts ->
            ShareLayoutSheet(
                layouts = layouts,
                onPick = { picked ->
                    shareLayouts = null
                    scope.launch { SkyShare.sendBitmap(context, picked) }
                },
                onDismiss = { shareLayouts = null },
            )
        }

        if (showLayersSheet) {
            LayersSheet(
                layersViewModel,
                onDismiss = { showLayersSheet = false },
                arModeOn = arModeOn,
                hasCamera = arCamera.hasCamera && cameraArEnabled,
                sensorsAvailable = mapViewModel.sensorsAvailable,
                onSetArMode = { on ->
                    if (on && !hasCameraPermission()) {
                        // The launcher's grant path enables the layer; denial lands in
                        // arPermissionDenials above.
                        onRequestCameraPermission()
                    } else {
                        val wasManual = referenceFrame == ReferenceFrame.MANUAL
                        if (mapViewModel.setArMode(on) && on && wasManual) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.ar_auto_mode_snackbar),
                                )
                            }
                        }
                    }
                },
            )
        }

        if (showOverflowSheet) {
            OverflowSheet(
                onShareSky = {
                    showOverflowSheet = false
                    startShare()
                },
                shareEnabled = shareEnabled,
                onOpenGallery = {
                    showOverflowSheet = false
                    mapViewModel.logMenuItem(AnalyticsEvents.GALLERY_OPENED_LABEL)
                    onOpenGallery()
                },
                onOpenLocation = {
                    showOverflowSheet = false
                    showLocationSheet = true
                },
                onOpenCalibration = {
                    showOverflowSheet = false
                    mapViewModel.logMenuItem(AnalyticsEvents.CALIBRATION_OPENED_LABEL)
                    onOpenCalibration(true)
                },
                onOpenTutorial = {
                    showOverflowSheet = false
                    mapViewModel.logMenuItem(AnalyticsEvents.TUTORIAL_OPENED_LABEL)
                    onOpenTutorial()
                },
                onOpenHelp = {
                    showOverflowSheet = false
                    mapViewModel.logMenuItem(AnalyticsEvents.HELP_OPENED_LABEL)
                    onOpenHelp()
                },
                onOpenWhatsNew = {
                    showOverflowSheet = false
                    mapViewModel.logMenuItem(AnalyticsEvents.WHATS_NEW_OPENED_LABEL)
                    onOpenWhatsNew()
                },
                onOpenSettings = {
                    showOverflowSheet = false
                    mapViewModel.logMenuItem(AnalyticsEvents.SETTINGS_OPENED_LABEL)
                    onOpenSettings()
                },
                onDismiss = { showOverflowSheet = false },
            )
        }

        if (showTimeTravelDialog) {
            val sunWontSetMessage = stringResource(R.string.sun_wont_set_message)
            TimeTravelDialog(
                timeTravelViewModel,
                onSunWontSet = {
                    scope.launch { snackbarHostState.showSnackbar(sunWontSetMessage) }
                },
                onDismiss = { showTimeTravelDialog = false },
            )
        }

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

        val showingSatellite by
            objectInfoViewModel.showingSatellite.collectAsStateWithLifecycle()
        val nextSatellitePass by
            objectInfoViewModel.nextSatellitePass.collectAsStateWithLifecycle()
        val satellitePassTimesReliable by
            objectInfoViewModel.satellitePassTimesReliable.collectAsStateWithLifecycle()
        val showingMoon by objectInfoViewModel.showingMoon.collectAsStateWithLifecycle()
        val lunarEclipse by objectInfoViewModel.lunarEclipse.collectAsStateWithLifecycle()

        objectInfoCard?.let { info ->
            ObjectInfoCard(
                info = info,
                riseSet = objectRiseSet,
                nightMode = nightMode,
                onSeeAlso = { link -> objectInfoViewModel.show(link.id) },
                // No Find here: a card opened by tapping the sky is an object the user has
                // already found. The gallery's card (SkyMapNavHost) keeps the button.
                onFind = null,
                onImageTap = { tapped ->
                    tapped.imageRef?.let { imageRef ->
                        expandedImage = ExpandedImage(imageRef, tapped.name, tapped.imageCredit)
                    }
                },
                onDismiss = { objectInfoViewModel.dismiss() },
                promoRow =
                    if (moonWidgetPromo) {
                        { MoonWidgetPromoRow(onDone = objectInfoViewModel::dismissMoonWidgetPromo) }
                    } else {
                        null
                    },
                satellitePassRow =
                    if (showingSatellite) {
                        {
                            SatellitePassRow(
                                pass = nextSatellitePass,
                                // Null with reliable data means "nothing is coming", which is a
                                // real answer; null because the data is stale means something
                                // else entirely, and the row says so.
                                passTimesReliable = satellitePassTimesReliable,
                            )
                        }
                    } else {
                        null
                    },
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
                imageRef = image.imageRef,
                name = image.name,
                credit = image.credit,
                nightMode = nightMode,
                onDismiss = { expandedImage = null },
            )
        }

        if (showLocationSheet) {
            LocationSheet(
                locationViewModel,
                nightMode = nightMode,
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

        LocationStateDialogs(
            locationState = locationState,
            manualLocationMode = manualLocationMode,
            locationViewModel = locationViewModel,
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenAppSettings = onOpenAppSettings,
            onEnterManually = {
                locationViewModel.resetManualEntry()
                showManualLocationDialog = true
            },
        )

        NoSensorWarning(
            mapViewModel.sensorsAvailable,
            sensorWarningSuppressed,
            onShown = mapViewModel::logNoSensorsWarning,
        )

        // One host for every transient notice on the map (the six v1 toast sites).
        // Anchored above the chrome row so a snackbar never covers the action buttons.
        MapSnackbarHost(
            snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
        )
    }
}

/**
 * v1's `wireLocationController` state listener, state-driven: the rationale prompt when no
 * location is set and auto-locate is welcome (`maybeShowLocationWarning` → `startLocationFlow`),
 * the permanently-denied path, and the acquiring timeout. Each dismissal is remembered for the
 * session so a lingering state doesn't immediately re-open the dialog; the timeout re-arms
 * whenever acquisition restarts.
 */
@Composable
private fun LocationStateDialogs(
    locationState: LocationState,
    manualLocationMode: Boolean,
    locationViewModel: LocationViewModel,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onEnterManually: () -> Unit,
) {
    var rationaleDismissed by rememberSaveable { mutableStateOf(false) }
    var permanentlyDeniedDismissed by rememberSaveable { mutableStateOf(false) }
    var timeoutDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(locationState) {
        if (locationState is LocationState.Acquiring) timeoutDismissed = false
    }

    val needsLocation =
        locationState is LocationState.Unset || locationState is LocationState.PermissionDenied
    if (needsLocation && !manualLocationMode && !rationaleDismissed) {
        LocationRationaleDialog(
            onGrant = {
                rationaleDismissed = true
                onRequestLocationPermission()
            },
            onEnterManually = {
                rationaleDismissed = true
                onEnterManually()
            },
            onLater = { rationaleDismissed = true },
        )
    }

    if (locationState is LocationState.PermissionPermanentlyDenied &&
        !permanentlyDeniedDismissed
    ) {
        LocationPermanentlyDeniedDialog(
            onOpenSettings = {
                permanentlyDeniedDismissed = true
                onOpenAppSettings()
            },
            onEnterManually = {
                permanentlyDeniedDismissed = true
                onEnterManually()
            },
            onDismiss = { permanentlyDeniedDismissed = true },
        )
    }

    if (locationState is LocationState.AcquiringTimeout && !timeoutDismissed) {
        AcquiringTimeoutDialog(
            onKeepWaiting = { locationViewModel.keepWaiting() },
            onEnterManually = {
                timeoutDismissed = true
                onEnterManually()
            },
            onDismiss = { timeoutDismissed = true },
        )
    }
}

/**
 * v1's `DragRotateZoomGestureDetector` + `GestureInterpreter` in Compose terms: per-event
 * pan/zoom/rotate deltas to the view model, plus v1's fling — velocity is tracked while the
 * gesture stays one-fingered and handed to [MapViewModel.onFling] on lift; a touch-down stops
 * any running fling (v1 `onDown`). Two-finger gestures never fling, exactly as v1's
 * `GestureDetector` never saw multitouch flings.
 */
private suspend fun PointerInputScope.detectSkyGestures(
    mapViewModel: MapViewModel,
    screenShortSidePx: () -> Int,
    onTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
) {
    // Double-tap state survives across gestures: two taps close in time and space are a
    // double tap (the first still fires onTap immediately — chrome toggling and identify
    // must not lag behind a wait-for-second-tap timeout).
    var lastTapUptimeMillis = 0L
    var lastTapPosition = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        mapViewModel.stopFling()
        mapViewModel.stopLeveling()
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var sawSecondFinger = false
        var maxTravelPx = 0f
        while (true) {
            val event = awaitPointerEvent()
            var pressed = 0
            val changes = event.changes
            for (i in 0 until changes.size) {
                if (changes[i].pressed) {
                    pressed++
                }
            }
            if (pressed == 0) break
            if (pressed > 1) sawSecondFinger = true
            val pan = event.calculatePan()
            if (pan != Offset.Zero) {
                mapViewModel.onDrag(pan.x, pan.y, screenShortSidePx(), pointerCount = pressed)
            }
            val zoom = event.calculateZoom()
            if (zoom != 1f) mapViewModel.onStretch(zoom)
            val rotation = event.calculateRotation()
            if (rotation != 0f) mapViewModel.onRotate(rotation)
            if (pressed == 1) {
                var change: PointerInputChange? = null
                for (i in 0 until changes.size) {
                    if (changes[i].pressed) {
                        change = changes[i]
                        break
                    }
                }
                if (change != null) {
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    maxTravelPx =
                        max(maxTravelPx, (change.position - down.position).getDistance())
                }
            }
        }
        if (!sawSecondFinger) {
            // One finger that never left the touch slop is a tap (v1 onSingleTapUp), not a
            // fling — a sub-slop "fling" would nudge the manual camera for no visible reason.
            if (maxTravelPx < viewConfiguration.touchSlop) {
                val isDoubleTap =
                    lastTapUptimeMillis != 0L &&
                        down.uptimeMillis - lastTapUptimeMillis <=
                        viewConfiguration.doubleTapTimeoutMillis &&
                        (down.position - lastTapPosition).getDistance() <=
                        DOUBLE_TAP_SLOP_DP.dp.toPx()
                if (isDoubleTap) {
                    // Consume the pair: a triple tap is a double plus a fresh single.
                    lastTapUptimeMillis = 0L
                    onDoubleTap()
                } else {
                    lastTapUptimeMillis = down.uptimeMillis
                    lastTapPosition = down.position
                    onTap(down.position)
                }
            } else {
                val velocity = velocityTracker.calculateVelocity()
                mapViewModel.onFling(velocity.x, velocity.y, screenShortSidePx())
            }
        }
        // v1 fired onGestureEnd for every lifted gesture; the leveler springs alongside any
        // fling (they move different axes: the fling drags, the leveler rolls).
        if (sawSecondFinger || maxTravelPx >= viewConfiguration.touchSlop) {
            mapViewModel.onGestureEnd()
        }
    }
}

/**
 * One-shot per process view: v1 showed its no-sensor warning once per launch, unless the
 * warm welcome's sensor slide had already broken the news ([suppressed], v1's
 * `no warn about missing sensors`).
 */
@Composable
private fun NoSensorWarning(
    sensorsAvailable: Boolean,
    suppressed: Boolean,
    onShown: () -> Unit,
) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!sensorsAvailable && !suppressed && !dismissed) {
        LaunchedEffect(Unit) { onShown() }
        AlertDialog(
            onDismissRequest = { dismissed = true },
            text = { Text(stringResource(R.string.no_sensor_warning)) },
            confirmButton = {
                TextButton(onClick = { dismissed = true }) {
                    Text(stringResource(R.string.no_sensor_warning_dismiss))
                }
            },
        )
    }
}

// v1's time-travel flash (`timetravelflash.xml`): pulsed to 0.7 alpha and back, one second each
// way. v1's `view_mask` magenta (#990099) is re-hued to the launcher icon's indigo for D73 —
// it keeps the "something just happened" jolt while staying in the navy family, where gold at
// this alpha would wash the sky out and read as a rendering fault.
private val FLASH_COLOR = Color(0xFF3D2F74)
private const val FLASH_PEAK_ALPHA = 0.7f
private const val FLASH_RAMP_MS = 1000

/** How long the auto-shown chrome lingers on entry (v1 flashed its controls similarly). */
private const val INITIAL_CHROME_FLASH_MS = 3000L

/** Startup grace before the low-accuracy calibration nudge may fire (user feedback). */
private const val CALIBRATION_STARTUP_GRACE_MS = 10_000L

/** Max distance between two taps that still counts as a double tap (in dp). */
private const val DOUBLE_TAP_SLOP_DP = 32

/**
 * `SnackbarHost` minus its internal enter/exit animation: while the AR camera layer's
 * `SurfaceView` is composited under the map, the stock host's fade-in layer never leaves
 * alpha 0 — snackbars laid out and timed out invisibly, exactly when the drag-to-align
 * receipt (D64) needed to be seen. This host renders the current snackbar statically and
 * reproduces the timeout; the visuals are the M3 snackbar's, flat like the rest of the
 * map chrome.
 */
@Composable
private fun MapSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val data = hostState.currentSnackbarData
    LaunchedEffect(data) {
        if (data != null) {
            val millis =
                when (data.visuals.duration) {
                    SnackbarDuration.Short -> 4_000L
                    SnackbarDuration.Long -> 10_000L
                    SnackbarDuration.Indefinite -> Long.MAX_VALUE
                }
            delay(millis)
            data.dismiss()
        }
    }
    if (data != null) {
        FlatSnackbar(data, modifier)
    }
}

@Composable
private fun FlatSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = SnackbarDefaults.color,
        contentColor = SnackbarDefaults.contentColor,
        modifier = modifier.padding(12.dp).fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            )
            val actionLabel = data.visuals.actionLabel
            if (actionLabel != null) {
                TextButton(
                    onClick = { data.performAction() },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = SnackbarDefaults.actionColor,
                        ),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
