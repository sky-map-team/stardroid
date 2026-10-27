/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The per-frame geometry of an [ImagePrimitive] drawn as a world-anchored quad — the portable
 * half of a programmable backend's image drawer (D128). Everything here depends on the field of
 * view, so it is evaluated at draw time: a producer's ~1 Hz resubmission cannot follow a pinch
 * (D86). The backend supplies only the texture and the draw call.
 */
object ImageQuad {
    private val WORLD_UP = Vector3.UNIT_Y
    private val WORLD_UP_FALLBACK = Vector3.UNIT_Z

    /** The quad's rotated, scaled half-axes in world units: `u` right, `v` up on screen. */
    data class HalfAxes(val u: Vector3, val v: Vector3)

    /** A direction in the image's texture-space disc frame: +x across, +y toward its north. */
    data class DiscVector(val x: Double, val y: Double)

    /**
     * The diameter to draw [image] at in this frame, in degrees — its true size or the
     * [SizeFloor], whichever is larger — or null when [ImagePrimitive.visibleBelowFovDeg] hides it
     * at this field of view.
     */
    fun drawnDiameterDeg(
        image: ImagePrimitive,
        camera: SkyCamera,
        viewport: Viewport,
    ): Double? {
        val below = image.visibleBelowFovDeg
        if (below != null && camera.fovDeg > below) return null
        return SizeFloor.drawnDiameterDeg(
            image.angularSizeDeg,
            image.minScreenFraction,
            image.minSizeDp,
            camera.fovDeg,
            min(viewport.widthPx, viewport.heightPx),
            viewport.density,
        )
    }

    /**
     * The quad's half-axes at [drawnDiameterDeg], in GLES1's `quadCorners` frame:
     *
     * - `horizontal = −(center × WORLD_UP).normalized` (right on screen by convention)
     * - `vertical = horizontal × center` (up on screen by convention)
     *
     * [ImagePrimitive.rotationDeg] rotates the frame around the centre; the half-axis scale is
     * `sin(drawnDiameterDeg / 2)`, the chord half-length on the unit sphere, so a 10° image
     * covers 10° of sky. Falls back to [WORLD_UP_FALLBACK] when the centre is within 8° of the
     * Y-pole and the cross product degenerates (D30).
     */
    fun halfAxes(
        image: ImagePrimitive,
        drawnDiameterDeg: Double = image.angularSizeDeg,
    ): HalfAxes {
        val center = image.center
        var cross = center cross WORLD_UP
        // sin²(8°) ≈ 0.01937.
        if (cross.length2 < 0.01937) cross = center cross WORLD_UP_FALLBACK
        val horizontal = -cross.normalized()
        val vertical = horizontal cross center

        val rotRad = image.rotationDeg * DEGREES_TO_RADIANS
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)
        val u = horizontal * cosR + vertical * sinR
        val v = horizontal * (-sinR) + vertical * cosR

        val scale = sin(drawnDiameterDeg / 2.0 * DEGREES_TO_RADIANS)
        return HalfAxes(u * scale, v * scale)
    }

    /**
     * The lit limb's direction in texture space. [ImagePrimitive.rotationDeg] has already turned
     * the quad so +y is the body's north pole, and the texture frame is a view of the sky from
     * inside, so east runs to −x: a position angle east of north is `(−sin, cos)` here.
     */
    fun litDirection(terminator: Terminator): DiscVector =
        positionAngle(terminator.brightLimbAngleDeg)

    /** The Earth's shadow axis in the same texture-space frame, in Moon radii from the centre. */
    fun shadowCenter(eclipse: EclipseShadow): DiscVector {
        val direction = positionAngle(eclipse.directionDeg)
        return DiscVector(direction.x * eclipse.offset, direction.y * eclipse.offset)
    }

    private fun positionAngle(degrees: Double): DiscVector {
        val chi = degrees * DEGREES_TO_RADIANS
        return DiscVector(-sin(chi), cos(chi))
    }
}
