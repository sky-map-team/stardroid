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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.map.LayersSheet
import com.google.android.stardroid.ui.map.MapChrome
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.startup.EulaScreen
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.theme.SkyMapTheme
import platform.UIKit.UIViewController

/**
 * The iOS app's root, for Swift to host: the Compose Multiplatform UI (D134) over the Metal map.
 *
 * The map carries the shared chrome (MapChrome: the layer rail, the action cluster, the HUD) and
 * the shared Layers sheet. This is a stand-in for Android's MapScreen, which composes every other
 * screen and so moves last (D137); until then the actions whose screens iOS lacks — search, time
 * travel, the overflow menu — do nothing.
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
    return ComposeUIViewController { SkyMapScreen(map, mapViewModel, layers, startup) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkyMapScreen(
    map: MapViewController,
    mapViewModel: MapViewModel,
    layersViewModel: LayersViewModel,
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
    SkyMapTheme(nightMode) {
        Box(Modifier.fillMaxSize()) {
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
            MapChrome(
                toggles = toggles,
                nightMode = nightMode,
                referenceFrame = referenceFrame,
                sensorsAvailable = mapViewModel.sensorsAvailable,
                onToggleLayer = { id, enabled -> layersViewModel.setEnabled(id, enabled) },
                onToggleReferenceFrame = mapViewModel::toggleReferenceFrame,
                onToggleNightMode = { mapViewModel.setNightMode(!nightMode) },
                onOpenSearch = {},
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
