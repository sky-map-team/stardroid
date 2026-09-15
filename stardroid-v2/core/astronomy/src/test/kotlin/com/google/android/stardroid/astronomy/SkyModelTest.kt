/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported from v1 `AstronomerModelTest` and `AstronomerModelWithMagneticVariationTest`, restated
 * against the pure [SkyModel]: each case fuses raw sensor vectors with [orientationFromSensors],
 * builds a [LocalFrame], and checks the frame axes and resolved [Pointing].
 */
class SkyModelTest {
    /** The phone is flat, long side pointing North at lat, long = 0, 90. */
    @Test
    fun phoneFlatAtLat0Long90() {
        checkModelOrientation(
            location = LatLong(0.0, 90.0),
            // Phone flat on back, top edge towards North; phone coordinates.
            acceleration = Vector3(0.0, 0.0, 10.0),
            magneticField = Vector3(0.0, 1.0, 10.0),
            // Celestial coordinates from here on.
            expectedZenith = Vector3(0.0, 1.0, 0.0),
            expectedNorth = Vector3(0.0, 0.0, 1.0),
            expectedEast = Vector3(-1.0, 0.0, 0.0),
            expectedPointing = Vector3(0.0, -1.0, 0.0),
            expectedUpAlongPhone = Vector3(0.0, 0.0, 1.0),
        )
    }

    /** As previous test, but at lat, long = (45, 0). */
    @Test
    fun phoneFlatAtLat45Long0() {
        checkModelOrientation(
            location = LatLong(45.0, 0.0),
            acceleration = Vector3(0.0, 0.0, 10.0),
            magneticField = Vector3(0.0, 10.0, 0.0),
            expectedZenith = Vector3(1 / SQRT2, 0.0, 1 / SQRT2),
            expectedNorth = Vector3(-1 / SQRT2, 0.0, 1 / SQRT2),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(-1 / SQRT2, 0.0, -1 / SQRT2),
            expectedUpAlongPhone = Vector3(-1 / SQRT2, 0.0, 1 / SQRT2),
        )
    }

    /** As previous test, but at lat, long = (0, 0). */
    @Test
    fun phoneFlatOnEquatorAtMeridian() {
        checkModelOrientation(
            location = LatLong(0.0, 0.0),
            acceleration = Vector3(0.0, 0.0, 10.0),
            magneticField = Vector3(0.0, 1.0, 10.0),
            expectedZenith = Vector3(1.0, 0.0, 0.0),
            expectedNorth = Vector3(0.0, 0.0, 1.0),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(-1.0, 0.0, 0.0),
            expectedUpAlongPhone = Vector3(0.0, 0.0, 1.0),
        )
    }

    /** As previous test, but with the phone vertical, in landscape mode and pointing east. */
    @Test
    fun phoneLandscapeFacingEastOnEquatorAtMeridian() {
        checkModelOrientation(
            location = LatLong(0.0, 0.0),
            acceleration = Vector3(10.0, 0.0, 0.0),
            magneticField = Vector3(-10.0, 1.0, 0.0),
            expectedZenith = Vector3(1.0, 0.0, 0.0),
            expectedNorth = Vector3(0.0, 0.0, 1.0),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(0.0, 1.0, 0.0),
            expectedUpAlongPhone = Vector3(0.0, 0.0, 1.0),
        )
    }

    /** As previous test, but in portrait mode facing north. */
    @Test
    fun phoneStandingUpFacingNorthOnEquatorAtMeridian() {
        checkModelOrientation(
            location = LatLong(0.0, 0.0),
            acceleration = Vector3(0.0, 10.0, 0.0),
            magneticField = Vector3(0.0, 10.0, -1.0),
            expectedZenith = Vector3(1.0, 0.0, 0.0),
            expectedNorth = Vector3(0.0, 0.0, 1.0),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(0.0, 0.0, 1.0),
            expectedUpAlongPhone = Vector3(1.0, 0.0, 0.0),
        )
    }

    // ---- Magnetic-variation cases (v1 AstronomerModelWithMagneticVariationTest) ------------

    @Test
    fun flatOnEquatorMag0Degrees() {
        checkModelOrientation(
            location = LatLong(0.0, 0.0),
            // Phone flat on back, top edge towards North; field coming in from N.
            acceleration = Vector3(0.0, 0.0, 10.0),
            magneticField = Vector3(0.0, 5.0, -10.0),
            expectedZenith = Vector3(1.0, 0.0, 0.0),
            expectedNorth = Vector3(0.0, 0.0, 1.0),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(-1.0, 0.0, 0.0),
            expectedUpAlongPhone = Vector3(0.0, 0.0, 1.0),
            magneticDeclinationDeg = 0.0,
        )
    }

    @Test
    fun flatOnEquatorMagN45DegreesW() {
        checkModelOrientation(
            location = LatLong(0.0, 0.0),
            // Field coming in from NW.
            acceleration = Vector3(0.0, 0.0, 10.0),
            magneticField = Vector3(-1.0, 1.0, -10.0),
            expectedZenith = Vector3(1.0, 0.0, 0.0),
            expectedNorth = Vector3(0.0, 0.0, 1.0),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(-1.0, 0.0, 0.0),
            expectedUpAlongPhone = Vector3(0.0, 0.0, 1.0),
            magneticDeclinationDeg = -45.0,
        )
    }

    @Test
    fun standingUpOnEquatorMagN10DegreesEast() {
        checkModelOrientation(
            location = LatLong(0.0, 0.0),
            acceleration = Vector3(0.0, 10.0, 0.0),
            magneticField =
                Vector3(
                    sin(10.0 * DEGREES_TO_RADIANS),
                    10.0,
                    -cos(10.0 * DEGREES_TO_RADIANS),
                ),
            expectedZenith = Vector3(1.0, 0.0, 0.0),
            expectedNorth = Vector3(0.0, 0.0, 1.0),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(0.0, 0.0, 1.0),
            expectedUpAlongPhone = Vector3(1.0, 0.0, 0.0),
            magneticDeclinationDeg = 10.0,
        )
    }

