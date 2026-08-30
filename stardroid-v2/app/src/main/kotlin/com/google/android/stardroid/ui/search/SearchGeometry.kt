/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.search

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.min

/**
 * The pure math behind the search overlay (layers-and-app.md): the arrow's screen bearing and
 * the seeking-color distance, derived from the same [SkyCamera] the renderer projects with. The
 * on-screen/off-screen decision itself uses `SkyProjection.worldToScreen` in the overlay — this
 * object covers the directions that projection can't give (targets beside or behind the eye).
 */
object SearchGeometry {
    /**
     * Bearing from screen center toward [target], radians in **screen space**: 0 points right,
     * positive turns clockwise (y grows downward). Defined for every direction except the exact
     * look-direction axis — v1's `SearchArrow` phi/theta difference plus camera roll collapses
     * to exactly this: the target's components along the camera's right and up axes.
     */
    fun screenBearingRad(
        camera: SkyCamera,
        target: Vector3,
    ): Double {
        // gluLookAt's s-axis: the camera's screen-right direction.
        val right = (camera.lineOfSight cross camera.up).normalized()
        val dx = target dot right
        val dy = target dot camera.up
        return atan2(-dy, dx)
    }

    /**
     * Great-circle angle between the look direction and the target, normalized to 0..1 (0 =
     * dead ahead, 1 = directly behind). Drives v1's red-near → blue-far arrow tint; v1 used a
     * phi/theta metric normalized by √2·π — same endpoints, and monotonic like this one.
     */
    fun normalizedSeparation(
        camera: SkyCamera,
        target: Vector3,
    ): Double =
        acos((camera.lineOfSight.normalized() dot target.normalized()).coerceIn(-1.0, 1.0)) /
            Math.PI

    /**
     * v1 `SearchHelper.targetInFocusRadiusImpl`: found when the target projects within half of
     * the focus radius — `min(W, H) − 20` px — of screen center; behind the eye is never found.
     */
    fun isTargetFound(
        camera: SkyCamera,
        widthPx: Int,
        heightPx: Int,
        target: Vector3,
    ): Boolean {
        if (widthPx <= 0 || heightPx <= 0) return false
        // Density only scales dp-sized primitives; the projection matrix ignores it.
        val point =
            SkyProjection(camera, Viewport(widthPx, heightPx, density = 1f))
                .worldToScreen(target)
                ?: return false
        val dx = point.xPx - widthPx / 2f
        val dy = point.yPx - heightPx / 2f
        val focusRadius = min(widthPx, heightPx) - FOCUS_RADIUS_INSET_PX
        val threshold = 0.5f * focusRadius
        return dx * dx + dy * dy < threshold * threshold
    }

    /** v1 `OverlayManager.resize`: the focus radius is the short screen side less this inset. */
    const val FOCUS_RADIUS_INSET_PX = 20f
}
