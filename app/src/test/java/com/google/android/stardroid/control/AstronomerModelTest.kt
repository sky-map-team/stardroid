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

import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sqrt
import com.google.android.stardroid.math.Vector3
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import java.util.*

/**
 * Test of the [AstronomerModelImpl] class.
 *
 * @author John Taylor
 */
class AstronomerModelTest : TestCase() {
    private var astronomer: AstronomerModel? = null
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        // For now only test a model with no magnetic correction.
        astronomer = AstronomerModelImpl(ZeroMagneticDeclinationCalculator())
    }

    /**
     * Checks that our assertion method works as intended.
     */
    fun testAssertVectorEquals_sameVector() {
        val v1 = Vector3.unitZ()
        val v2 = Vector3.unitZ()
        assertVectorEquals(v1, v2, 0.0001f, 0.0001f)
    }

    /**
     * Checks that our assertion method works as intended.
     */
    fun testAssertVectorEquals_differentLengths() {
        val v1 = Vector3(0f, 0f, 1.0f)
        val v2 = Vector3(0f, 0f, 1.1f)
        try {
            assertVectorEquals(v1, v2, 0.0001f, 0.0001f)
            fail("Vectors should have been found to have different lengths.")
        } catch (e: AssertionFailedError) {
            // Expected.
        }
    }

    /**
     * Checks that our assertion method works as intended.
     */
    fun testAssertVectorEquals_differentDirections() {
        val v1 = Vector3.unitZ()
        val v2 = Vector3.unitY()
        try {
            assertVectorEquals(v1, v2, 0.0001f, 0.0001f)
            fail("Vectors should have been found to point in different directions.")
        } catch (e: AssertionFailedError) {
            // Expected.
        }
    }

    /**
     * The phone is flat, long side pointing North at lat,long = 0, 90.
     */
    fun testSetPhoneSensorValues_phoneFlatAtLat0Long90() {
        val location = LatLong(0f, 90f)
        // Phone flat on back, top edge towards North
        // The following are in the phone's coordinate system.
        val acceleration = Vector3(0f, 0f, -10f)
        val magneticField = Vector3(0f, -1f, 10f)
        // The following are in the celestial coordinate system.
        val expectedZenith = Vector3(0f, 1f, 0f)
        val expectedNadir = Vector3(0f, -1f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(-1f, 0f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(1f, 0f, 0f)
        checkModelOrientation(
            location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNadir,
            expectedNorth
        )
    }

    /**
     * As previous test, but at lat, long = (45, 0)
     */
    fun testSetPhoneSensorValues_phoneFlatAtLat45Long0() {
        val location = LatLong(45f, 0f)
        val acceleration = Vector3(0f, 0f, -10f)
        val magneticField = Vector3(0f, -10f, 0f)
        val expectedZenith = Vector3(1 / SQRT2, 0f, 1 / SQRT2)
        val expectedNadir = Vector3(-1 / SQRT2, 0f, -1 / SQRT2)
        val expectedNorth = Vector3(-1 / SQRT2, 0f, 1 / SQRT2)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(1 / SQRT2, 0f, -1 / SQRT2)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkModelOrientation(
            location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNadir,
            expectedNorth
        )
    }

    /**
     * As previous test, but at lat, long = (0, 0)
     */
    fun testSetPhoneSensorValues_phoneFlatOnEquatorAtMeridian() {
        val location = LatLong(0f, 0f)
        // Phone flat on back, top edge towards North
        val acceleration = Vector3(0f, 0f, -10f)
        val magneticField = Vector3(0f, -1f, 10f)
        val expectedZenith = Vector3(1f, 0f, 0f)
        val expectedNadir = Vector3(-1f, 0f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkModelOrientation(
            location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNadir,
            expectedNorth
        )
    }

    /**
     * As previous test, but with the phone vertical, but in landscape mode
     * and pointing east.
     */
    fun testSetPhoneSensorValues_phoneLandscapeFacingEastOnEquatorAtMeridian() {
        val location = LatLong(0f, 0f)
        val acceleration = Vector3(10f, 0f, 0f)
        val magneticField = Vector3(-10f, 1f, 0f)
        val expectedZenith = Vector3(1f, 0f, 0f)
        val expectedNadir = Vector3(-1f, 0f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkModelOrientation(
            location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedEast,
            expectedSouth
        )
    }

    /**
     * As previous test, but in portrait mode facing north.
     */
    fun testSetPhoneSensorValues_phoneStandingUpFacingNorthOnEquatorAtMeridian() {
        val location = LatLong(0f, 0f)
        val acceleration = Vector3(0f, -10f, 0f)
        val magneticField = Vector3(0f, 10f, 1f)
        val expectedZenith = Vector3(1f, 0f, 0f)
        val expectedNadir = Vector3(-1f, 0f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkModelOrientation(
            location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedNorth,
            expectedZenith
        )
    }

    private fun checkModelOrientation(
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
        astronomer!!.location = location
        val fakeClock =
            Clock { // This date is special as RA, DEC = (0, 0) is directly overhead at the
                // equator on the Greenwich meridian.
                // 12:07 March 20th 2009
                val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
                calendar[2009, 2, 20, 12, 7] = 24
                calendar.timeInMillis
            }
        astronomer!!.setClock(fakeClock)
        astronomer!!.setPhoneSensorValues(acceleration, magneticField)
        assertVectorEquals(expectedZenith, astronomer!!.zenith, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedNadir, astronomer!!.nadir, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedNorth, astronomer!!.north, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedEast, astronomer!!.east, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedSouth, astronomer!!.south, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(expectedWest, astronomer!!.west, TOL_LENGTH, TOL_ANGLE)
        assertVectorEquals(
            expectedPointing, astronomer!!.pointing.lineOfSight,
            TOL_LENGTH, TOL_ANGLE
        )
        assertVectorEquals(
            expectedUpAlongPhone, astronomer!!.pointing.perpendicular,
            TOL_LENGTH, TOL_ANGLE
        )
    }

    companion object {
        private val SQRT2 = sqrt(2f)
        private const val TOL_ANGLE = 1e-3f
        private const val TOL_LENGTH = 1e-3f
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
        }
    }
}