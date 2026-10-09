/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.sensors.CalibrationPrompt
import com.google.android.stardroid.time.TimeTravelState
import com.google.android.stardroid.ui.calibration.CompassCalibrationViewModel
import com.google.android.stardroid.ui.common.SystemBackHandler
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.LocationSheet
import com.google.android.stardroid.ui.location.LocationStateDialogs
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.location.ManualLocationEntryDialog
import com.google.android.stardroid.ui.location.rememberLocationSetMessage
import com.google.android.stardroid.ui.objectinfo.EclipseRow
import com.google.android.stardroid.ui.objectinfo.ImageExpandOverlay
import com.google.android.stardroid.ui.objectinfo.MoonWidgetPromo
import com.google.android.stardroid.ui.objectinfo.ObjectInfoCard
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.objectinfo.SatellitePassRow
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.ar_alignment_adjusted_message
import com.google.android.stardroid.ui.resources.ar_auto_mode_snackbar
import com.google.android.stardroid.ui.resources.ar_permission_denied
import com.google.android.stardroid.ui.resources.ar_permission_settings_action
import com.google.android.stardroid.ui.resources.auto_level_off_toast
import com.google.android.stardroid.ui.resources.auto_level_on_toast
import com.google.android.stardroid.ui.resources.calibration_low_accuracy_toast
import com.google.android.stardroid.ui.resources.hud_alignment_reset_message
import com.google.android.stardroid.ui.resources.label_size_hint_action
import com.google.android.stardroid.ui.resources.label_size_hint_message
import com.google.android.stardroid.ui.resources.no_sensor_warning
import com.google.android.stardroid.ui.resources.no_sensor_warning_dismiss
import com.google.android.stardroid.ui.resources.snackbar_action_open
import com.google.android.stardroid.ui.resources.snackbar_action_undo
import com.google.android.stardroid.ui.resources.sun_wont_set_message
import com.google.android.stardroid.ui.search.SearchControlBar
import com.google.android.stardroid.ui.search.SearchDialog
import com.google.android.stardroid.ui.search.SearchGeometry
import com.google.android.stardroid.ui.search.SearchOverlay
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.timetravel.TimeTravelDialog
import com.google.android.stardroid.ui.timetravel.TimeTravelFlash
import com.google.android.stardroid.ui.timetravel.TimeTravelPlayer
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

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
 * The map screen, on both platforms (D137): the host's sky behind the shared control overlay.
 *
 * The host draws the sky in [sky] (Android's GL surface, iOS's Metal view) and handles its
 * gestures itself, reporting two of them back: a still tap, in pixels from the screen's top-left,
 * and a double tap. Pan, zoom, rotate and fling go straight to [MapViewModel] as v1-shaped deltas
 * (`MapMover`); the camera and scene flows reach the renderer through each host's `RenderBinder`,
 * not through composition.
 *
 * [covered] says the host is drawing something over the map: iOS's pages and startup screens,
 * Android's own sheets. The map's dialogs, its object card and the calibration nudge wait until
 * it clears. Android's other screens are navigation destinations, where the map isn't composed
 * at all.
 */
