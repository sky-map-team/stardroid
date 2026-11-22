/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The through-camera mode's hardware edge (camera-ar-launch.md §4.2): CameraX on Android,
 * AVFoundation on iOS.
 *
 * @param Target what the platform draws the preview into.
 * @param Still the platform's image type for a share still.
 */
interface ArCamera<in Target, out Still> {
    /** Whether there is a camera at all: without one the AR toggle hides. */
    val hasCamera: Boolean

    /**
     * Starts the rear camera into [target]. Both callbacks come on the main thread; [onReady]
     * again if the envelope changes.
     */
    fun bind(
        target: Target,
        onPreviewLuma: (Double) -> Unit,
        onReady: (ArCameraSpecs) -> Unit,
    )

    fun unbind()

    /** 1 is the main wide lens. */
    fun setZoomRatio(ratio: Double)

    fun setExposureIndex(index: Int)

    /** An upright still at share size, owned by the caller; null if the capture failed. */
    suspend fun takeStill(): Still?
}

/** Carries the ViewModel's zoom and exposure decisions to the hardware, until cancelled. */
suspend fun ArCamera<*, *>.followControls(mapViewModel: MapViewModel) {
    coroutineScope {
        launch { mapViewModel.arZoomRatio.collect(::setZoomRatio) }
        launch { mapViewModel.arExposureIndex.collect(::setExposureIndex) }
    }
}
