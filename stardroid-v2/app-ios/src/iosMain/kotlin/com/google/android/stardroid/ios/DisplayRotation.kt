/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.math.Matrix3
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import kotlin.concurrent.AtomicReference

/**
 * How far the screen is turned from portrait, named for Android's `Surface.ROTATION_*`: the
 * sensors report in the phone's own frame, so the map needs the same remap Android's
 * `SensorOrientationSource` applies.
 */
enum class DisplayRotation {
    ROTATION_0,
    ROTATION_90,
    ROTATION_180,
    ROTATION_270,
    ;

    companion object {
        /**
         * UIKit names a landscape by the side the home indicator is on. Android's rotation is
         * the drawing's, opposite to the phone's: ROTATION_90 is the phone turned clockwise, its
         * top to the right and the home indicator on the left, which is LandscapeLeft.
         */
        fun from(orientation: UIInterfaceOrientation): DisplayRotation =
            when (orientation) {
                UIInterfaceOrientationLandscapeLeft -> ROTATION_90
                UIInterfaceOrientationPortraitUpsideDown -> ROTATION_180
                UIInterfaceOrientationLandscapeRight -> ROTATION_270
                else -> ROTATION_0
            }
    }
}

/** The current [DisplayRotation], written on the main thread and read on Core Motion's queue. */
class DisplayRotationHolder {
    private val current = AtomicReference(DisplayRotation.ROTATION_0)

    var rotation: DisplayRotation
        get() = current.value
        set(value) {
            current.value = value
        }
}

/**
 * This phone→world matrix with the phone's axes remapped into the screen's frame, as
 * `SensorManager.remapCoordinateSystem` does for each display rotation on Android.
 */
internal fun Matrix3.inDisplayFrame(rotation: DisplayRotation): Matrix3 =
    when (rotation) {
        DisplayRotation.ROTATION_0 -> this
        DisplayRotation.ROTATION_90 -> Matrix3(xy, -xx, xz, yy, -yx, yz, zy, -zx, zz)
        DisplayRotation.ROTATION_180 -> Matrix3(-xx, -xy, xz, -yx, -yy, yz, -zx, -zy, zz)
        DisplayRotation.ROTATION_270 -> Matrix3(-xy, xx, xz, -yy, yx, yz, -zy, zx, zz)
    }
