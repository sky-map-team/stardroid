/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.camera

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.ui.map.ArLens
import kotlin.math.atan
import kotlin.math.tan

/**
 * Android's camera FOV across the view's shorter side in degrees, as `SkyCamera.fovDeg` is (D21),
 * from sensor geometry: phone sensors mount landscape. Null, leaving the map's zoom free, on
 * degenerate inputs.
 */
fun cameraShortSideFovDeg(
    sensorLongSideMm: Double,
    sensorShortSideMm: Double,
    focalLengthMm: Double,
    viewAspectLongOverShort: Double,
): Double? {
    if (sensorLongSideMm <= 0.0 || sensorShortSideMm <= 0.0) return null
    if (focalLengthMm <= 0.0 || viewAspectLongOverShort < 1.0) return null
    val halfTan =
        visibleShortHalfTan(
            imageHalfWidth = sensorShortSideMm / (2.0 * focalLengthMm),
            imageHalfHeight = sensorLongSideMm / (2.0 * focalLengthMm),
            viewWidth = 1.0,
            viewHeight = viewAspectLongOverShort,
        )
    return fullAngleDeg(halfTan)
}

/**
 * iOS's entry point: the tangent of the visible short half-side, from a format's horizontal FOV.
 *
 * [formatWidth] × [formatHeight] is the unrotated sensor image, and [sensorWidthAlongViewWidth]
 * whether its width lies along [viewWidth] in this view. Null on degenerate inputs.
 */
fun cameraShortHalfTan(
    horizontalFovDeg: Double,
    formatWidth: Int,
    formatHeight: Int,
    viewWidth: Double,
    viewHeight: Double,
    sensorWidthAlongViewWidth: Boolean,
): Double? {
    if (horizontalFovDeg <= 0.0 || horizontalFovDeg >= 180.0) return null
    if (formatWidth <= 0 || formatHeight <= 0 || viewWidth <= 0.0 || viewHeight <= 0.0) return null
    val along = tan(horizontalFovDeg * DEGREES_TO_RADIANS / 2.0)
    val across = along * formatHeight / formatWidth
    return visibleShortHalfTan(
        imageHalfWidth = if (sensorWidthAlongViewWidth) along else across,
        imageHalfHeight = if (sensorWidthAlongViewWidth) across else along,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
    )
}

/**
 * A virtual multi-lens camera's lenses, widest first, in zoom ratios where 1 is the main wide
 * lens (as CameraX counts them).
 *
 * @param switchOverFactors the zoom factors at which each lens after the first takes over, with
 * 1 the widest lens's whole view.
 * @param halfTans each lens's own unzoomed [cameraShortHalfTan]; one that is unknown is assumed
 * to take over without a jump.
 * @param mainFactor the zoom factor that is ratio 1.
 */
fun arLenses(
    switchOverFactors: List<Double>,
    halfTans: List<Double?>,
    mainFactor: Double,
): List<ArLens> {
    val widest = halfTans.firstOrNull() ?: return emptyList()
    if (mainFactor <= 0.0) return emptyList()
    return (listOf(1.0) + switchOverFactors).mapIndexed { index, factor ->
        ArLens(factor / mainFactor, halfTans.getOrNull(index) ?: (widest / factor))
    }
}

fun fullAngleDeg(halfTan: Double): Double = 2.0 * atan(halfTan) * RADIANS_TO_DEGREES

// The preview fills the view and crops the overflow, centred, on both platforms.
private fun visibleShortHalfTan(
    imageHalfWidth: Double,
    imageHalfHeight: Double,
    viewWidth: Double,
    viewHeight: Double,
): Double {
    val scale = maxOf(viewWidth / imageHalfWidth, viewHeight / imageHalfHeight)
    return minOf(viewWidth, viewHeight) / scale
}

/** [requested] limited to the session's zoom range; unchanged while the range is unknown. */
fun clampZoom(
    requested: Float,
    min: Float?,
    max: Float?,
): Float = if (min == null || max == null) requested else requested.coerceIn(min, max)
