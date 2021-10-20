// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.control

import com.google.android.stardroid.math.Geometry
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.MathUtil.cos
import com.google.android.stardroid.math.MathUtil.sin
import com.google.android.stardroid.math.MathUtil.sqrt
import com.google.android.stardroid.math.Vector3
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import java.util.*

/**
 * Test of the observer's model when using magnetic variation.
 *
 * @author John Taylor
 */
// TODO(johntaylor): combine this with AstronomerModelWithMagneticVariationTest
// as there's currently too much code duplication.
class AstronomerModelWithMagneticVariationTest : TestCase() {
    private class MagneticDeclinationCalculation(private val angle: Float) :
        MagneticDeclinationCalculator {
        override fun getDeclination(): Float {
            return angle
        }

        override fun setLocationAndTime(location: LatLong, timeInMillis: Long) {
            // Do nothing
        }
    }

    fun testAssertVectorEquals() {
        val v1 = Vector3(0f, 0f, 1f)
        var v2 = Vector3(0f, 0f, 1f)
        assertVectorEquals(v1, v2, 0.0001f, 0.0001f)
        v2 = Vector3(0f, 0f, 1.1f)
        var failed = false
        try {
            assertVectorEquals(v1, v2, 0.0001f, 0.0001f)
        } catch (e: AssertionFailedError) {
            failed = true
        }
        assertTrue(failed)
        v2 = Vector3(0f, 1f, 0f)
        failed = false
        try {
            assertVectorEquals(v1, v2, 0.0001f, 0.0001f)
        } catch (e: AssertionFailedError) {
            failed = true
        }
        assertTrue(failed)
    }

    fun testFlatOnEquatorMag0Degrees() {
        val location = LatLong(0f, 0f)
        // The following vectors are in the phone's coordinate system.
        // Phone flat on back, top edge towards North
        val acceleration = Vector3(0f, 0f, -10f)
        // Magnetic field coming in from N
        val magneticField = Vector3(0f, -5f, -10f)

        // These vectors are in celestial coordinates.
        val expectedZenith = Vector3(1f, 0f, 0f)
        val expectedNadir = Vector3(-1f, 0f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkPointing(
            0.0f, location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNadir,
            expectedNorth
        )
    }

    fun testFlatOnEquatorMagN45DegreesW() {
        val location = LatLong(0f, 0f)
        // The following vectors are in the phone's coordinate system.
        // Phone flat on back, top edge towards North
        val acceleration = Vector3(0f, 0f, -10f)
        // Magnetic field coming in from NW
        val magneticField = Vector3(1f, -1f, -10f)

        // These vectors are in celestial coordinates.
        val expectedZenith = Vector3(1f, 0f, 0f)
        val expectedNadir = Vector3(-1f, 0f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkPointing(
            -45.0f, location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNadir,
            expectedNorth
        )
    }

    fun testStandingUpOnEquatorMagN10DegreesEast() {
        val location = LatLong(0f, 0f)
        val acceleration = Vector3(0f, -10f, 0f)
        val magneticField = Vector3(
            -sin(radians(10f)),
            10f,
            cos(radians(10f))
        )
        val expectedZenith = Vector3(1f, 0f, 0f)
        val expectedNadir = Vector3(-1f, 0f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkPointing(
            10f, location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNorth,
            expectedZenith
        )
    }

    private fun checkPointing(
        magDeclination: Float,
        location: LatLong,
        acceleration: Vector3,
        magneticField: Vector3,
        expectedZenith: Vector3,
        expectedNadir: Vector3,
        expectedNorth: Vector3,
        expectedEast: Vector3,
        expectedSouth: Vector3,
        expectedWest: Vector3,
        expectedPointing: Vector3,
        expectedUpAlongPhone: Vector3
    ) {
        val astronomer: AstronomerModel = AstronomerModelImpl(
            MagneticDeclinationCalculation(magDeclination)
        )
        astronomer.location = location
        val fakeClock =
            Clock { // This date is special as RA, DEC = (0, 0) is directly overhead at the
                // equator on the Greenwich meridian.
                // 12:07 March 20th 2009
                val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
                calendar[2009, 2, 20, 12, 7] = 24
                calendar.timeInMillis
            }
        astronomer.setClock(fakeClock)
        astronomer.setPhoneSensorValues(acceleration, magneticField)
        val pointing = astronomer.pointing.lineOfSight
        val upAlongPhone = astronomer.pointing.perpendicular
        val north = astronomer.north
        val east = astronomer.east
        val south = astronomer.south
        val west = astronomer.west
        val zenith = astronomer.zenith
        val nadir = astronomer.nadir
        assertVectorEquals(expectedZenith, zenith, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedNadir, nadir, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedNorth, north, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedEast, east, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedSouth, south, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedWest, west, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedPointing, pointing, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedUpAlongPhone, upAlongPhone, TOL_LENGTH, TOL_ANGLE)
    }

    fun testFlatLat45Long0MagN180Degrees() {
        val location = LatLong(45f, 0f)
        val acceleration = Vector3(0f, 0f, -10f)
        val magneticField = Vector3(0f, 10f, 0f)
        val expectedZenith = Vector3(1 / SQRT2, 0f, 1 / SQRT2)
        val expectedNadir = Vector3(-1 / SQRT2, 0f, -1 / SQRT2)
        val expectedNorth = Vector3(-1 / SQRT2, 0f, 1 / SQRT2)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(1 / SQRT2, 0f, -1 / SQRT2)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkPointing(
            180f, location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNadir,
            expectedNorth
        )
    }

    companion object {
        private const val TOL_ANGLE = 1e-3.toFloat()
        private const val TOL_LENGTH = 1e-3.toFloat()
        private val SQRT2 = sqrt(2f)
        private fun assertVectorEquals(
            v1: Vector3, v2: Vector3, tol_angle: Float,
            tol_length: Float
        ) {
            val normv1 = v1.length
            val normv2 = v2.length
            assertEquals("Vectors of different lengths", normv1, normv2, tol_length)
            val cosineSim =         // We might want to optimize this implementation at some point.
                v1.cosineSimilarity(v2)
            val cosTol = cos(tol_angle)
            assertTrue("Vectors in different directions", cosineSim >= cosTol)
            //TODO look at iteration in Julian day
        }

        /**
         * Convert from degrees to radians.
         */
        private fun radians(degrees: Float): Float {
            return degrees * Geometry.DEGREES_TO_RADIANS
        }
    }
}