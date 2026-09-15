/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.tan

/**
 * The pure math behind tap-to-identify (v1 `CelestialHitTester`): a screen pixel becomes a sky
 * direction, and the nearest curated object within an FOV-scaled angular threshold is the hit.
 *
 * [screenToDirection] is the exact inverse of the shared projection's pinhole model
 * (`Matrix4.perspective`): the FOV spans the *shorter* viewport side, so a tap on the drawn
 * image of an object round-trips to that object's direction through `SkyProjection`.
 */
object IdentifyGeometry {
    /** v1: tap tolerance at the widest zoom ([MAX_FOV_DEG]), scaling down as the user zooms. */
    const val TAP_THRESHOLD_DEGREES = 5.0

    /** v1 `CelestialHitTester.MAX_FOV`. */
    const val MAX_FOV_DEG = 90.0

    /** v1: the tolerance floor at high zoom. */
    const val MIN_TAP_THRESHOLD_DEGREES = 0.5

    /**
     * How far below its anchor a typical label's *centre* sits, in dp: the common icon gap
     * (`DSO_ICON_SIZE_DP / 2 + 2` = 12 dp) plus half a line of 15 sp title text. The centre,
     * not the far edge — a tap only has to land nearer this object than any other, so aiming
     * at the middle of the word is what the tolerance must reach.
     *
     * Deliberately a constant rather than plumbed from the renderer; see
     * [labelInclusiveTapThresholdDeg].
     */
    private const val LABEL_REACH_DP = 19.0

    /** v1's tolerance for the current zoom: proportional scaling with a floor. */
    fun tapThresholdDeg(fovDeg: Double): Double =
        (TAP_THRESHOLD_DEGREES * fovDeg / MAX_FOV_DEG).coerceAtLeast(MIN_TAP_THRESHOLD_DEGREES)

    /**
     * The tap tolerance including the object's label, which users aim at at least as often as
     * the object itself — a name is a far bigger target than a 2 px dot, and tapping "Jupiter"
     * and getting nothing reads as the feature being broken.
     *
     * Labels hang *below* their anchor in screen space by a dp offset (D68), so the exact
     * region is an asymmetric, zoom-independent screen-space rectangle, while this threshold is
     * a symmetric angle. Rather than plumb laid-out label rectangles back from the renderer —
     * which the `render/api` contract does not expose, and which would make identify depend on
     * a frame having been drawn — this converts the label's screen reach into the angle it
     * subtends at the current zoom and adds it to v1's tolerance.
     *
     * That over-widens sideways and upward by the same amount it correctly widens downward.
     * The cost is bounded and small: the reach is a fixed pixel distance, so at high zoom it
     * spans a tiny angle, and it is the *nearest* candidate that wins regardless. Scaling with
     * [labelScaleFactor] means users who enlarge their labels get proportionally larger targets,
     * which is the behaviour they are asking for anyway.
     *
     * @param shortSidePx the viewport's shorter side, which [fovDeg] spans (D21).
     * @param densityDpPerPx `Density.density` — px per dp.
     * @param labelScaleFactor `RenderState.labelScaleFactor`, the user's label-size preference.
     */
    fun labelInclusiveTapThresholdDeg(
        fovDeg: Double,
        shortSidePx: Int,
        densityDpPerPx: Float,
        labelScaleFactor: Double,
    ): Double {
        val base = tapThresholdDeg(fovDeg)
        if (shortSidePx <= 0 || densityDpPerPx <= 0f) return base
        val reachPx = LABEL_REACH_DP * labelScaleFactor * densityDpPerPx
        // Degrees per pixel is not constant across a pinhole projection, but the label reach is
        // a few percent of the viewport, so the small-angle ratio is accurate well inside the
        // tolerance's own precision.
        return base + fovDeg * (reachPx / shortSidePx)
    }

    /**
     * The unit geocentric direction under screen pixel ([xPx], [yPx]), top-left origin. Inverts
     * `SkyProjection.worldToScreen` for points in front of the eye: NDC recovers the view-space
     * ray via `tan(fov/2)` at the short viewport edge, un-rotated by the camera basis.
     */
    fun screenToDirection(
        camera: SkyCamera,
        widthPx: Int,
        heightPx: Int,
        xPx: Float,
        yPx: Float,
    ): Vector3 {
        if (widthPx <= 0 || heightPx <= 0) return camera.lineOfSight.normalized()
        val shortPx = min(widthPx, heightPx).toDouble()
        val tanHalfFov = tan(camera.fovDeg * DEGREES_TO_RADIANS / 2.0)
        val ndcX = 2.0 * xPx / widthPx - 1.0
        // Screen y grows downward; NDC y grows upward.
        val ndcY = 1.0 - 2.0 * yPx / heightPx
        val look = camera.lineOfSight.normalized()
        val up = camera.up.normalized()
        // gluLookAt's s-axis: the camera's screen-right direction.
        val right = (look cross up).normalized()
        val rightOffset = ndcX * tanHalfFov * widthPx / shortPx
        val upOffset = ndcY * tanHalfFov * heightPx / shortPx
        return (look + right * rightOffset + up * upOffset).normalized()
    }

    /** Great-circle angle between two unit directions, degrees. */
    fun angularSeparationDeg(
        a: Vector3,
        b: Vector3,
    ): Double = acos((a dot b).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
}
