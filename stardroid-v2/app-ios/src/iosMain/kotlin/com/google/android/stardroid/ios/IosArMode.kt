/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.ui.map.MapViewModel
import com.google.android.stardroid.ui.map.followControls
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoThermalState
import platform.Foundation.NSProcessInfoThermalStateDidChangeNotification
import platform.Foundation.thermalState

/**
 * Through-camera mode's iOS host, what Android's `MapRoute` does: the camera lives while the
 * ViewModel's layer is on, and runs only while the map can be seen. A phone running hot turns
 * the layer off (camera-ar-launch.md §4.2).
 */
internal class IosArMode(
    private val map: MapViewController,
    private val mapViewModel: MapViewModel,
    private val camera: IosArCamera,
    private val foreground: StateFlow<Boolean>,
    enabled: Boolean,
) {
    /** The flag and the hardware: without both the toggle never shows. */
    val available: Boolean = enabled && camera.hasCamera

    private val mapShowing = MutableStateFlow(true)
    private val heatStops = Channel<Unit>(Channel.CONFLATED)

    /** One per time heat turned the layer off, for the snackbar that says so. */
    val stoppedForHeat: Flow<Unit> = heatStops.receiveAsFlow()

    /**
     * Runs until cancelled, which releases the camera: the screen's composition runs it, so an
     * iPad window that closes doesn't keep the camera.
     */
    suspend fun run() {
        if (available) mapViewModel.arMode.collectLatest(::runLayer)
    }

    private suspend fun runLayer(on: Boolean) {
        map.setSeeThrough(on)
        if (!on) return
        if (runningHot()) {
            stopForHeat()
            return
        }
        camera.bind(map.cameraTarget, mapViewModel::onArPreviewLuma, mapViewModel::onArCameraReady)
        map.onLayout = camera::layoutChanged
        try {
            coroutineScope {
                launch { camera.followControls(mapViewModel) }
                launch {
                    combine(foreground, mapShowing) { open, showing -> open && showing }
                        .collect(camera::setActive)
                }
                launch {
                    thermalChanges().first { runningHot() }
                    stopForHeat()
                }
            }
        } finally {
            map.onLayout = null
            camera.unbind()
        }
    }

    private fun stopForHeat() {
        heatStops.trySend(Unit)
        mapViewModel.setArMode(false)
    }

    /** A page over the map pauses the camera, as leaving Android's map destination releases it. */
    fun setMapShowing(showing: Boolean) {
        mapShowing.value = showing
    }

    fun hasPermission(): Boolean = camera.isAuthorized

    fun requestPermission() {
        camera.requestAccess { granted ->
            if (granted) {
                mapViewModel.setArMode(true)
            } else {
                // iOS asks once, so the way on is always Settings.
                mapViewModel.arPermissionDenials.tryEmit(false)
            }
        }
    }
}

private fun runningHot(): Boolean =
    when (NSProcessInfo.processInfo.thermalState) {
        NSProcessInfoThermalState.NSProcessInfoThermalStateSerious,
        NSProcessInfoThermalState.NSProcessInfoThermalStateCritical,
        -> true
        else -> false
    }

private fun thermalChanges(): Flow<Unit> =
    callbackFlow {
        val center = NSNotificationCenter.defaultCenter
        val observer =
            center.addObserverForName(
                NSProcessInfoThermalStateDidChangeNotification,
                null,
                NSOperationQueue.mainQueue,
            ) { trySend(Unit) }
        // It may have changed while the camera was starting.
        trySend(Unit)
        awaitClose { center.removeObserver(observer) }
    }
