/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.camera

import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import kotlin.math.atan

/**
 * The physical camera's field of view across the preview's **shorter** side, in degrees —
 * the one number that locks the map's zoom to the camera (camera-ar-mode.md/D64: the map's
 * `SkyCamera.fovDeg` is also short-side, D21).
 *
 * Pure math, split from the CameraX plumbing so it is JVM-testable: `2·atan(visible/2f)`
 * over the sensor extent actually *visible* after the preview's FILL_CENTER crop. The
 * preview fills the view and crops the overflow, so the visible region is the largest
 * view-aspect region inside the sensor; phone sensors mount landscape, so the view's short
 * side always maps onto the sensor's short axis.
 *
 * Returns null on degenerate inputs (missing/zero characteristics) — the caller falls back
 * to leaving the map FOV unlocked.
 */
fun cameraShortSideFovDeg(
    sensorLongSideMm: Double,
    sensorShortSideMm: Double,
    focalLengthMm: Double,
    viewAspectLongOverShort: Double,
): Double? {
    if (sensorLongSideMm <= 0.0 || sensorShortSideMm <= 0.0) return null
    if (focalLengthMm <= 0.0 || viewAspectLongOverShort < 1.0) return null
    val sensorAspect = sensorLongSideMm / sensorShortSideMm
    // View skinnier than the sensor (every modern phone): the full long axis shows and the
    // short axis is cropped to match. Otherwise the short axis survives whole.
    val visibleShortSideMm =
        if (viewAspectLongOverShort >= sensorAspect) {
            sensorLongSideMm / viewAspectLongOverShort
        } else {
            sensorShortSideMm
        }
    return 2.0 * atan(visibleShortSideMm / (2.0 * focalLengthMm)) * RADIANS_TO_DEGREES
}
