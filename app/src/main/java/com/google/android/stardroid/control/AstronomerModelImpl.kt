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

import com.google.android.stardroid.math.Matrix3x3.Companion.identity
import com.google.android.stardroid.control.AstronomerModel.Pointing
import com.google.android.stardroid.ApplicationConstants
import android.hardware.SensorManager
import android.util.Log
import com.google.android.stardroid.math.*
import com.google.android.stardroid.util.MiscUtil
import java.util.*
import kotlin.math.abs

/**
 * The model of the astronomer.
 *
 *
 * Stores all the data about where and when he is and where he's looking and
 * handles translations between three frames of reference:
 *
 *  1. Celestial - a frame fixed against the background stars with
 * x, y, z axes pointing to (RA = 90, DEC = 0), (RA = 0, DEC = 0), DEC = 90
 *  1. Phone - a frame fixed in the phone with x across the short side, y across
 * the long side, and z coming out of the phone screen.
 *  1. Local - a frame fixed in the astronomer's local position. x is due east
 * along the ground y is due north along the ground, and z points towards the
 * zenith.
 *
 *
 *
 * We calculate the local frame in phone coords, and in celestial coords and
 * calculate a transform between the two.
 * In the following, N, E, U correspond to the local
 * North, East and Up vectors (ie N, E along the ground, Up to the Zenith)
 *
 *
 * In Phone Space: axesPhone = [N, E, U]
 *
 *
 * In Celestial Space: axesSpace = [N, E, U]
 *
 *
 * We find T such that axesCelestial = T * axesPhone
 *
 *
 * Then, [viewDir, viewUp]_celestial = T * [viewDir, viewUp]_phone
 *
 *
 * where the latter vector is trivial to calculate.
 *
 *
 * Implementation note: this class isn't making defensive copies and
 * so is vulnerable to clients changing its internal state.
 *
 * @author John Taylor
 */
