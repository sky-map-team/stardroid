/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PX_TOL = 1e-3

class SkyProjectionTest {
    // Looking down +X with up = +Z. right = lookDir × up = (0, -1, 0), so screen-right is -Y.
    private val camera = SkyCamera(lineOfSight = Vector3.UNIT_X, up = Vector3.UNIT_Z, fovDeg = 90.0)
    private val square = Viewport(widthPx = 600, heightPx = 600, density = 1f)

    @Test
    fun lineOfSight_projectsToScreenCenter() {
        val projection = SkyProjection(camera, square)
        val p = projection.worldToScreen(camera.lineOfSight)!!
        assertThat(p.xPx.toDouble()).isWithin(PX_TOL).of(300.0)
        assertThat(p.yPx.toDouble()).isWithin(PX_TOL).of(300.0)
    }

    @Test
    fun fortyFiveDegreesRight_landsAtRightEdge_forSquareAspect() {
        // Square aspect + 90° vertical FOV ⇒ 90° horizontal FOV; a 45° offset reaches the edge.
        val projection = SkyProjection(camera, square)
        val right = direction(azDeg = 45.0, altDeg = 0.0)
        val p = projection.worldToScreen(right)!!
        assertThat(p.xPx.toDouble()).isWithin(PX_TOL).of(600.0) // right edge
        assertThat(p.yPx.toDouble()).isWithin(PX_TOL).of(300.0) // vertically centered
    }

    @Test
    fun fortyFiveDegreesUp_landsAtTopEdge_topLeftOrigin() {
        val projection = SkyProjection(camera, square)
        val up = direction(azDeg = 0.0, altDeg = 45.0)
        val p = projection.worldToScreen(up)!!
        assertThat(p.xPx.toDouble()).isWithin(PX_TOL).of(300.0)
        assertThat(p.yPx.toDouble()).isWithin(PX_TOL).of(0.0) // y=0 is the top
    }

    @Test
    fun pointBehindViewer_returnsNull() {
        val projection = SkyProjection(camera, square)
        assertThat(projection.worldToScreen(-camera.lineOfSight)).isNull()
        // Exactly on the eye plane (90° away) is also rejected.
        assertThat(projection.worldToScreen(direction(azDeg = 90.0, altDeg = 0.0))).isNull()
    }

    @Test
    fun pointBeyondFov_projectsOffScreen() {
        val projection = SkyProjection(camera, square)
        val p = projection.worldToScreen(direction(azDeg = 60.0, altDeg = 0.0))!!
        assertThat(p.xPx).isGreaterThan(600f) // past the right edge
    }

    @Test
    fun wideAspect_widensHorizontalFovOnly() {
        // Aspect 2:1 with the same 90° vertical FOV ⇒ a 45°-right point no longer reaches the edge.
        val wide = Viewport(widthPx = 1200, heightPx = 600, density = 1f)
        val p = SkyProjection(camera, wide).worldToScreen(direction(azDeg = 45.0, altDeg = 0.0))!!
        // ndcX = 0.5 ⇒ 3/4 across a 1200px surface; vertical unchanged.
        assertThat(p.xPx.toDouble()).isWithin(PX_TOL).of(900.0)
        assertThat(p.yPx.toDouble()).isWithin(PX_TOL).of(300.0)
    }

    @Test
    fun allDirectionsInFrontHemisphere_project_andSymmetricPointsMirror() {
        val projection = SkyProjection(camera, square)
        val rng = Random(20260623)
        repeat(1000) {
            val az = rng.nextDouble(-44.0, 44.0)
            val alt = rng.nextDouble(-44.0, 44.0)
            val a = projection.worldToScreen(direction(az, alt))!!
            val mirrored = projection.worldToScreen(direction(-az, alt))!!
            // Mirroring azimuth reflects x about the centre and leaves y put.
            assertThat((a.xPx + mirrored.xPx).toDouble()).isWithin(1e-2).of(600.0)
            assertThat(a.yPx.toDouble()).isWithin(1e-2).of(mirrored.yPx.toDouble())
        }
    }

    /**
     * A unit direction [azDeg] to the screen-right and [altDeg] up from the line of sight, in this
     * test's camera frame (lookDir +X, up +Z, right −Y).
     */
    private fun direction(
        azDeg: Double,
        altDeg: Double,
    ): Vector3 {
        val az = Math.toRadians(azDeg)
        val alt = Math.toRadians(altDeg)
        // Start at lookDir (+X); rotate right (toward −Y) by az and up (toward +Z) by alt.
        return Vector3(
            cos(alt) * cos(az),
            -cos(alt) * sin(az),
            sin(alt),
        )
    }
}
