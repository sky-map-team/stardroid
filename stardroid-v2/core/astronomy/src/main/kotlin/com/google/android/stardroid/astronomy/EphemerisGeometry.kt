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
import com.google.android.stardroid.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

/** Obliquity of the ecliptic at J2000, in radians, with its cosine and sine precomputed once. */
private const val OBLIQUITY_RAD = 23.439281 * DEGREES_TO_RADIANS
private val COS_OBLIQUITY = cos(OBLIQUITY_RAD)
private val SIN_OBLIQUITY = sin(OBLIQUITY_RAD)

/**
 * Heliocentric ecliptic rectangular coordinates (AU) for an orbit: centred on the Sun, with the
 * z axis normal to Earth's orbital plane. Ported from v1's
 * `heliocentricCoordinatesFromOrbitalElements`, which lived in `:core:math` but depends on
 * astronomy types — it belongs here.
 */
internal fun heliocentricCoordinates(elem: OrbitalElements): Vector3 {
    val anomaly = elem.anomaly
    val ecc = elem.eccentricity
    val radius = elem.distanceAu * (1 - ecc * ecc) / (1 + ecc * cos(anomaly))

    val asc = elem.ascendingNodeRad
    val inc = elem.inclinationRad
    val argument = anomaly + elem.perihelionRad - asc
    return Vector3(
        radius * (cos(asc) * cos(argument) - sin(asc) * sin(argument) * cos(inc)),
        radius * (sin(asc) * cos(argument) + cos(asc) * sin(argument) * cos(inc)),
        radius * (sin(argument) * sin(inc)),
    )
}

/**
 * Rotates a vector from the ecliptic (Earth orbital) plane into Earth's equatorial plane by the
 * obliquity of the ecliptic.
 */
internal fun toEquatorialCoordinates(eclipticPlane: Vector3): Vector3 =
    Vector3(
        eclipticPlane.x,
        eclipticPlane.y * COS_OBLIQUITY - eclipticPlane.z * SIN_OBLIQUITY,
        eclipticPlane.y * SIN_OBLIQUITY + eclipticPlane.z * COS_OBLIQUITY,
    )