class AstronomerModelImpl(magneticDeclinationCalculator: MagneticDeclinationCalculator) :
    AstronomerModel {
    private var pointingInPhoneCoords = POINTING_DIR_IN_STANDARD_PHONE_COORDS
    private var screenUpInPhoneCoords = SCREEN_UP_STANDARD_IN_PHONE_COORDS
    private var magneticDeclinationCalculator: MagneticDeclinationCalculator? = null
    private var autoUpdatePointing = true
    private var fieldOfView = 45f // Degrees
    private var location = LatLong(0f, 0f)
    private var clock: Clock = RealClock()
    private var celestialCoordsLastUpdated: Long = -1

    /**
     * The pointing comprises a vector into the phone's screen expressed in
     * celestial coordinates combined with a perpendicular vector along the
     * phone's longer side.
     */
    private val pointing = Pointing()

    /** The sensor acceleration in the phone's coordinate system.  */
    private val acceleration = ApplicationConstants.INITIAL_DOWN.copy()
    private var upPhone = acceleration * -1f

    /** The sensor magnetic field in the phone's coordinate system.  */
    private val magneticField = ApplicationConstants.INITIAL_SOUTH.copy()
    private var useRotationVector = false
    private val rotationVector = floatArrayOf(1f, 0f, 0f, 0f)

    /** North along the ground in celestial coordinates.  */
    private var trueNorthCelestial = Vector3.unitX()

    /** Up in celestial coordinates.  */
    private var upCelestial = Vector3.unitY()

    /** East in celestial coordinates.  */
    private var trueEastCelestial = AXIS_OF_EARTHS_ROTATION

    /** [North, Up, East]^-1 in phone coordinates.  */
    private var axesPhoneInverseMatrix = identity

    /** [North, Up, East] in celestial coordinates.  */
    private var axesMagneticCelestialMatrix = identity
    override fun setViewDirectionMode(mode: AstronomerModel.ViewDirectionMode) {
        val (p, s) = when (mode) {
            AstronomerModel.ViewDirectionMode.STANDARD -> listOf(
                POINTING_DIR_IN_STANDARD_PHONE_COORDS,
                SCREEN_UP_STANDARD_IN_PHONE_COORDS
            )
            AstronomerModel.ViewDirectionMode.ROTATE90 -> listOf(
                POINTING_DIR_IN_STANDARD_PHONE_COORDS,
                SCREEN_UP_ROTATED_IN_PHONE_COORDS
            )
            AstronomerModel.ViewDirectionMode.TELESCOPE -> listOf(
                POINTING_DIR_FOR_TELESCOPES,
                SCREEN_UP_FOR_TELESCOPES
            )
        }
        pointingInPhoneCoords = p
        screenUpInPhoneCoords = s
    }

    override fun setAutoUpdatePointing(autoUpdatePointing: Boolean) {
        this.autoUpdatePointing = autoUpdatePointing
    }

    override fun getFieldOfView(): Float {
        return fieldOfView
    }

    override fun setFieldOfView(degrees: Float) {
        fieldOfView = degrees
    }

    override fun getMagneticCorrection(): Float {
        return magneticDeclinationCalculator!!.declination
    }

    override fun getTime(): Date {
        return Date(clock.timeInMillisSinceEpoch)
    }

    override fun getLocation(): LatLong {
        return location
    }

    override fun setLocation(location: LatLong) {
        this.location = location
        calculateLocalNorthAndUpInCelestialCoords(true)
    }

    override fun getPhoneUpDirection(): Vector3 {
        return upPhone
    }

    override fun setPhoneSensorValues(acceleration: Vector3, magneticField: Vector3) {
        if (magneticField.length2 < TOL || acceleration.length2 < TOL) {
            Log.w(TAG, "Invalid sensor values - ignoring")
            Log.w(TAG, "Mag: $magneticField")
            Log.w(TAG, "Accel: $acceleration")
            return
        }
        this.acceleration.assign(acceleration)
        this.magneticField.assign(magneticField)
        useRotationVector = false
    }

    override fun setPhoneSensorValues(rotationVector: FloatArray) {
        // TODO(jontayler): What checks do we need for this to be valid?
        // Note on some phones such as the Galaxy S4 this vector is the wrong size and needs to be
        // truncated to 4.
        System.arraycopy(
            rotationVector,
            0,
            this.rotationVector,
            0,
            rotationVector.size.coerceAtMost(4)
        )
        useRotationVector = true
    }

    override fun getNorth(): Vector3 {
        calculateLocalNorthAndUpInCelestialCoords(false)
        return trueNorthCelestial.copy()
    }

    override fun getSouth(): Vector3 {
        calculateLocalNorthAndUpInCelestialCoords(false)
        return -trueNorthCelestial
    }

    override fun getZenith(): Vector3 {
        calculateLocalNorthAndUpInCelestialCoords(false)
        return upCelestial.copy()
    }

    override fun getNadir(): Vector3 {
        calculateLocalNorthAndUpInCelestialCoords(false)
        return -upCelestial
    }

    override fun getEast(): Vector3 {
        calculateLocalNorthAndUpInCelestialCoords(false)
        return trueEastCelestial.copy()
    }

    override fun getWest(): Vector3 {
        calculateLocalNorthAndUpInCelestialCoords(false)
        return -trueEastCelestial
    }

    override fun setMagneticDeclinationCalculator(calculator: MagneticDeclinationCalculator) {
        magneticDeclinationCalculator = calculator
        calculateLocalNorthAndUpInCelestialCoords(true)
    }

    /**
     * Updates the astronomer's 'pointing', that is, the direction the phone is
     * facing in celestial coordinates and also the 'up' vector along the
     * screen (also in celestial coordinates).
     *
     *
     * This method requires that [.axesMagneticCelestialMatrix] and
     * [.axesPhoneInverseMatrix] are currently up to date.
     */
    private fun calculatePointing() {
        if (!autoUpdatePointing) {
            return
        }
        calculateLocalNorthAndUpInCelestialCoords(false)
        calculateLocalNorthAndUpInPhoneCoordsFromSensors()
        val transform = axesMagneticCelestialMatrix * axesPhoneInverseMatrix
        val viewInSpaceSpace =  transform * pointingInPhoneCoords
        val screenUpInSpaceSpace = transform * screenUpInPhoneCoords
        pointing.updateLineOfSight(viewInSpaceSpace)
        pointing.updatePerpendicular(screenUpInSpaceSpace)
    }

    /**
     * Calculates local North, East and Up vectors in terms of the celestial
     * coordinate frame.
     */
    private fun calculateLocalNorthAndUpInCelestialCoords(forceUpdate: Boolean) {
        val currentTime = clock.timeInMillisSinceEpoch
        if (!forceUpdate &&
            abs(currentTime - celestialCoordsLastUpdated) <
            MINIMUM_TIME_BETWEEN_CELESTIAL_COORD_UPDATES_MILLIS
        ) {
            return
        }
        celestialCoordsLastUpdated = currentTime
        updateMagneticCorrection()
        val up = calculateRADecOfZenith(time, location)
        upCelestial = getGeocentricCoords(up)
        val z = AXIS_OF_EARTHS_ROTATION
        val zDotu = upCelestial dot z
        trueNorthCelestial = z - upCelestial * zDotu
        trueNorthCelestial.normalize()
        trueEastCelestial = trueNorthCelestial * upCelestial

        // Apply magnetic correction.  Rather than correct the phone's axes for
        // the magnetic declination, it's more efficient to rotate the
        // celestial axes by the same amount in the opposite direction.
        val rotationMatrix = calculateRotationMatrix(
            magneticDeclinationCalculator!!.declination, upCelestial
        )
        val magneticNorthCelestial = rotationMatrix * trueNorthCelestial
        val magneticEastCelestial = magneticNorthCelestial * upCelestial
        axesMagneticCelestialMatrix = Matrix3x3(
            magneticNorthCelestial,
            upCelestial,
            magneticEastCelestial
        )
    }
    // TODO(jontayler): with the switch to using the rotation vector sensor this is rather
    // convoluted and doing too much work.  It can be greatly simplified when we rewrite the
    // rendering module.
    /**
     * Calculates local North and Up vectors in terms of the phone's coordinate
     * frame from the magnetic field and accelerometer sensors.
     */
    private fun calculateLocalNorthAndUpInPhoneCoordsFromSensors() {
        val magneticNorthPhone: Vector3
        val magneticEastPhone: Vector3
        if (useRotationVector) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            // The up and north vectors are the 2nd and 3rd rows of this matrix.
            magneticNorthPhone = Vector3(rotationMatrix[3], rotationMatrix[4], rotationMatrix[5])
            upPhone = Vector3(rotationMatrix[6], rotationMatrix[7], rotationMatrix[8])
            magneticEastPhone = Vector3(rotationMatrix[0], rotationMatrix[1], rotationMatrix[2])
        } else {
            // TODO(johntaylor): we can reduce the number of vector copies done in here.
            upPhone = acceleration.normalizedCopy()
            val magneticFieldToNorth = magneticField.normalizedCopy()
            // This is the vector to magnetic North *along the ground*.
            // (The "vector rejection")
            magneticNorthPhone =
                magneticFieldToNorth - upPhone * (magneticFieldToNorth dot upPhone)
            magneticNorthPhone.normalize()
            // East is the cross-product.
            magneticEastPhone = magneticNorthPhone * upPhone
        }
        // The matrix is orthogonal, so transpose it to find its inverse.
        // Easiest way to do that is to construct it from row vectors instead
        // of column vectors.
        axesPhoneInverseMatrix = Matrix3x3(
            magneticNorthPhone, upPhone, magneticEastPhone, false)
    }

    /**
     * Updates the angle between True North and Magnetic North.
     */
    private fun updateMagneticCorrection() {
        magneticDeclinationCalculator?.setLocationAndTime(location, timeMillis)
    }

    /**
     * Returns the user's pointing.  Note that clients should not usually modify this
     * object as it is not defensively copied.
     */
    override fun getPointing(): Pointing {
        calculatePointing()
        return pointing
    }

    override fun setPointing(lineOfSight: Vector3, perpendicular: Vector3) {
        pointing.updateLineOfSight(lineOfSight)
        pointing.updatePerpendicular(perpendicular)
    }

    override fun setClock(clock: Clock) {
        this.clock = clock
        calculateLocalNorthAndUpInCelestialCoords(true)
    }

    override fun getTimeMillis(): Long {
        return clock.timeInMillisSinceEpoch
    }

    companion object {
        private val TAG = MiscUtil.getTag(AstronomerModelImpl::class.java)
        private val POINTING_DIR_IN_STANDARD_PHONE_COORDS = -Vector3.unitZ()
        private val SCREEN_UP_STANDARD_IN_PHONE_COORDS = Vector3.unitY()
        // Some devices like glasses seem to fix the orientation 90 degrees to what we expect.
        private val SCREEN_UP_ROTATED_IN_PHONE_COORDS = Vector3.unitX()
        // For telescopes where you want the phone strapped to the tube so that you're
        // essentially sighting along the long edge of the phone
        private val POINTING_DIR_FOR_TELESCOPES = Vector3.unitY()
        private val SCREEN_UP_FOR_TELESCOPES = Vector3.unitZ()

        private val AXIS_OF_EARTHS_ROTATION = Vector3.unitZ()
        private const val MINIMUM_TIME_BETWEEN_CELESTIAL_COORD_UPDATES_MILLIS = 60000L
        private const val TOL = 0.01f
    }

    init {
        setMagneticDeclinationCalculator(magneticDeclinationCalculator)
    }
}