    @Test
    fun flatLat45Long0MagN180Degrees() {
        checkModelOrientation(
            location = LatLong(45.0, 0.0),
            acceleration = Vector3(0.0, 0.0, 10.0),
            magneticField = Vector3(0.0, -10.0, 0.0),
            expectedZenith = Vector3(1 / SQRT2, 0.0, 1 / SQRT2),
            expectedNorth = Vector3(-1 / SQRT2, 0.0, 1 / SQRT2),
            expectedEast = Vector3(0.0, 1.0, 0.0),
            expectedPointing = Vector3(-1 / SQRT2, 0.0, -1 / SQRT2),
            expectedUpAlongPhone = Vector3(-1 / SQRT2, 0.0, 1 / SQRT2),
            magneticDeclinationDeg = 180.0,
        )
    }

    // ---- View-direction modes and fusion edge cases -----------------------------------------

    @Test
    fun viewDirectionModesUseTheSameTransform() {
        val frame = SkyModel.localFrame(SPECIAL_TIME, LatLong(0.0, 0.0))
        // Phone standing up facing north.
        val orientation =
            orientationFromSensors(Vector3(0.0, 10.0, 0.0), Vector3(0.0, 10.0, -1.0))!!
        val rotate90 = SkyModel.pointing(frame, orientation, ViewDirectionMode.ROTATE90)
        assertVectorNear(rotate90.lineOfSight, Vector3(0.0, 0.0, 1.0), "rotate90 lineOfSight")
        // Screen-up along phone x = world magnetic east.
        assertVectorNear(rotate90.perpendicular, Vector3(0.0, 1.0, 0.0), "rotate90 perpendicular")
        val telescope = SkyModel.pointing(frame, orientation, ViewDirectionMode.TELESCOPE)
        // Sighting along phone y = up the phone = the zenith; screen-up out of the screen = south.
        assertVectorNear(telescope.lineOfSight, Vector3(1.0, 0.0, 0.0), "telescope lineOfSight")
        assertVectorNear(
            telescope.perpendicular,
            Vector3(0.0, 0.0, -1.0),
            "telescope perpendicular",
        )
    }

    @Test
    fun orientationFromSensorsIsOrthonormal() {
        val orientation =
            orientationFromSensors(Vector3(1.0, 2.0, 9.0), Vector3(-3.0, 20.0, -30.0))!!
        val east = Vector3(orientation.xx, orientation.xy, orientation.xz)
        val north = Vector3(orientation.yx, orientation.yy, orientation.yz)
        val up = Vector3(orientation.zx, orientation.zy, orientation.zz)
        assertThat(east.length).isWithin(TOL).of(1.0)
        assertThat(north.length).isWithin(TOL).of(1.0)
        assertThat(up.length).isWithin(TOL).of(1.0)
        assertThat(east dot north).isWithin(TOL).of(0.0)
        assertThat(north dot up).isWithin(TOL).of(0.0)
        assertThat(up dot east).isWithin(TOL).of(0.0)
        assertThat(orientation.determinant).isWithin(TOL).of(1.0)
    }

    @Test
    fun orientationFromSensorsRejectsDegenerateInput() {
        // v1 ignored near-zero sensor vectors; a field parallel to gravity has no north.
        assertThat(orientationFromSensors(Vector3.ZERO, Vector3(0.0, 1.0, 0.0))).isNull()
        assertThat(orientationFromSensors(Vector3(0.0, 0.0, 10.0), Vector3.ZERO)).isNull()
        assertThat(
            orientationFromSensors(Vector3(0.0, 0.0, 10.0), Vector3(0.0, 0.0, -50.0)),
        ).isNull()
    }

    private fun checkModelOrientation(
        location: LatLong,
        acceleration: Vector3,
        magneticField: Vector3,
        expectedZenith: Vector3,
        expectedNorth: Vector3,
        expectedEast: Vector3,
        expectedPointing: Vector3,
        expectedUpAlongPhone: Vector3,
        magneticDeclinationDeg: Double = 0.0,
    ) {
        val frame = SkyModel.localFrame(SPECIAL_TIME, location, magneticDeclinationDeg)
        assertVectorNear(frame.up, expectedZenith, "zenith")
        assertVectorNear(frame.trueNorth, expectedNorth, "north")
        assertVectorNear(frame.trueEast, expectedEast, "east")

        val orientation = orientationFromSensors(acceleration, magneticField)!!
        val pointing = SkyModel.pointing(frame, orientation)
        assertVectorNear(pointing.lineOfSight, expectedPointing, "lineOfSight")
        assertVectorNear(pointing.perpendicular, expectedUpAlongPhone, "perpendicular")
    }

    private fun assertVectorNear(
        actual: Vector3,
        expected: Vector3,
        label: String,
    ) {
        assertWithMessage("$label x").that(actual.x).isWithin(TOL).of(expected.x)
        assertWithMessage("$label y").that(actual.y).isWithin(TOL).of(expected.y)
        assertWithMessage("$label z").that(actual.z).isWithin(TOL).of(expected.z)
    }

    companion object {
        // At this time RA, Dec = (0, 0) is directly overhead at the equator on the Greenwich
        // meridian (v1's fake-clock instant: 12:07:24 March 20th 2009).
        private val SPECIAL_TIME = utc(2009, 3, 20, 12, 7, 24)
        private val SQRT2 = sqrt(2.0)
        private const val TOL = 1e-3
    }
}
