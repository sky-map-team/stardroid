/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS app's root, for Swift to host: the Compose Multiplatform UI (D134) with the Metal map
 * underneath. The map is the whole screen for now; the shared Compose chrome arrives with the
 * screens (phase 5).
 */
fun skyMapViewController(): UIViewController {
    val graph = IosAppGraph()
    val map = MapViewController(graph, graph.mapViewModel())
    graph.locationController.start()
    return ComposeUIViewController { SkyMapScreen(map) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkyMapScreen(map: MapViewController) {
    Box(Modifier.fillMaxSize()) {
        // NonCooperative: the map's own recognizers get every touch at once, without Compose's
        // ~150 ms cooperative hold (D134's spike).
        UIKitViewController(
            factory = { map.viewController },
            modifier = Modifier.fillMaxSize(),
            properties =
                UIKitInteropProperties(
                    interactionMode = UIKitInteropInteractionMode.NonCooperative,
                ),
        )
    }
}
