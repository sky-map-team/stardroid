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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.map.LayersSheet
import com.google.android.stardroid.ui.map.MapChrome
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.search.SearchControlBar
import com.google.android.stardroid.ui.search.SearchDialog
import com.google.android.stardroid.ui.search.SearchGeometry
import com.google.android.stardroid.ui.search.SearchOverlay
import com.google.android.stardroid.ui.search.SearchViewModel
import com.google.android.stardroid.ui.startup.EulaScreen
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.theme.SkyMapTheme
import platform.UIKit.UIViewController

/**
 * The iOS app's root, for Swift to host: the Compose Multiplatform UI (D134) over the Metal map.
 *
 * The map carries the shared chrome (MapChrome: the layer rail, the action cluster, the HUD) and
 * the shared Layers sheet and search. This is a stand-in for Android's MapScreen, which composes
 * every other screen and so moves last (D137); until then the actions whose screens iOS lacks —
 * time travel, the overflow menu — do nothing.
 *
 * Android's startup gating, as far as iOS has screens for it: the EULA blocks everything until
 * accepted. The warm welcome and What's New follow later in phase 5.
 */
fun skyMapViewController(): UIViewController {
    val graph = IosAppGraph()
    val mapViewModel = graph.mapViewModel()
    val map = MapViewController(graph, mapViewModel)
    val startup = graph.startupViewModel()
    val layers = graph.layersViewModel()
    val search = graph.searchViewModel(mapViewModel)
    return ComposeUIViewController { SkyMapScreen(map, mapViewModel, layers, search, startup) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkyMapScreen(
    map: MapViewController,
    mapViewModel: MapViewModel,
    layersViewModel: LayersViewModel,
    searchViewModel: SearchViewModel,
    startup: StartupViewModel,
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
                    onOpenTimeTravel = {},
                    onOpenLayersSheet = {
                        expandLayer = null
                        showLayersSheet = true
                    },
                    onCustomizeLayer = { id ->
                        expandLayer = id
                        showLayersSheet = true
                    },
                    onOpenOverflow = {},
                    // Reset has no undo snackbar yet; the correction it clears only arises in AR
                    // mode, which iOS does not have.
                    hudState = hudState.takeIf { hudEnabled },
                    onResetAlignment = mapViewModel::resetAlignment,
                    shareEnabled = false,
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
            if (showLayersSheet) {
                LayersSheet(
                    layersViewModel = layersViewModel,
                    onDismiss = { showLayersSheet = false },
                    sensorsAvailable = mapViewModel.sensorsAvailable,
                    expandLayer = expandLayer,
                )
            }
            val current = gates
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
            }
        }
    }
}
