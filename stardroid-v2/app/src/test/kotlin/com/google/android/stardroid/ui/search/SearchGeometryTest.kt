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
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.atan2

class SearchGeometryTest {
    // Looking down +x with celestial north up; screen-right is then -y (gluLookAt's s-axis).
    private val camera = SkyCamera(Vector3(1.0, 0.0, 0.0), Vector3.UNIT_Z, 45.0)
    private val viewport = Viewport(1000, 2000, density = 1f)

    @Test
    fun `bearing points right for a target right of the view`() {
        val bearing = SearchGeometry.screenBearingRad(camera, Vector3(1.0, -0.2, 0.0))
        assertThat(bearing).isWithin(TOL).of(0.0)
    }

    @Test
    fun `bearing points up for a target above the view`() {
        // Screen y grows downward, so "up" is -π/2.
        val bearing = SearchGeometry.screenBearingRad(camera, Vector3(1.0, 0.0, 0.3))
        assertThat(bearing).isWithin(TOL).of(-PI / 2)
    }

    @Test
    fun `bearing agrees with the shared projection for on-screen targets`() {
        // The arrow must never contradict the drawn sky (D21): for any projectable target the
        // geometric bearing and the worldToScreen pixel delta from center must match.
        val projection = SkyProjection(camera, viewport)
        val targets =
            listOf(
                Vector3(1.0, -0.1, 0.05),
                Vector3(1.0, 0.15, -0.1),
                Vector3(1.0, 0.02, 0.2),
            )
        for (target in targets) {
            val point = projection.worldToScreen(target.normalized())!!
            val expected =
                atan2(
                    (point.yPx - viewport.heightPx / 2f).toDouble(),
                    (point.xPx - viewport.widthPx / 2f).toDouble(),
                )
            val bearing = SearchGeometry.screenBearingRad(camera, target.normalized())
            assertThat(bearing).isWithin(BEARING_TOL).of(expected)
        }
    }

    @Test
    fun `bearing is defined for a target behind the viewer`() {
        // Directly behind but slightly up: the arrow should point up, not crash or NaN.
        val bearing = SearchGeometry.screenBearingRad(camera, Vector3(-1.0, 0.0, 0.1))
        assertThat(bearing).isWithin(TOL).of(-PI / 2)
    }

    @Test
    fun `normalized separation runs from ahead 0 to behind 1`() {
        assertThat(SearchGeometry.normalizedSeparation(camera, Vector3(1.0, 0.0, 0.0)))
            .isWithin(TOL)
            .of(0.0)
        assertThat(SearchGeometry.normalizedSeparation(camera, Vector3.UNIT_Z))
            .isWithin(TOL)
            .of(0.5)
        assertThat(SearchGeometry.normalizedSeparation(camera, Vector3(-1.0, 0.0, 0.0)))
            .isWithin(TOL)
            .of(1.0)
    }

    @Test
    fun `target dead ahead is found`() {
        assertThat(
            SearchGeometry.isTargetFound(camera, 1000, 2000, Vector3(1.0, 0.0, 0.0)),
        ).isTrue()
    }

    @Test
    fun `target behind the viewer is never found`() {
        assertThat(
            SearchGeometry.isTargetFound(camera, 1000, 2000, Vector3(-1.0, 0.0, 0.0)),
        ).isFalse()
    }

    @Test
    fun `target outside half the focus radius is not found`() {
        // Just inside the screen edge horizontally: distance from center ≈ 490 px, which is
        // just within half the focus radius ((1000 − 20) / 2 = 490) — nudge further out via a
        // target that projects past it vertically instead.
        assertThat(
            SearchGeometry.isTargetFound(camera, 1000, 2000, Vector3(1.0, 0.0, 0.45)),
        ).isFalse()
    }

    @Test
    fun `zero-sized surface is never found`() {
        assertThat(
            SearchGeometry.isTargetFound(camera, 0, 0, Vector3(1.0, 0.0, 0.0)),
        ).isFalse()
    }

    private companion object {
        const val TOL = 1e-9
        const val BEARING_TOL = 1e-6
    }
}
