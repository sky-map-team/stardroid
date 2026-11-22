/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

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
import androidx.compose.ui.graphics.ImageBitmap
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.share.ShareBranding
import com.google.android.stardroid.share.ShareCompositor
import com.google.android.stardroid.share.ShareLayouts
import com.google.android.stardroid.share.release
import com.google.android.stardroid.share.rememberShareCompositor
import com.google.android.stardroid.ui.common.formattedStringResource
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.map.ShareLayoutSheet
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.app_name
import com.google.android.stardroid.ui.resources.share_failed_message
import com.google.android.stardroid.ui.resources.share_invitation
import com.google.android.stardroid.ui.resources.share_text
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIBezierPath
import platform.UIKit.UIImage

/**
 * Sharing the sky, what Android's `MapRoute` and `SkyShare` do: capture, let the user pick a
 * layout, and hand the picture to the share sheet. Unlike Android, nothing is saved first.
 */
internal class SkySharing(
    private val scope: CoroutineScope,
    private val map: MapViewController,
    private val camera: IosArCamera,
    private val mapViewModel: MapViewModel,
    private val compositor: ShareCompositor,
    private val snackbarHostState: SnackbarHostState,
    private val appName: String,
    private val invitation: String,
    private val shareText: String,
    private val failedMessage: String,
) {
    /** The choice on offer, while the layout sheet is up. */
    var layouts by mutableStateOf<ShareLayouts?>(null)
        private set

    private var working by mutableStateOf(false)
    private var systemSheetUp by mutableStateOf(false)

    /** From the shutter until the share sheet closes, which an announcement mustn't interrupt. */
    val inProgress: Boolean get() = working || layouts != null || systemSheetUp

    private var job: Job? = null
    private var sendJob: Job? = null

    fun start() {
        if (job?.isActive == true) return
        mapViewModel.logMenuItem(AnalyticsEvents.SHARE_SKY_LABEL)
        working = true
        job = scope.launch { whileWorking { capture() } }
    }

    private suspend fun capture() {
        val inAr = mapViewModel.arMode.value
        val mapImage = map.captureForShare()
        if (mapImage == null) {
            showFailure()
            return
        }
        val branding = ShareBranding(appIcon(), appName, invitation, SHARE_LINK)
        var still: ImageBitmap? = null
        try {
            still = if (inAr) camera.takeStill() else null
            if (still == null) {
                // No still, map only, as on Android.
                val image = compositor.mapOnly(mapImage, branding)
                send(image)
                // Never shown, and written out by now.
                image.release()
            } else {
                val scrim = mapViewModel.renderState.first().cameraScrim
                // Squared: the scrim is blended with the colour's factors, so a quad of opacity
                // s leaves alpha s² in the layer, and that is how much the screen dims the camera.
                val composed = compositor.arLayouts(mapImage, still, scrim * scrim, branding)
                // Turned off meanwhile (a toggle, heat): nothing left to choose for.
                if (mapViewModel.arMode.value) {
                    layouts = composed
                } else {
                    composed.release()
                }
            }
        } finally {
            mapImage.release()
            still?.release()
            branding.icon?.release()
        }
    }

    fun pick(image: ImageBitmap) {
        // Before the layouts go, so inProgress has no gap.
        working = true
        layouts = null
        sendJob = scope.launch { whileWorking { send(image) } }
    }

    fun dismiss() {
        layouts = null
    }

    // Not before the sheet that draws them has gone, and not while one is being written out.
    fun onSheetGone(shown: ShareLayouts) {
        // Only covered by a page: it comes back with the same pictures.
        if (layouts === shown) return
        scope.launch {
            sendJob?.join()
            shown.release()
        }
    }

    private suspend fun whileWorking(block: suspend () -> Unit) {
        working = true
        try {
            block()
        } finally {
            working = false
        }
    }

    private suspend fun send(image: ImageBitmap) {
        val sheet = shareImage(image, shareText)
        if (sheet == null) {
            showFailure()
            return
        }
        systemSheetUp = true
        scope.launch {
            // Polled: the completion handler also fires for activities that leave the sheet up.
            while (sheet.presentingViewController != null) delay(SHEET_POLL_MILLIS)
            systemSheetUp = false
        }
    }

    private suspend fun showFailure() {
        snackbarHostState.showSnackbar(failedMessage)
    }
}

@Composable
internal fun rememberSkySharing(
    map: MapViewController,
    camera: IosArCamera,
    mapViewModel: MapViewModel,
    snackbarHostState: SnackbarHostState,
): SkySharing {
    val scope = rememberCoroutineScope()
    val compositor = rememberShareCompositor()
    val appName = stringResource(Res.string.app_name)
    val invitation = stringResource(Res.string.share_invitation)
    val text = formattedStringResource(Res.string.share_text, SHARE_LINK)
    val failed = stringResource(Res.string.share_failed_message)
    val sharing =
        remember(map, compositor, appName, invitation, text, failed) {
            SkySharing(
                scope,
                map,
                camera,
                mapViewModel,
                compositor,
                snackbarHostState,
                appName,
                invitation,
                text,
                failed,
            )
        }
    val arModeOn by mapViewModel.arMode.collectAsState()
    // Don't hold ~25 MB of layouts once the camera layer is off.
    LaunchedEffect(arModeOn) { if (!arModeOn) sharing.dismiss() }
    return sharing
}

@Composable
internal fun SkySharingSheet(sharing: SkySharing) {
    sharing.layouts?.let { layouts ->
        // Declared before the sheet, so it's disposed after it.
        DisposableEffect(layouts) { onDispose { sharing.onSheetGone(layouts) } }
        ShareLayoutSheet(layouts, onPick = sharing::pick, onDismiss = sharing::dismiss)
    }
}

private fun ShareLayouts.release() {
    overlay.release()
    sideBySide.release()
}

// The installed app's own icon (ASSET-LICENSES.txt's AppIcon.png), masked as Android's launcher
// icon arrives. The asset catalog's icon set has no name of its own; these are what it compiles to.
@OptIn(ExperimentalForeignApi::class)
private fun appIcon(): ImageBitmap? {
    val icon =
        UIImage.imageNamed("AppIcon60x60") ?: UIImage.imageNamed("AppIcon76x76") ?: return null
    val side = ICON_SIZE_PX.toDouble()
    val bounds = CGRectMake(0.0, 0.0, side, side)
    return drawImageBitmap(ICON_SIZE_PX, ICON_SIZE_PX) {
        UIBezierPath.bezierPathWithRoundedRect(bounds, cornerRadius = side * ICON_CORNER).addClip()
        icon.drawInRect(bounds)
    }
}

private const val ICON_SIZE_PX = 180
private const val SHEET_POLL_MILLIS = 500L
private const val ICON_CORNER = 0.225
