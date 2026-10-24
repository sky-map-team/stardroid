/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import android.opengl.GLSurfaceView
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.camera.SkyCameraPreview
import com.google.android.stardroid.share.SkyShare
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.ui.calibration.CompassCalibrationViewModel
import com.google.android.stardroid.ui.common.WidgetsSheet
import com.google.android.stardroid.ui.common.widgetOffers
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.location.LocationViewModel
import com.google.android.stardroid.ui.objectinfo.MoonWidgetPromoRow
import com.google.android.stardroid.ui.objectinfo.ObjectInfoViewModel
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.timetravel.TimeTravelViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Android's map destination: the shared [MapScreen] over what only Android has — the GL sky and
 * its Compose gesture detector, the CameraX plane under it for through-camera mode, sharing the
 * sky, the home-screen widgets, and the Geoapify key from the build.
 */
@Composable
fun MapRoute(
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
    // Not remember()-cached: RemoteConfigExperimentConfig is one long-lived instance
    // whose isEnabled() answer changes asynchronously once fetchAndActivate() completes,
    // so keying a cache on the instance itself would never invalidate. Read fresh every
    // recomposition, like the sibling flags above.
    val widgetsOnOffer = widgetOffers(experimentConfig)
    var showWidgetsSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val arModeOn by mapViewModel.arMode.collectAsStateWithLifecycle()
    // The two AR share composites awaiting a layout pick; bitmaps, so deliberately not
    // saveable — a rotation simply drops the sheet and the user shares again.
    var shareLayouts by remember { mutableStateOf<SkyShare.ShareLayouts?>(null) }
    val shareFailedMessage = stringResource(R.string.share_failed_message)
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
                snackbarHostState.showSnackbar(shareFailedMessage)
            }
            // We own both captures (SkyShare never recycles what it is handed) and nothing
            // reads them after the composites are built. The null-still path matters too:
            // it falls back to shareSky, which takes its own capture, so this map would
            // otherwise be leaked outright.
            map?.recycle()
            still?.recycle()
        }
    }

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
        onOpenSettings = onOpenSettings,
        onOpenGallery = onOpenGallery,
        onOpenTutorial = onOpenTutorial,
        onOpenHelp = onOpenHelp,
        onOpenWhatsNew = onOpenWhatsNew,
        onOpenCalibration = onOpenCalibration,
        onRequestLocationPermission = onRequestLocationPermission,
        onRequestAutoLocation = onRequestAutoLocation,
        onOpenAppSettings = onOpenAppSettings,
        // This route's own sheets cover the map as the shared ones do.
        covered = showWidgetsSheet || shareLayouts != null,
        mapApiKey =
            stringResource(R.string.geoapify_maps_api_key)
                .takeUnless { it == "unset" || it.isEmpty() },
        onShareSky = startShare.takeIf { shareEnabled },
        onOpenWidgets = { showWidgetsSheet = true }.takeIf { widgetsOnOffer.isNotEmpty() },
        moonWidgetPromoRow = { placed -> MoonWidgetPromoRow(placed = placed) },
        hasArCamera = arCamera.hasCamera && cameraArEnabled,
        hasCameraPermission = hasCameraPermission,
        onRequestCameraPermission = onRequestCameraPermission,
    ) { onTap, onDoubleTap ->
        // The camera layer's plumbing lives while the layer is on: the preview binds under the
        // GL surface, the view model's zoom/exposure decisions reach the hardware, and leaving
        // composition (toggle off, navigation away) unbinds and releases the camera. The camera
        // plane sits under the (translucent, media-overlay) GL surface — the stacked-surface
        // compositing model of camera-ar-mode.md/D64, Option A.
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

        // Transparent gesture layer above the GL surface. The detector outlives recompositions
        // (it restarts only when the screen's size does), so it calls through to the latest
        // tap handlers.
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
        var screenShortSidePx by remember { mutableIntStateOf(0) }
        Box(
            Modifier
                .matchParentSize()
                .onSizeChanged { screenShortSidePx = min(it.width, it.height) }
                .pointerInput(screenShortSidePx) {
                    detectSkyGestures(
                        mapViewModel,
                        screenShortSidePx = { screenShortSidePx },
                        onTap = { currentOnTap(it) },
                        onDoubleTap = { currentOnDoubleTap() },
                    )
                },
        )
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

    if (showWidgetsSheet) {
        WidgetsSheet(widgetsOnOffer, onDismiss = { showWidgetsSheet = false })
    }
}

/**
 * v1's `DragRotateZoomGestureDetector` + `GestureInterpreter` in Compose terms: per-event
 * pan/zoom/rotate deltas to the view model, plus v1's fling — velocity is tracked while the
 * gesture stays one-fingered and handed to [MapViewModel.onFling] on lift; a touch-down stops
 * any running fling (v1 `onDown`). Two-finger gestures never fling, exactly as v1's
 * `GestureDetector` never saw multitouch flings. iOS's UIKit recognizers do the same in
 * `MapViewController`.
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

/** Max distance between two taps that still counts as a double tap (in dp). */
private const val DOUBLE_TAP_SLOP_DP = 32
