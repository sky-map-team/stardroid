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
import com.google.android.stardroid.testing.assertThat
import com.google.android.stardroid.ui.map.ArCameraSpecs
import kotlin.math.tan
import kotlin.test.Test

class ArLensFovTest {
    // A dual-wide's two lenses, as tangents of the visible short half-side.
    private val ultraWide = 0.64
    private val wide = 0.33

    private fun specs(vararg tans: Double?) =
        ArCameraSpecs(
            shortSideFovDeg = fullAngleDeg(wide),
            zoomMin = 0.5,
            zoomMax = 8.0,
            exposureMin = 0,
            exposureMax = 0,
            lenses = arLenses(listOf(2.0), tans.toList(), mainFactor = 2.0),
        )

    @Test
    fun bothEntryPointsAgreeOnTheSameLens() {
        val fromSensor = cameraShortSideFovDeg(6.4, 4.8, 4.38, 20.0 / 9.0)!!
        val horizontal = fullAngleDeg(6.4 / (2 * 4.38))
        val fromFov = cameraShortHalfTan(horizontal, 4032, 3024, 900.0, 2000.0, landscape = false)!!
        assertThat(fullAngleDeg(fromFov)).isWithin(1e-9).of(fromSensor)
    }

    @Test
    fun turningThePhoneKeepsTheShortSideFov() {
        val portrait = cameraShortHalfTan(70.0, 4, 3, 390.0, 844.0, landscape = false)!!
        val landscape = cameraShortHalfTan(70.0, 4, 3, 844.0, 390.0, landscape = true)!!
        assertThat(landscape).isWithin(1e-12).of(portrait)
    }

    @Test
    fun aTallWindowOnASidewaysTabletIsCroppedFromTheWideImage() {
        val halfTan = cameraShortHalfTan(70.0, 4, 3, 500.0, 1000.0, landscape = true)!!
        // The image's whole height shows, and half of that across the window.
        val imageHalfHeight = tan(35.0 * DEGREES_TO_RADIANS) * 3 / 4
        assertThat(halfTan).isWithin(1e-12).of(imageHalfHeight / 2)
    }

    @Test
    fun theWidestZoomShowsTheWholeUltraWideView() {
        val fov = specs(ultraWide, wide).fovAtZoom(0.5)!!
        assertThat(fov).isWithin(1e-9).of(fullAngleDeg(ultraWide))
    }

    @Test
    fun belowTheSwitchTheUltraWideIsCropped() {
        val fov = specs(ultraWide, wide).fovAtZoom(0.999)!!
        assertThat(fov).isWithin(1e-9).of(fullAngleDeg(ultraWide / (0.999 / 0.5)))
    }

    @Test
    fun atTheSwitchTheWideLensTakesOver() {
        val fov = specs(ultraWide, wide).fovAtZoom(1.0)!!
        assertThat(fov).isWithin(1e-9).of(fullAngleDeg(wide))
    }

    @Test
    fun aboveTheSwitchTheWideLensIsCropped() {
        val fov = specs(ultraWide, wide).fovAtZoom(3.0)!!
        assertThat(fov).isWithin(1e-9).of(fullAngleDeg(wide / 3.0))
    }

    @Test
    fun anUnknownLensTakesOverWithoutAJump() {
        val fov = specs(ultraWide, null).fovAtZoom(1.0)!!
        assertThat(fov).isWithin(1e-9).of(fullAngleDeg(ultraWide / 2.0))
    }

    @Test
    fun withoutLensesTheFovIsDividedByTheZoom() {
        val android = specs(ultraWide, wide).copy(shortSideFovDeg = 50.0, lenses = emptyList())
        assertThat(android.fovAtZoom(2.0)!!).isWithin(1e-12).of(25.0)
    }

    @Test
    fun degenerateFormatsAreRejected() {
        assertThat(cameraShortHalfTan(0.0, 4, 3, 1.0, 2.0, landscape = false)).isNull()
        assertThat(cameraShortHalfTan(70.0, 0, 3, 1.0, 2.0, landscape = false)).isNull()
        assertThat(cameraShortHalfTan(70.0, 4, 3, 0.0, 2.0, landscape = false)).isNull()
    }
}
