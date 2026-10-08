/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.scene

import com.google.android.stardroid.astronomy.LocalFrame
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.render.api.SkyCamera
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the user is looking, in the observer's horizon frame: azimuth from true north through
 * east, altitude above the horizon, and the field of view across the shorter screen side.
 *
 * This is the iOS shell's stand-in for sensor pointing (drag to look around, pinch to zoom) until
 * a CoreMotion source feeds `SkyModel.pointing` the way the Android sensors do.
 */
data class LookDirection(
    val azimuthDeg: Double = 180.0,
    val altitudeDeg: Double = 30.0,
    val fovDeg: Double = 70.0,
) {
    /**
     * Drags the sky by ([dxPt], [dyPt]) screen points: the sky follows the finger, so the view
     * turns the opposite way. One point spans `fovDeg / shortSidePt` degrees.
     */
    fun dragged(
        dxPt: Double,
        dyPt: Double,
        shortSidePt: Double,
    ): LookDirection {
        val degPerPt = fovDeg / shortSidePt
        return copy(
            azimuthDeg = (azimuthDeg - dxPt * degPerPt).mod(360.0),
            altitudeDeg = (altitudeDeg + dyPt * degPerPt).coerceIn(-MAX_ALTITUDE, MAX_ALTITUDE),
        )
    }

    /** Pinch zoom: a [scale] above 1 (fingers apart) narrows the field of view. */
    fun zoomed(scale: Double): LookDirection =
        if (scale <= 0.0) this else copy(fovDeg = (fovDeg / scale).coerceIn(MIN_FOV, MAX_FOV))

    /** The celestial camera for this look direction in [frame] (true north, not magnetic). */
    fun toCamera(frame: LocalFrame): SkyCamera {
        val az = azimuthDeg * DEGREES_TO_RADIANS
        val alt = altitudeDeg * DEGREES_TO_RADIANS
        val horizontal = frame.trueNorth * cos(az) + frame.trueEast * sin(az)
        return SkyCamera(
            lineOfSight = horizontal * cos(alt) + frame.up * sin(alt),
            up = horizontal * -sin(alt) + frame.up * cos(alt),
            fovDeg = fovDeg,
        )
    }

    companion object {
        /** Just shy of the zenith so the camera's up vector never degenerates. */
        const val MAX_ALTITUDE = 89.9
        const val MIN_FOV = 5.0
        const val MAX_FOV = 120.0
    }
}