@Composable
fun MapScreen(
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
    // Null where the system's own prompt is the rationale (iOS).
    onRequestLocationPermission: (() -> Unit)?,
    onRequestAutoLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    covered: Boolean = false,
    // Whether the overflow sheet offers Calibrate. iOS keeps it in Diagnostics instead: an
    // iPhone's compass stays calibrated, so it isn't worth a menu row.
    calibrationInMenu: Boolean = true,
    // The location sheet's map preview; null leaves the preview out.
    mapApiKey: String? = null,
    // Null leaves sharing out: the SHARE_SKY experiment is off, or the platform can't share yet.
    onShareSky: (() -> Unit)? = null,
    // Null leaves the Widgets row out: no widget is on offer.
    onOpenWidgets: (() -> Unit)? = null,
    // The object card's moon-widget promotion; null leaves it out.
    moonWidgetPromoRow: (@Composable (placed: Boolean) -> Unit)? = null,
    // Through-camera mode (camera-ar-mode.md/D64): whether the Layers sheet offers it (the
    // CAMERA_AR experiment folded in), and the permission hooks. The camera itself is the
    // host's, under its sky.
    hasArCamera: Boolean = false,
    hasCameraPermission: () -> Boolean = { false },
    onRequestCameraPermission: () -> Unit = {},
    sky: @Composable BoxScope.(onTap: (Offset) -> Unit, onDoubleTap: () -> Unit) -> Unit,
) {
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
    // diagnostics, and calibration are no longer local booleans here — they're the host's
    // destinations (D48), reached through the onOpenX callbacks below.
    var showLayersSheet by rememberSaveable { mutableStateOf(false) }
    // The layer whose options the sheet opens expanded, from the rail's help popup.
    var layersSheetFocus by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(chromeToggledByUser, chromeEverToggled, covered) {
        // Until someone has worked the toggle at least once, the chrome stays up: a first-run
        // user who has never seen it hide has no way to know a sky tap brings it back. Once
        // they have done it themselves, the auto-hide resumes for every later run. The flash
        // is for the user to see, so it waits for anything drawn over the map to go.
        if (!covered && !chromeToggledByUser && chromeEverToggled == true) {
            delay(INITIAL_CHROME_FLASH_MS)
            chromeVisible = false
        }
    }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    var timeTravelPlayerHeightPx by remember { mutableIntStateOf(0) }
    // Identify needs px-per-dp to size its label-inclusive tap tolerance.
    val tapDensity = LocalDensity.current.density

    // Activating a target closes the dialog and, in manual mode, teleports to it (v1).
    LaunchedEffect(searchTarget) {
        searchTarget?.let { target ->
            showSearchDialog = false
            mapViewModel.aimAt(target.direction, target.fovDeg)
        }
    }
    // v1: the hardware BACK key ends an active search (on iOS, the edge swipe).
    SystemBackHandler(enabled = searchTarget != null && !covered) {
        searchViewModel.cancelSearch()
    }

    // The notices' text, read here: the snackbars are shown from effects and gesture callbacks,
    // outside composition, and should follow the app's language as the rest of the screen does.
    val lowAccuracyMessage = stringResource(Res.string.calibration_low_accuracy_toast)
    val openAction = stringResource(Res.string.snackbar_action_open)
    val undoAction = stringResource(Res.string.snackbar_action_undo)
    val alignmentAdjustedMessage = stringResource(Res.string.ar_alignment_adjusted_message)
    val labelSizeHintMessage = stringResource(Res.string.label_size_hint_message)
    val labelSizeHintAction = stringResource(Res.string.label_size_hint_action)
    val arPermissionDeniedMessage = stringResource(Res.string.ar_permission_denied)
    val arPermissionSettingsAction = stringResource(Res.string.ar_permission_settings_action)
    val autoLevelOnMessage = stringResource(Res.string.auto_level_on_toast)
    val autoLevelOffMessage = stringResource(Res.string.auto_level_off_toast)
    val alignmentResetMessage = stringResource(Res.string.hud_alignment_reset_message)
    val arAutoModeMessage = stringResource(Res.string.ar_auto_mode_snackbar)

    // v1 ran SensorAccuracyMonitor for the life of the map activity: a badly calibrated
    // compass opens the calibration screen in its auto-dismissable form (or nudges via
    // snackbar, if the user opted out there — v1 toasted). Only over the bare map, so the
    // screen never opens under a dialog or sheet the user is working in; the monitor's own
    // throttle is persisted, so restarting it as they come and go doesn't re-nag.
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var gracePeriodPassed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!gracePeriodPassed) {
            delay(CALIBRATION_STARTUP_GRACE_MS)
            gracePeriodPassed = true
        }
    }
    val mapIdle =
        !covered && !showSearchDialog && !showTimeTravelDialog && !showLayersSheet &&
            !showOverflowSheet && !showLocationSheet && !showManualLocationDialog &&
            objectInfoCard == null && expandedImage == null
    LaunchedEffect(calibrationViewModel, lifecycleOwner, gracePeriodPassed && mapIdle) {
        if (gracePeriodPassed && mapIdle) {
            calibrationViewModel.prompts
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { prompt ->
                    when (prompt) {
                        CalibrationPrompt.SCREEN -> onOpenCalibration(false)
                        CalibrationPrompt.TOAST -> {
                            launch {
                                val result =
                                    snackbarHostState.showSnackbar(
                                        lowAccuracyMessage,
                                        actionLabel = openAction,
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

    // The per-event search target, aimed once time travel's clock has arrived. The travel
    // flash and notices are TimeTravelFlash's, over the sky below.
    LaunchedEffect(Unit) {
        timeTravelViewModel.searchTargets.collect { searchViewModel.selectById(it) }
    }

    // v1's "Location set to X" toast, shown on every fresh fix or manual entry — now a
    // snackbar so night mode can tint it.
    val locationSetMessage = rememberLocationSetMessage()
    LaunchedEffect(locationViewModel, lifecycleOwner) {
        locationViewModel.toasts
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { toast ->
                launch { snackbarHostState.showSnackbar(locationSetMessage(toast)) }
            }
    }

    val arModeOn by mapViewModel.arMode.collectAsStateWithLifecycle()
    // Drag-to-align receipts (D64): every finished alignment gesture gets a visible
    // acknowledgement with a whole-gesture undo.
    LaunchedEffect(Unit) {
        mapViewModel.arAlignmentReceipts.collect {
            val result =
                snackbarHostState.showSnackbar(
                    alignmentAdjustedMessage,
                    actionLabel = undoAction,
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
                    labelSizeHintMessage,
                    actionLabel = labelSizeHintAction,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) onOpenSettings()
        }
    }
    // Permission denials from the host's camera ask: a plain snackbar when the system will
    // ask again, an app-settings action when permanently denied (v1's location shape).
    LaunchedEffect(Unit) {
        mapViewModel.arPermissionDenials.collect { canAskAgain ->
            val result =
                snackbarHostState.showSnackbar(
                    arPermissionDeniedMessage,
                    actionLabel = if (canAskAgain) null else arPermissionSettingsAction,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) onOpenAppSettings()
        }
    }

    // A still finger on the sky — v1's onSingleTapUp — toggles the control chrome (v1's
    // FullscreenControlsManager) and asks object info to identify the spot.
    val onSkyTap: (Offset) -> Unit = { offset ->
        chromeToggledByUser = true
        // Persisted, so later runs get v1's auto-hide: they have now seen the chrome go away
        // and come back by their own hand.
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
    }
    // A double tap in manual mode flips horizon auto-leveling.
    val onSkyDoubleTap: () -> Unit = {
        if (referenceFrame == ReferenceFrame.MANUAL) {
            val enabled = mapViewModel.toggleAutoLevelHorizon()
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        if (enabled) autoLevelOnMessage else autoLevelOffMessage,
                        actionLabel = undoAction,
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    mapViewModel.toggleAutoLevelHorizon()
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it },
    ) {
        // The host's sky and its gestures, active in both frames: pinch-zoom works even in
        // sensor mode (v1 never disables its ZoomController), while the view model ignores
        // drag/rotate/fling until the user takes the camera manual.
        sky(onSkyTap, onSkyDoubleTap)

        // The time-travel flash over the sky, under the controls, exactly where v1's
        // `view_mask` sat in the layout; its notices are snackbars.
        TimeTravelFlash(
            effects = timeTravelViewModel.effects,
            onNotice = { snackbarHostState.showSnackbar(it) },
            modifier = Modifier.matchParentSize(),
        )

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
            // Portrait as MapChrome reads it: a window that fills the screen, taller than wide.
            val portrait = LocalWindowInfo.current.containerSize.let { it.width <= it.height }
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
                onShareShutter = onShareSky ?: {},
                shareEnabled = onShareSky != null,
                hudState = hudState.takeIf { hudEnabled },
                onResetAlignment = {
                    mapViewModel.resetAlignment()
                    scope.launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                alignmentResetMessage,
                                actionLabel = undoAction,
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
                onOpenLayersSheet = {
                    layersSheetFocus = null
                    showLayersSheet = true
                },
                onCustomizeLayer = {
                    layersSheetFocus = it.id
                    showLayersSheet = true
                },
                onOpenOverflow = { showOverflowSheet = true },
            )
        }

        // The player shows from the moment travel engages (v1 showed it before the sweep
        // finished) and hides the moment the user heads home.
        if (timeTravelState != TimeTravelState.REAL_TIME) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    // Android's fullscreen theme hides the status bar, so statusBarsPadding()
                    // alone resolves to ~0 and the player slides under a camera cutout. Add the
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

        if (showLayersSheet) {
            LayersSheet(
                layersViewModel,
                onDismiss = {
                    showLayersSheet = false
                    layersSheetFocus = null
                },
                expandLayer = layersSheetFocus?.let { LayerId(it) },
                arModeOn = arModeOn,
                hasCamera = hasArCamera,
                sensorsAvailable = mapViewModel.sensorsAvailable,
                onSetArMode = { on ->
                    if (on && !hasCameraPermission()) {
                        // The host's grant path enables the layer; denial lands in
                        // arPermissionDenials above.
                        onRequestCameraPermission()
                    } else {
                        val wasManual = referenceFrame == ReferenceFrame.MANUAL
                        if (mapViewModel.setArMode(on) && on && wasManual) {
                            scope.launch { snackbarHostState.showSnackbar(arAutoModeMessage) }
                        }
                    }
                },
            )
        }

        if (showOverflowSheet) {
            OverflowSheet(
                onShareSky =
                    onShareSky?.let { share ->
                        {
                            showOverflowSheet = false
                            share()
                        }
                    },
                shareEnabled = onShareSky != null,
                onOpenGallery = {
                    showOverflowSheet = false
                    mapViewModel.logMenuItem(AnalyticsEvents.GALLERY_OPENED_LABEL)
                    onOpenGallery()
                },
                onOpenWidgets =
                    onOpenWidgets?.let { open ->
                        {
                            showOverflowSheet = false
                            mapViewModel.logMenuItem(AnalyticsEvents.WIDGETS_OPENED_LABEL)
                            open()
                        }
                    },
                widgetsEnabled = onOpenWidgets != null,
                onOpenLocation = {
                    showOverflowSheet = false
                    showLocationSheet = true
                },
                onOpenCalibration =
                    if (calibrationInMenu) {
                        {
                            showOverflowSheet = false
                            mapViewModel.logMenuItem(AnalyticsEvents.CALIBRATION_OPENED_LABEL)
                            onOpenCalibration(true)
                        }
                    } else {
                        null
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
            val sunWontSetMessage = stringResource(Res.string.sun_wont_set_message)
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

        // Covered, the card is the host's: iOS's gallery page shows its own, as Android's
        // gallery destination does.
        objectInfoCard?.takeUnless { covered }?.let { info ->
            ObjectInfoCard(
                info = info,
                riseSet = objectRiseSet,
                nightMode = nightMode,
                onSeeAlso = { link -> objectInfoViewModel.show(link.id) },
                // No Find here: a card opened by tapping the sky is an object the user has
                // already found. The gallery's card keeps the button.
                onFind = null,
                onImageTap = { tapped ->
                    tapped.imageRef?.let { imageRef ->
                        expandedImage = ExpandedImage(imageRef, tapped.name, tapped.imageCredit)
                    }
                },
                onDismiss = { objectInfoViewModel.dismiss() },
                promoRow =
                    moonWidgetPromoRow
                        ?.takeIf { moonWidgetPromo != MoonWidgetPromo.HIDDEN }
                        ?.let { row -> { row(moonWidgetPromo == MoonWidgetPromo.PLACED) } },
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

        expandedImage?.takeUnless { covered }?.let { image ->
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
                mapApiKey = mapApiKey,
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

        if (!covered) {
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
        }

        NoSensorWarning(
            mapViewModel.sensorsAvailable,
            sensorWarningSuppressed,
            covered = covered,
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
 * One-shot per process view: v1 showed its no-sensor warning once per launch, unless the
 * warm welcome's sensor slide had already broken the news ([suppressed], v1's
 * `no warn about missing sensors`). It waits while the map is [covered].
 */
@Composable
private fun NoSensorWarning(
    sensorsAvailable: Boolean,
    suppressed: Boolean,
    covered: Boolean,
    onShown: () -> Unit,
) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!sensorsAvailable && !suppressed && !dismissed && !covered) {
        LaunchedEffect(Unit) { onShown() }
        AlertDialog(
            onDismissRequest = { dismissed = true },
            text = { Text(stringResource(Res.string.no_sensor_warning)) },
            confirmButton = {
                TextButton(onClick = { dismissed = true }) {
                    Text(stringResource(Res.string.no_sensor_warning_dismiss))
                }
            },
        )
    }
}

/** How long the auto-shown chrome lingers on entry (v1 flashed its controls similarly). */
private const val INITIAL_CHROME_FLASH_MS = 3000L

/** Startup grace before the low-accuracy calibration nudge may fire (user feedback). */
private const val CALIBRATION_STARTUP_GRACE_MS = 10_000L

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
