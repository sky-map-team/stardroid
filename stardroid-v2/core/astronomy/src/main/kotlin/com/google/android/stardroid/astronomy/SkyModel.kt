/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.rotationMatrix
import kotlinx.datetime.Instant

/**
 * The observer's local reference directions expressed in celestial (geocentric equatorial)
 * coordinates: north and east along the ground, up toward the zenith.
 *
 * The magnetic pair is the true pair rotated about [up] by the local magnetic declination, so a
 * compass-derived phone orientation maps straight onto it (v1 rotated the celestial axes rather
 * than correcting each sensor reading — cheaper, same effect).
 */
data class LocalFrame(
    val trueNorth: Vector3,
    val up: Vector3,
    val trueEast: Vector3,
    val magneticNorth: Vector3,
    val magneticEast: Vector3,
) {
    /** `[magneticNorth, up, magneticEast]` as column vectors — v1's `axesMagneticCelestialMatrix`. */
    val axesMagneticCelestial: Matrix3
        get() = Matrix3.fromVectors(magneticNorth, up, magneticEast)
}

/**
 * Where the phone points on the celestial sphere: the direction into the screen ([lineOfSight])
 * and the direction along the screen's long axis ([perpendicular]) — together they fix the
 * camera's roll.
 */
data class Pointing(val lineOfSight: Vector3, val perpendicular: Vector3)

/**
 * Which phone axis is "into the sky" — v1 feature parity for devices strapped to telescope
 * tubes or with rotated displays.
 *
 * @property pointingPhone the look direction in phone coordinates.
 * @property screenUpPhone the screen-up direction in phone coordinates.
 */
enum class ViewDirectionMode(val pointingPhone: Vector3, val screenUpPhone: Vector3) {
    /** Looking out of the back of the screen, up along the phone's long side. */
    STANDARD(-Vector3.UNIT_Z, Vector3.UNIT_Y),

    /** Some devices (e.g. glasses) fix the display 90° from the sensor frame. */
    ROTATE90(-Vector3.UNIT_Z, Vector3.UNIT_X),

    /** Sighting along the phone's long edge, for phones strapped to a telescope tube. */
    TELESCOPE(Vector3.UNIT_Y, Vector3.UNIT_Z),
}

/**
 * v1's `AstronomerModelImpl` three-frame math (celestial / phone / local) as pure functions.
 *
 * The mutable model with its 60-second cache and sensor state is gone: callers hold a
 * [LocalFrame] recomputed on their own clock/location cadence and resolve a [Pointing] per
 * orientation sample. Orientation matrices use Android's rotation-matrix convention (see
 * [pointing]); the accelerometer+magnetometer fallback fusion lives in [orientationFromSensors].
 */
object SkyModel {
    private val AXIS_OF_EARTHS_ROTATION = Vector3.UNIT_Z

    /** The point directly overhead — RA from the local sidereal time, Dec from the latitude. */
    fun zenithRaDec(
        time: Instant,
        location: LatLong,
    ): RaDec = RaDec(meanSiderealTimeDeg(time, location.longitudeDeg), location.latitudeDeg)

    /**
     * The observer's local frame in celestial coordinates for this time and place.
     *
     * Port of v1's `calculateLocalNorthAndUpInCelestialCoords`: up is the zenith direction,
     * true north is Earth's axis with the vertical component removed, east completes the
     * right-handed set, and the magnetic pair is rotated by [magneticDeclinationDeg] about up.
     */
    fun localFrame(
        time: Instant,
        location: LatLong,
        magneticDeclinationDeg: Double = 0.0,
    ): LocalFrame {
        // Exactly at a geographic pole, up is collinear with Earth's axis, the north rejection
        // collapses to zero, and normalized() yields a degenerate zero frame. Clamp just shy of the
        // pole so an over-the-pole location still resolves to a usable (if arbitrary) north.
        val safeLocation =
            LatLong(location.latitudeDeg.coerceIn(-89.0, 89.0), location.longitudeDeg)
        val up = zenithRaDec(time, safeLocation).toGeocentricVector()
        val z = AXIS_OF_EARTHS_ROTATION
        val trueNorth = (z - up * (up dot z)).normalized()
        val trueEast = trueNorth cross up
        val magneticNorth = rotationMatrix(magneticDeclinationDeg, up) * trueNorth
        val magneticEast = magneticNorth cross up
        return LocalFrame(trueNorth, up, trueEast, magneticNorth, magneticEast)
    }

    /**
     * Resolves the phone's [orientation] to a celestial [Pointing].
     *
     * [orientation] is a phone→world rotation in Android's rotation-matrix convention (what
     * `SensorManager.getRotationMatrixFromVector` produces, or [orientationFromSensors] for the
     * legacy sensors): rows are world East, North, Up expressed in phone coordinates, with the
     * world's north being *magnetic* north. Port of v1's `calculatePointing`:
     * `T = axesCelestial × axesPhone⁻¹`, then the phone-frame view vectors map through `T`.
     */
    fun pointing(
        frame: LocalFrame,
        orientation: Matrix3,
        mode: ViewDirectionMode = ViewDirectionMode.STANDARD,
    ): Pointing {
        val magneticEastPhone = Vector3(orientation.xx, orientation.xy, orientation.xz)
        val magneticNorthPhone = Vector3(orientation.yx, orientation.yy, orientation.yz)
        val upPhone = Vector3(orientation.zx, orientation.zy, orientation.zz)
        // [North, Up, East] built from rows: the transpose (= inverse, orthogonal) of the
        // column-vector axes matrix.
        val axesPhoneInverse =
            Matrix3.fromVectors(
                magneticNorthPhone,
                upPhone,
                magneticEastPhone,
                columnVectors = false,
            )
        val transform = frame.axesMagneticCelestial * axesPhoneInverse
        return Pointing(
            lineOfSight = transform * mode.pointingPhone,
            perpendicular = transform * mode.screenUpPhone,
        )
    }
}

/**
 * Fuses raw accelerometer and magnetometer vectors (phone coordinates) into a phone→world
 * orientation matrix in the same convention [SkyModel.pointing] expects — the fallback for
 * devices without a rotation-vector sensor.
 *
 * Port of v1's `calculateLocalNorthAndUpInPhoneCoordsFromSensors` legacy branch: up is the
 * acceleration direction (opposite gravity), magnetic north along the ground is the field with
 * its vertical component removed (the "vector rejection"), east is their cross product.
 *
 * Returns `null` for sensor values too small or too parallel to fuse — v1 logged and ignored
 * such samples.
 */
fun orientationFromSensors(
    acceleration: Vector3,
    magneticField: Vector3,
): Matrix3? {
    if (acceleration.length2 < SENSOR_TOL || magneticField.length2 < SENSOR_TOL) return null
    val upPhone = acceleration.normalized()
    val fieldDir = magneticField.normalized()
    val magneticNorthPhone = (fieldDir - upPhone * (fieldDir dot upPhone)).normalized()
    // normalized() returns ZERO when the field is (near-)vertical and the rejection degenerates.
    if (magneticNorthPhone == Vector3.ZERO) return null
    val magneticEastPhone = magneticNorthPhone cross upPhone
    return Matrix3.fromVectors(
        magneticEastPhone,
        magneticNorthPhone,
        upPhone,
        columnVectors = false,
    )
}

private const val SENSOR_TOL = 0.01
