/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.space

import com.google.android.stardroid.R
import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.*
import com.google.android.stardroid.math.MathUtils.asin
import com.google.android.stardroid.math.MathUtils.atan2
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin
import java.util.*

/**
 * A likely temporary class to represent the Moon.
 */
class Moon : EarthOrbitingObject(SolarSystemBody.Moon) {
    override fun getRaDec(date: Date): RaDec {
        val (l, m, n) = geocentricDirectionCosines(date)
        val ra: Float = mod2pi(atan2(m, l)) * RADIANS_TO_DEGREES
        val dec: Float = asin(n) * RADIANS_TO_DEGREES
        return RaDec(ra, dec)
    }

    /**
     * Right ascension and declination of the moon as seen by an observer at [location],
     * correcting the geocentric position for diurnal parallax (up to ~1 degree - two lunar
     * diameters - depending on where on Earth the observer stands and how close the Moon is).
     * Same Astronomical Almanac (2008, p. D22) page as [getRaDec]; the horizontal-parallax
     * series (`pi`) puts the Moon at `1 / sin(pi)` Earth radii, and the observer sits one
     * Earth radius out at `(cos(lat) cos(lst), cos(lat) sin(lst), sin(lat))` in the same
     * equatorial frame, with local sidereal time from [meanSiderealTime].
     */
    fun getTopocentricRaDec(date: Date, location: LatLong): RaDec {
        val (l, m, n) = geocentricDirectionCosines(date)
        val t = ((julianDay(date) - 2451545.0f) / 36525.0f).toFloat()
        val distanceEarthRadii = 1.0f / sin(lunarHorizontalParallax(t) * DEGREES_TO_RADIANS)
        val lstRad = meanSiderealTime(date, location.longitude) * DEGREES_TO_RADIANS
        val latRad = location.latitude * DEGREES_TO_RADIANS
        val observerFromGeocenter = Vector3(
            cos(latRad) * cos(lstRad), cos(latRad) * sin(lstRad), sin(latRad)
        )
        val topocentric = Vector3(l, m, n) * distanceEarthRadii - observerFromGeocenter
        return RaDec.fromGeocentricCoords(topocentric)
    }

    /**
     * Calculate the geocentric right ascension and declination of the moon using
     * an approximation as described on page D22 of the 2008 Astronomical Almanac
     * All of the variables in this method use the same names as those described
     * in the text: lambda = Ecliptic longitude (degrees) beta = Ecliptic latitude
     * (degrees) pi = horizontal parallax (degrees) r = distance (Earth radii)
     *
     * NOTE: The text does not give a specific time period where the approximation
     * is valid, but it should be valid through at least 2009.
     *
     * Returns the equatorial direction cosines (l, m, n) as a [Vector3].
     */
    private fun geocentricDirectionCosines(date: Date): Vector3 {
        // First, calculate the number of Julian centuries from J2000.0.
        val t = ((julianDay(date) - 2451545.0f) / 36525.0f).toFloat()
        // Second, calculate the approximate geocentric orbital elements.
        val lambda = (218.32f + 481267.881f * t + (6.29f
                * sin((135.0f + 477198.87f * t) * DEGREES_TO_RADIANS)) - 1.27f
                * sin((259.3f - 413335.36f * t) * DEGREES_TO_RADIANS)) + (0.66f
                * sin((235.7f + 890534.22f * t) * DEGREES_TO_RADIANS)) + (0.21f
                * sin((269.9f + 954397.74f * t) * DEGREES_TO_RADIANS)) - (0.19f
                * sin((357.5f + 35999.05f * t) * DEGREES_TO_RADIANS)) - (0.11f
                * sin((186.5f + 966404.03f * t) * DEGREES_TO_RADIANS))
        val beta = (5.13f * sin((93.3f + 483202.02f * t) * DEGREES_TO_RADIANS) + 0.28f
                * sin((228.2f + 960400.89f * t) * DEGREES_TO_RADIANS)) - (0.28f
                * sin((318.3f + 6003.15f * t) * DEGREES_TO_RADIANS)) - (0.17f
                * sin((217.6f - 407332.21f * t) * DEGREES_TO_RADIANS))

        // Third, convert to equatorial direction cosines.
        val l = (cos(beta * DEGREES_TO_RADIANS)
                * cos(lambda * DEGREES_TO_RADIANS))
        val m = (0.9175f * cos(beta * DEGREES_TO_RADIANS)
                * sin(lambda * DEGREES_TO_RADIANS)) - 0.3978f * sin(beta * DEGREES_TO_RADIANS)
        val n = (0.3978f * cos(beta * DEGREES_TO_RADIANS)
                * sin(lambda * DEGREES_TO_RADIANS)) + 0.9175f * sin(beta * DEGREES_TO_RADIANS)
        return Vector3(l, m, n)
    }

