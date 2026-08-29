/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.camera

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CameraFovTest {
    @Test
    fun `full-screen phone preview crops the short axis`() {
        // A typical 1/2.55" main camera (6.4×4.8 mm, f=4.38 mm) behind a 20:9 screen:
        // FILL_CENTER keeps the sensor's long axis and crops the short one to 6.4/2.1667 mm.
        val fov =
            cameraShortSideFovDeg(
                sensorLongSideMm = 6.4,
                sensorShortSideMm = 4.8,
                focalLengthMm = 4.38,
                viewAspectLongOverShort = 6.5 / 3.0,
            )
        assertThat(fov).isNotNull()
        assertThat(fov!!).isWithin(0.1).of(37.3)
    }

    @Test
    fun `view fatter than the sensor keeps the whole short axis`() {
        // A square-ish view (aspect 1.0) against a 4:3 sensor: the short axis survives
        // whole, so the FOV is the sensor's full short-side FOV.
        val fov =
            cameraShortSideFovDeg(
                sensorLongSideMm = 6.4,
                sensorShortSideMm = 4.8,
                focalLengthMm = 4.38,
                viewAspectLongOverShort = 1.0,
            )
        assertThat(fov).isNotNull()
        assertThat(fov!!).isWithin(0.1).of(57.4)
    }

    @Test
    fun `matching aspects agree with the uncropped short side`() {
        val cropped =
            cameraShortSideFovDeg(6.4, 4.8, 4.38, viewAspectLongOverShort = 6.4 / 4.8)
        val whole = cameraShortSideFovDeg(6.4, 4.8, 4.38, viewAspectLongOverShort = 1.0)
        assertThat(cropped!!).isWithin(1e-9).of(whole!!)
    }

    @Test
    fun `degenerate characteristics return null instead of nonsense`() {
        assertThat(cameraShortSideFovDeg(0.0, 4.8, 4.38, 2.0)).isNull()
        assertThat(cameraShortSideFovDeg(6.4, 0.0, 4.38, 2.0)).isNull()
        assertThat(cameraShortSideFovDeg(6.4, 4.8, 0.0, 2.0)).isNull()
        assertThat(cameraShortSideFovDeg(6.4, 4.8, 4.38, 0.9)).isNull()
    }
}
