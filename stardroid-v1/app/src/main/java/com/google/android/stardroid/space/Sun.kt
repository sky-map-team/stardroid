/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.heliocentricCoordinatesFromOrbitalElements
import com.google.android.stardroid.math.julianCenturiesTerrestrial
import java.util.*

// Kilometers per astronomical unit.
private const val KM_PER_AU = 149597870.7f

/**
 * The Sun is special as it's at the center of the solar system.
 *
 * It's a sort of trivial sun-orbiting object.
 */
class Sun : SunOrbitingObject(SolarSystemBody.Sun) {
    override val bodySize = -0.83f

    /**
     * Geocentric apparent right ascension and declination of the Sun, from Meeus,
     * *Astronomical Algorithms* (2nd ed., ch. 25, "lower accuracy" method), which is good to about
     * 0.01 degrees. This replaces the inherited [SunOrbitingObject.getRaDec], whose position from
     * the Earth's truncated Keplerian elements is ~0.4 degrees off - enough to throw solar-eclipse
     * timing (which depends on the Sun-Moon separation) out by half an hour. The result carries
     * nutation and aberration, so it is the apparent equatorial position of date.
     */
    override fun getRaDec(date: Date): RaDec {
        val t = julianCenturiesTerrestrial(date)
        // Geometric mean longitude and mean anomaly (degrees).
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        // Equation of the centre.
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m) +
                (0.019993 - 0.000101 * t) * Math.sin(2 * m) +
                0.000289 * Math.sin(3 * m)
        val trueLong = l0 + c
        // Apparent longitude: correct for nutation and aberration.
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val lambda = Math.toRadians(trueLong - 0.00569 - 0.00478 * Math.sin(omega))
        val epsilon = Math.toRadians(23.439291 - 0.0130042 * t + 0.00256 * Math.cos(omega))
        val ra = Math.toDegrees(
            Math.atan2(Math.cos(epsilon) * Math.sin(lambda), Math.cos(lambda))
        )
        val dec = Math.toDegrees(Math.asin(Math.sin(epsilon) * Math.sin(lambda)))
        return RaDec(((ra + 360.0) % 360.0).toFloat(), dec.toFloat())
    }

    override fun getMyHeliocentricCoordinates(date: Date) =
        Vector3(0.0f, 0.0f, 0.0f)

    // TODO(serafini): For now, return semi-reasonable values for the Sun and
    // Moon. We shouldn't call this method for those bodies, but we want to do
    // something sane if we do.
    override fun getMagnitude(time: Date) = -27.0f

    // The Sun has no orbital elements of its own (it's the heliocentric origin), so it can't use
    // the base SolarSystemObject calculation; use Earth's distance from the Sun instead.
    override fun getTrueAngularRadius(time: Date): Float {
        val earthCoords =
            heliocentricCoordinatesFromOrbitalElements(SolarSystemBody.Earth.getOrbitalElements(time))
        val earthDistanceKm = earthCoords.length * KM_PER_AU
        return MathUtils.asin((SolarSystemBody.Sun.meanRadiusKm / earthDistanceKm).coerceIn(-1f, 1f))
    }
}