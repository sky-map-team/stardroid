/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.ui.graphics.ImageBitmap
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.startup.FlagSource
import com.google.android.stardroid.ui.map.ArCamera
import platform.UIKit.UIView

/** Where the camera's preview goes: behind everything else in [container]. */
class ArPreviewTarget(
    val container: UIView,
    val rotation: () -> DisplayRotation,
)

/** [ArCamera] with what iOS's host adds: no lifecycle owner stops the camera or turns it here. */
interface IosArCamera : ArCamera<ArPreviewTarget, ImageBitmap> {
    val isAuthorized: Boolean

    /** The system prompt if it hasn't been answered; [onResult] comes on the main thread. */
    fun requestAccess(onResult: (granted: Boolean) -> Unit)

    /** Pauses or resumes a bound camera, keeping its zoom and exposure. */
    fun setActive(active: Boolean)

    /** The target changed size or the interface turned. */
    fun layoutChanged()
}

// The simulator has no camera, compass or Remote Config, so its binary alone carries stand-ins
// (camera-ar-launch.md §4.6). On a device all three are the real thing, with no override.

internal expect fun createArCamera(): IosArCamera

internal expect fun flagSourceForTarget(source: FlagSource): FlagSource

internal expect fun orientationSourceForTarget(source: OrientationSource): OrientationSource
