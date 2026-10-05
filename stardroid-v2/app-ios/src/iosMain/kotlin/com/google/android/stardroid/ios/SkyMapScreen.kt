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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.google.android.stardroid.ui.map.MapHud
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.ic_moon
import com.google.android.stardroid.ui.resources.ic_sun
import com.google.android.stardroid.ui.startup.EulaScreen
import com.google.android.stardroid.ui.startup.StartupViewModel
import com.google.android.stardroid.ui.theme.SkyMapTheme
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import platform.UIKit.UIViewController

/**
 * The iOS app's root, for Swift to host: the Compose Multiplatform UI (D134) over the Metal map.
 * The map is the whole screen, with the shared HUD; the rest of the shared chrome arrives screen
 * by screen (phase 5).
 *
 * Android's startup gating, as far as iOS has screens for it: the EULA blocks everything until
 * accepted. The warm welcome and What's New follow in phase 5.
 */
fun skyMapViewController(): UIViewController {
    val graph = IosAppGraph()
    val mapViewModel = graph.mapViewModel()
    val map = MapViewController(graph, mapViewModel)
    val startup = graph.startupViewModel()
    return ComposeUIViewController {
        SkyMapScreen(map, mapViewModel, startup, graph.settings.showHud)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkyMapScreen(
    map: MapViewController,
    mapViewModel: MapViewModel,
    startup: StartupViewModel,
    showHud: Flow<Boolean>,
) {
    val nightMode by mapViewModel.nightMode.collectAsState()
    val gates by startup.state.collectAsState()
    val hudState by mapViewModel.hudState.collectAsState()
    val hudEnabled by showHud.collectAsState(initial = true)
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
            // The shared HUD (D65), top-right as on Android. Reset has no undo snackbar yet; the
            // correction it clears only arises in AR mode, which iOS does not have.
            hudState?.takeIf { hudEnabled }?.let {
                MapHud(
                    state = it,
                    onResetAlignment = mapViewModel::resetAlignment,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .safeDrawingPadding()
                            .padding(top = 8.dp, end = 8.dp),
                )
            }
            NightModeToggle(
                nightMode = nightMode,
                onToggle = { mapViewModel.setNightMode(!nightMode) },
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
            )
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

/**
 * Night mode's one control until the map chrome comes over from Android (phase 5), which
 * replaces it. An icon rather than words, so it needs no string.
 */
@Composable
private fun NightModeToggle(
    nightMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(onClick = onToggle, modifier = modifier.testTag("nightModeToggle")) {
        // Android's own glyphs, tinted by the theme: red in night mode, as a colour emoji is not.
        Icon(
            painterResource(if (nightMode) Res.drawable.ic_sun else Res.drawable.ic_moon),
            contentDescription = null,
        )
    }
}
