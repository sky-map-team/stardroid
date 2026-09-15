/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IdentifyGeometryTest {
    @Test
    fun `the screen center maps to the look direction`() {
        val direction =
            IdentifyGeometry.screenToDirection(CAMERA, WIDTH, HEIGHT, WIDTH / 2f, HEIGHT / 2f)
        assertThat(
            IdentifyGeometry.angularSeparationDeg(direction, CAMERA.lineOfSight),
        ).isLessThan(TOL_DEG)
    }

    @Test
    fun `screenToDirection inverts the shared projection`() {
        // Directions scattered around the view, including off both screen axes.
        val targets =
            listOf(
                Vector3(1.0, 0.05, 0.02),
                Vector3(1.0, -0.1, 0.08),
                Vector3(1.0, 0.12, -0.06),
                Vector3(1.0, -0.02, -0.11),
            ).map { it.normalized() }
        val projection = SkyProjection(CAMERA, Viewport(WIDTH, HEIGHT, density = 1f))
        for (target in targets) {
            val point = projection.worldToScreen(target)!!
            val roundTrip =
                IdentifyGeometry.screenToDirection(CAMERA, WIDTH, HEIGHT, point.xPx, point.yPx)
            assertThat(
                IdentifyGeometry.angularSeparationDeg(roundTrip, target),
            ).isLessThan(ROUND_TRIP_TOL_DEG)
        }
    }

    @Test
    fun `the tap threshold scales with zoom and floors at half a degree`() {
        assertThat(IdentifyGeometry.tapThresholdDeg(90.0)).isEqualTo(5.0)
        assertThat(IdentifyGeometry.tapThresholdDeg(45.0)).isEqualTo(2.5)
        // 5° FOV would scale to 0.28°; v1 floors the tolerance at 0.5°.
        assertThat(IdentifyGeometry.tapThresholdDeg(5.0)).isEqualTo(0.5)
    }

    @Test
    fun `the label-inclusive threshold widens the tolerance to cover the name`() {
        val base = IdentifyGeometry.tapThresholdDeg(45.0)
        val widened =
            IdentifyGeometry.labelInclusiveTapThresholdDeg(
                fovDeg = 45.0,
                shortSidePx = WIDTH,
                densityDpPerPx = 3f,
                labelScaleFactor = 1.0,
            )
        // 19 dp at 3x is 57 px, 1/18.9 of a 1080 px short side: 45° * 57/1080 ≈ 2.375°.
        assertThat(widened).isWithin(1e-6).of(base + 45.0 * 57.0 / 1080.0)
        // Comfortably bigger than a bare star, comfortably smaller than v1's own tolerance.
        assertThat(widened - base).isLessThan(base)
    }

    @Test
    fun `larger label sizes widen the tap target proportionally`() {
        fun thresholdAt(scale: Double) =
            IdentifyGeometry.labelInclusiveTapThresholdDeg(45.0, WIDTH, 3f, scale)
        val base = IdentifyGeometry.tapThresholdDeg(45.0)
        // The label-derived part doubles with the preference; v1's own tolerance does not.
        assertThat(thresholdAt(2.0) - base).isWithin(1e-6).of(2 * (thresholdAt(1.0) - base))
    }

    @Test
    fun `a degenerate viewport falls back to the plain tolerance`() {
        assertThat(IdentifyGeometry.labelInclusiveTapThresholdDeg(45.0, 0, 3f, 1.0))
            .isEqualTo(IdentifyGeometry.tapThresholdDeg(45.0))
        assertThat(IdentifyGeometry.labelInclusiveTapThresholdDeg(45.0, WIDTH, 0f, 1.0))
            .isEqualTo(IdentifyGeometry.tapThresholdDeg(45.0))
    }

    @Test
    fun `the label allowance shrinks with zoom, so dense fields stay selective`() {
        // The reach is a fixed pixel distance, so its angular size follows the FOV down.
        fun allowanceAt(fovDeg: Double) =
            IdentifyGeometry.labelInclusiveTapThresholdDeg(fovDeg, WIDTH, 3f, 1.0) -
                IdentifyGeometry.tapThresholdDeg(fovDeg)
        assertThat(allowanceAt(5.0)).isLessThan(allowanceAt(45.0))
        assertThat(allowanceAt(1.0)).isLessThan(0.2)
    }

    @Test
    fun `angular separation is the great-circle angle`() {
        assertThat(
            IdentifyGeometry.angularSeparationDeg(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
        ).isWithin(TOL_DEG).of(90.0)
        assertThat(
            IdentifyGeometry.angularSeparationDeg(Vector3(1.0, 0.0, 0.0), Vector3(1.0, 0.0, 0.0)),
        ).isWithin(TOL_DEG).of(0.0)
    }

    private companion object {
        // A portrait phone viewport; the FOV spans the shorter (width) side.
        const val WIDTH = 1080
        const val HEIGHT = 2280
        const val TOL_DEG = 1e-6

        // The projection returns Float pixels; their precision costs a few microdegrees,
        // noise against the 0.5° minimum tap tolerance.
        const val ROUND_TRIP_TOL_DEG = 1e-4
        val CAMERA =
            SkyCamera(
                lineOfSight = Vector3(1.0, 0.0, 0.0),
                up = Vector3(0.0, 0.0, 1.0),
                fovDeg = 60.0,
            )
    }
}
