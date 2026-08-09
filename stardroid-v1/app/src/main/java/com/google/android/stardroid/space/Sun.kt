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
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.heliocentricCoordinatesFromOrbitalElements
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