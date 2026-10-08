/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.scene

import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class LookDirectionTest {
    private val frame =
        SkyModel.localFrame(
            Instant.parse("2026-01-01T22:00:00Z"),
            LatLong(45.0, 9.0),
        )

    @Test
    fun toCamera_atTheHorizonLooksAlongTheAzimuthWithZenithUp() {
        val south = LookDirection(azimuthDeg = 180.0, altitudeDeg = 0.0).toCamera(frame)
        assertClose(south.lineOfSight, -frame.trueNorth)
        assertClose(south.up, frame.up)

        val east = LookDirection(azimuthDeg = 90.0, altitudeDeg = 0.0).toCamera(frame)
        assertClose(east.lineOfSight, frame.trueEast)
    }

    @Test
    fun toCamera_isOrthonormal() {
        val camera = LookDirection(azimuthDeg = 237.0, altitudeDeg = 41.0).toCamera(frame)
        assertThat(camera.lineOfSight.length).isWithin(1e-12).of(1.0)
        assertThat(camera.up.length).isWithin(1e-12).of(1.0)
        assertThat(camera.lineOfSight dot camera.up).isWithin(1e-12).of(0.0)
    }

    @Test
    fun dragged_turnsAgainstTheFingerAndWrapsAzimuth() {
        val look = LookDirection(azimuthDeg = 5.0, altitudeDeg = 10.0, fovDeg = 60.0)
        // 600 pt short side at 60° FOV: 0.1°/pt. Finger right 100 pt → view turns 10° left.
        val moved = look.dragged(dxPt = 100.0, dyPt = 50.0, shortSidePt = 600.0)
        assertThat(moved.azimuthDeg).isWithin(1e-9).of(355.0)
        assertThat(moved.altitudeDeg).isWithin(1e-9).of(15.0)
    }

    @Test
    fun dragged_clampsAltitudeShortOfThePoles() {
        val up = LookDirection(altitudeDeg = 80.0).dragged(0.0, 10_000.0, shortSidePt = 100.0)
        assertThat(up.altitudeDeg).isEqualTo(LookDirection.MAX_ALTITUDE)
        // Still a valid camera: SkyCamera rejects a collinear up vector.
        up.toCamera(frame)
    }

    @Test
    fun zoomed_scalesAndClampsTheFieldOfView() {
        assertThat(LookDirection(fovDeg = 60.0).zoomed(2.0).fovDeg).isWithin(1e-9).of(30.0)
        assertThat(
            LookDirection(fovDeg = 60.0).zoomed(100.0).fovDeg,
        ).isEqualTo(LookDirection.MIN_FOV)
        assertThat(
            LookDirection(fovDeg = 60.0).zoomed(0.01).fovDeg,
        ).isEqualTo(LookDirection.MAX_FOV)
        assertThat(LookDirection(fovDeg = 60.0).zoomed(0.0).fovDeg).isEqualTo(60.0)
    }

    private fun assertClose(
        actual: Vector3,
        expected: Vector3,
    ) {
        assertThat(actual.distanceTo(expected)).isWithin(1e-9).of(0.0)
    }
}