    /** Lunar equatorial horizontal parallax in degrees, from the same Almanac page. */
    private fun lunarHorizontalParallax(t: Float): Float {
        return 0.9508f + 0.0518f * cos((135.0f + 477198.87f * t) * DEGREES_TO_RADIANS) +
                0.0095f * cos((259.3f - 413335.36f * t) * DEGREES_TO_RADIANS) +
                0.0078f * cos((235.7f + 890534.22f * t) * DEGREES_TO_RADIANS) +
                0.0028f * cos((269.9f + 954397.74f * t) * DEGREES_TO_RADIANS)
    }

    /**
     * The Moon's true angular radius, in radians. The Moon doesn't orbit the Sun, so it can't
     * use [SolarSystemObject.getTrueAngularRadius]'s heliocentric distance calculation; instead,
     * reuse the horizontal-parallax series already computed for [getTopocentricRaDec], since
     * `1 / sin(parallax)` is the Earth-Moon distance in Earth radii.
     */
    override fun getTrueAngularRadius(time: Date): Float {
        val t = ((julianDay(time) - 2451545.0f) / 36525.0f).toFloat()
        val distanceEarthRadii = 1.0f / sin(lunarHorizontalParallax(t) * DEGREES_TO_RADIANS)
        val distanceKm = distanceEarthRadii * SolarSystemBody.Earth.meanRadiusKm
        return asin((SolarSystemBody.Moon.meanRadiusKm / distanceKm).coerceIn(-1f, 1f))
    }

    /** Returns the resource id for the planet's image.  */
    override fun getImageResourceId(time: Date) = getLunarPhaseImageId(time)

    /**
     * Determine the Moon's phase and return the resource ID of the correct
     * image.
     */
    fun getLunarPhaseImageId(time: Date): Int {
        // First, calculate phase angle:
        val phase: Float = calculatePhaseAngle(time)
        // Log.d(TAG, "Lunar phase = $phase")

        // Next, figure out what resource id to return.
        if (phase < 22.5f) {
            // New moon.
            return R.drawable.moon0
        } else if (phase > 150.0f) {
            // Full moon.
            return R.drawable.moon4
        }

        // Either crescent, quarter, or gibbous. Need to see whether we are
        // waxing or waning. Calculate the phase angle one day in the future.
        // If phase is increasing, we are waxing. If not, we are waning.
        val tomorrow = Date(time.time + 24 * 3600 * 1000)
        val phase2: Float = calculatePhaseAngle(tomorrow)
        // Log.d(TAG, "Tomorrow's phase = $phase2")
        if (phase < 67.5f) {
            // Crescent
            return if (phase2 > phase) R.drawable.moon1 else R.drawable.moon7
        } else if (phase < 112.5f) {
            // Quarter
            return if (phase2 > phase) R.drawable.moon2 else R.drawable.moon6
        }

        // Gibbous
        return if (phase2 > phase) R.drawable.moon3 else R.drawable.moon5
    }

    override val bodySize = -0.83f

    // TODO(serafini): For now, return semi-reasonable values for the Sun and
    // Moon. We shouldn't call this method for those bodies, but we want to do
    // something sane if we do.
    override fun getMagnitude(time: Date) = -10.0f
}