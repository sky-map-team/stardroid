/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.withSign

/**
 * A right ascension / declination pair, in degrees. Centered on Earth and fixed in space, these
 * are analogous to longitude/latitude on the celestial sphere.
 *
 * Geocentric unit vectors and `RaDec` convert freely: the z axis is dec +90°, and the x axis is
 * ra 0°/dec 0°.
 */
data class RaDec(val raDeg: Double, val decDeg: Double) {
    /** This direction as a unit vector in geocentric coordinates. */
    fun toGeocentricVector(): Vector3 {
        val ra = raDeg * DEGREES_TO_RADIANS
        val dec = decDeg * DEGREES_TO_RADIANS
        return Vector3(
            cos(ra) * cos(dec),
            sin(ra) * cos(dec),
            sin(dec),
        )
    }

    companion object {
        fun raDegreesFromHms(
            h: Double,
            m: Double,
            s: Double,
        ): Double = HOURS_TO_DEGREES * (h + m / 60.0 + s / 3600.0)

        // The sign lives in the degrees field (incl. -0.0, e.g. -0° 30'); minutes and seconds are
        // unsigned magnitudes. withSign copies d's sign bit. v1 added them unsigned — wrong for
        // southern declinations.
        fun decDegreesFromDms(
            d: Double,
            m: Double,
            s: Double,
        ): Double = (abs(d) + m / 60.0 + s / 3600.0).withSign(d)

        fun fromHoursMinutesSeconds(
            raHours: Double,
            raMinutes: Double,
            raSeconds: Double,
            decDegrees: Double,
            decMinutes: Double,
            decSeconds: Double,
        ) = RaDec(
            raDegreesFromHms(raHours, raMinutes, raSeconds),
            decDegreesFromDms(decDegrees, decMinutes, decSeconds),
        )

        /** Recovers ra/dec from a vector in geocentric coordinates (need not be a unit vector). */
        fun fromGeocentricVector(v: Vector3): RaDec {
            val ra = normalizeDegrees(atan2(v.y, v.x) * RADIANS_TO_DEGREES)
            val dec = atan2(v.z, kotlin.math.sqrt(v.x * v.x + v.y * v.y)) * RADIANS_TO_DEGREES
            return RaDec(ra, dec)
        }
    }
}
