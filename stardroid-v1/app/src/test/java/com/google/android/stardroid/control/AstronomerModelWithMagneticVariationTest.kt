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

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin
import com.google.android.stardroid.math.MathUtils.sqrt
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.Vector3Subject
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.junit.Test
import java.util.*

/**
 * Test of the observer's model when using magnetic variation.
 *
 * @author John Taylor
 */
// TODO(johntaylor): combine this with AstronomerModelWithMagneticVariationTest
// as there's currently too much code duplication.
class AstronomerModelWithMagneticVariationTest {
    private class MagneticDeclinationCalculation(private val angle: Float) :
        MagneticDeclinationCalculator {
        override fun getDeclination(): Float {
            return angle
        }

        override fun setLocationAndTime(location: LatLong, timeInMillis: Long) {
            // Do nothing
        }
    }

    @Test
    fun testFlatOnEquatorMag0Degrees() {
        val location = LatLong(0f, 0f)
        // The following vectors are in the phone's coordinate system.
        // Phone flat on back, top edge towards North
        val acceleration = Vector3(0f, 0f, 10f)
        // Magnetic field coming in from N
        val magneticField = Vector3(0f, 5f, -10f)

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

    @Test
    fun testFlatOnEquatorMagN45DegreesW() {
        val location = LatLong(0f, 0f)
        // The following vectors are in the phone's coordinate system.
        // Phone flat on back, top edge towards North
        val acceleration = Vector3(0f, 0f, 10f)
        // Magnetic field coming in from NW
        val magneticField = Vector3(-1f, 1f, -10f)

        // These vectors are in celestial coordinates.
        val expectedZenith = Vector3(1f, 0f, 0f)
        val expectedNadir = Vector3(-1f, 0f, 0f)
        val expectedNorth = Vector3(0f, 0f, 1f)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(0f, 0f, -1f)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkPointing(
            -45.0f, location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing = expectedNadir,
            expectedUpAlongPhone = expectedNorth
        )
    }

    @Test
    fun testStandingUpOnEquatorMagN10DegreesEast() {
        val location = LatLong(0f, 0f)
        val acceleration = Vector3(0f, 10f, 0f)
        val magneticField = Vector3(
            sin(10f * DEGREES_TO_RADIANS),
            10f,
            -cos(10f * DEGREES_TO_RADIANS)
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

    @Test
    fun testFlatLat45Long0MagN180Degrees() {
        val location = LatLong(45f, 0f)
        val acceleration = Vector3(0f, 0f, 10f)
        val magneticField = Vector3(0f, -10f, 0f)
        val expectedZenith = Vector3(1 / SQRT2, 0f, 1 / SQRT2)
        val expectedNadir = Vector3(-1 / SQRT2, 0f, -1 / SQRT2)
        val expectedNorth = Vector3(-1 / SQRT2, 0f, 1 / SQRT2)
        val expectedEast = Vector3(0f, 1f, 0f)
        val expectedSouth = Vector3(1 / SQRT2, 0f, -1 / SQRT2)
        val expectedWest = Vector3(0f, -1f, 0f)
        checkPointing(
            180f, location, acceleration, magneticField, expectedZenith, expectedNadir,
            expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing = expectedNadir,
            expectedUpAlongPhone = expectedNorth
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

        Vector3Subject.assertThat(astronomer.zenith).isWithin(TOL).of(expectedZenith)
        Vector3Subject.assertThat(astronomer.nadir).isWithin(TOL).of(expectedNadir)
        Vector3Subject.assertThat(astronomer.north).isWithin(TOL).of(expectedNorth)
        Vector3Subject.assertThat(astronomer.east).isWithin(TOL).of(expectedEast)
        Vector3Subject.assertThat(astronomer.south).isWithin(TOL).of(expectedSouth)
        Vector3Subject.assertThat(astronomer.west).isWithin(TOL).of(expectedWest)
        Vector3Subject.assertThat(astronomer.pointing.lineOfSight).isWithin(TOL).of(expectedPointing)
        Vector3Subject.assertThat(astronomer.pointing.perpendicular).isWithin(TOL).of(expectedUpAlongPhone)
    }

    companion object {
        private const val TOL = 1e-3.toFloat()
        private val SQRT2 = sqrt(2f)
    }
}