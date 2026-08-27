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
     *
     * The geocentric position and Earth-Moon distance both come from [geocentricEclipticPosition]
     * (Meeus, *Astronomical Algorithms*, 2nd ed., ch. 47); the observer sits one Earth radius out
     * at `(cos(lat) cos(lst), cos(lat) sin(lst), sin(lat))` in the same equatorial frame, with
     * local sidereal time from [meanSiderealTime].
     */
    fun getTopocentricRaDec(date: Date, location: LatLong): RaDec {
        val position = geocentricEclipticPosition(date)
        val geocentric = eclipticToEquatorialCosines(position)
        val distanceEarthRadii = (position.distanceKm / EARTH_EQUATORIAL_RADIUS_KM).toFloat()
        val lstRad = meanSiderealTime(date, location.longitude) * DEGREES_TO_RADIANS
        val latRad = location.latitude * DEGREES_TO_RADIANS
        val observerFromGeocenter = Vector3(
            cos(latRad) * cos(lstRad), cos(latRad) * sin(lstRad), sin(latRad)
        )
        val topocentric = geocentric * distanceEarthRadii - observerFromGeocenter
        return RaDec.fromGeocentricCoords(topocentric)
    }

    /**
     * The geocentric equatorial direction cosines (l, m, n) as a [Vector3], derived from the
     * apparent ecliptic longitude/latitude of [geocentricEclipticPosition].
     */
    private fun geocentricDirectionCosines(date: Date): Vector3 =
        eclipticToEquatorialCosines(geocentricEclipticPosition(date))

    /**
     * Geocentric apparent ecliptic longitude, latitude and Earth-Moon distance of the Moon,
     * computed from the ELP-2000/82 truncated series published in Meeus,
     * *Astronomical Algorithms* (2nd ed., 1998), ch. 47. Retaining the full 60-term longitude,
     * 60-term latitude and 60-term distance tables gives roughly 10" accuracy in longitude and
     * 4" in latitude - about two orders of magnitude better than the previous six-term Almanac
     * approximation, which is what eclipse timing (extremely sensitive to lunar position) needs.
     *
     * The time argument is Terrestrial Time (see [julianCenturiesTerrestrial]); longitude carries
     * the leading nutation term so it is the *apparent* longitude of date.
     */
    private fun geocentricEclipticPosition(date: Date): EclipticPosition {
        val t = julianCenturiesTerrestrial(date)
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        // Fundamental arguments in degrees (Meeus 47.1-47.5).
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 -
                t4 / 65194000.0
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 -
                t4 / 113065000.0
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
        val mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 -
                t4 / 14712000.0
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 +
                t4 / 863310000.0
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t
        // Eccentricity correction for terms involving the Sun's anomaly M (Meeus 47.6).
        val e = 1.0 - 0.002516 * t - 0.0000074 * t2

        var sumL = 0.0 // 1e-6 degrees
        var sumR = 0.0 // 1e-3 km
        for (row in TABLE_47A) {
            val arg = row[0] * d + row[1] * m + row[2] * mp + row[3] * f
            val eFactor = when (Math.abs(row[1])) {
                1 -> e
                2 -> e * e
                else -> 1.0
            }
            sumL += row[4] * eFactor * Math.sin(Math.toRadians(arg))
            sumR += row[5] * eFactor * Math.cos(Math.toRadians(arg))
        }
        var sumB = 0.0 // 1e-6 degrees
        for (row in TABLE_47B) {
            val arg = row[0] * d + row[1] * m + row[2] * mp + row[3] * f
            val eFactor = when (Math.abs(row[1])) {
                1 -> e
                2 -> e * e
                else -> 1.0
            }
            sumB += row[4] * eFactor * Math.sin(Math.toRadians(arg))
        }

        // Additive terms from Venus, Jupiter and the flattening of the Earth (Meeus, p. 342).
        sumL += 3958.0 * Math.sin(Math.toRadians(a1)) +
                1962.0 * Math.sin(Math.toRadians(lp - f)) +
                318.0 * Math.sin(Math.toRadians(a2))
        sumB += -2235.0 * Math.sin(Math.toRadians(lp)) +
                382.0 * Math.sin(Math.toRadians(a3)) +
                175.0 * Math.sin(Math.toRadians(a1 - f)) +
                175.0 * Math.sin(Math.toRadians(a1 + f)) +
                127.0 * Math.sin(Math.toRadians(lp - mp)) -
                115.0 * Math.sin(Math.toRadians(lp + mp))

        var lambda = lp + sumL / 1_000_000.0 // mean longitude of date, degrees
        val beta = sumB / 1_000_000.0        // ecliptic latitude, degrees
        val distanceKm = 385000.56 + sumR / 1000.0

        // Leading nutation terms (Meeus ch. 22) to turn mean longitude into apparent longitude
        // and to build the true obliquity used for the equatorial rotation.
        val omega = 125.04452 - 1934.136261 * t
        val lSun = 280.4665 + 36000.7698 * t
        val lMoon = 218.3165 + 481267.8813 * t
        val dPsiArcsec = -17.20 * Math.sin(Math.toRadians(omega)) -
                1.32 * Math.sin(Math.toRadians(2 * lSun)) -
                0.23 * Math.sin(Math.toRadians(2 * lMoon)) +
                0.21 * Math.sin(Math.toRadians(2 * omega))
        val dEpsilonArcsec = 9.20 * Math.cos(Math.toRadians(omega)) +
                0.57 * Math.cos(Math.toRadians(2 * lSun)) +
                0.10 * Math.cos(Math.toRadians(2 * lMoon)) -
                0.09 * Math.cos(Math.toRadians(2 * omega))
        lambda += dPsiArcsec / 3600.0
        val epsilon0 = 23.4392911 - 0.0130042 * t - 1.64e-7 * t2 + 5.04e-7 * t3
        val obliquityRad = Math.toRadians(epsilon0 + dEpsilonArcsec / 3600.0)

        return EclipticPosition(lambda, beta, distanceKm, obliquityRad)
    }

    /**
     * Rotates the apparent ecliptic coordinates in [position] into equatorial direction cosines
     * (Meeus 13.3-13.4), returned as a unit [Vector3] in the same frame the rest of the app uses.
     */
    private fun eclipticToEquatorialCosines(position: EclipticPosition): Vector3 {
        val lambdaRad = Math.toRadians(position.lambda)
        val betaRad = Math.toRadians(position.beta)
        val cosEps = Math.cos(position.obliquityRad)
        val sinEps = Math.sin(position.obliquityRad)
        val cosBeta = Math.cos(betaRad)
        val l = cosBeta * Math.cos(lambdaRad)
        val m = cosEps * cosBeta * Math.sin(lambdaRad) - sinEps * Math.sin(betaRad)
        val n = sinEps * cosBeta * Math.sin(lambdaRad) + cosEps * Math.sin(betaRad)
        return Vector3(l.toFloat(), m.toFloat(), n.toFloat())
    }

    /**
     * The Moon's true angular radius, in radians, from the Earth-Moon distance of
     * [geocentricEclipticPosition].
     */
    override fun getTrueAngularRadius(time: Date): Float {
        val distanceKm = geocentricEclipticPosition(time).distanceKm
        val ratio = (SolarSystemBody.Moon.meanRadiusKm / distanceKm).toFloat().coerceIn(-1f, 1f)
        return asin(ratio)
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

    /** Apparent ecliptic longitude/latitude (degrees), Earth-Moon distance (km), obliquity (rad). */
    private data class EclipticPosition(
        val lambda: Double,
        val beta: Double,
        val distanceKm: Double,
        val obliquityRad: Double
    )

    private companion object {
        /** Mean equatorial radius of the Earth in km, matching the horizontal-parallax convention. */
        const val EARTH_EQUATORIAL_RADIUS_KM = 6378.14

        /**
         * Meeus table 47.A: multiples of (D, M, M', F), then the longitude coefficient
         * (1e-6 degrees) and the distance coefficient (1e-3 km).
         */
        val TABLE_47A = arrayOf(
            intArrayOf(0, 0, 1, 0, 6288774, -20905355),
            intArrayOf(2, 0, -1, 0, 1274027, -3699111),
            intArrayOf(2, 0, 0, 0, 658314, -2955968),
            intArrayOf(0, 0, 2, 0, 213618, -569925),
            intArrayOf(0, 1, 0, 0, -185116, 48888),
            intArrayOf(0, 0, 0, 2, -114332, -3149),
            intArrayOf(2, 0, -2, 0, 58793, 246158),
            intArrayOf(2, -1, -1, 0, 57066, -152138),
            intArrayOf(2, 0, 1, 0, 53322, -170733),
            intArrayOf(2, -1, 0, 0, 45758, -204586),
            intArrayOf(0, 1, -1, 0, -40923, -129620),
            intArrayOf(1, 0, 0, 0, -34720, 108743),
            intArrayOf(0, 1, 1, 0, -30383, 104755),
            intArrayOf(2, 0, 0, -2, 15327, 10321),
            intArrayOf(0, 0, 1, 2, -12528, 0),
            intArrayOf(0, 0, 1, -2, 10980, 79661),
            intArrayOf(4, 0, -1, 0, 10675, -34782),
            intArrayOf(0, 0, 3, 0, 10034, -23210),
            intArrayOf(4, 0, -2, 0, 8548, -21636),
            intArrayOf(2, 1, -1, 0, -7888, 24208),
            intArrayOf(2, 1, 0, 0, -6766, 30824),
            intArrayOf(1, 0, -1, 0, -5163, -8379),
            intArrayOf(1, 1, 0, 0, 4987, -16675),
            intArrayOf(2, -1, 1, 0, 4036, -12831),
            intArrayOf(2, 0, 2, 0, 3994, -10445),
            intArrayOf(4, 0, 0, 0, 3861, -11650),
            intArrayOf(2, 0, -3, 0, 3665, 14403),
            intArrayOf(0, 1, -2, 0, -2689, -7003),
            intArrayOf(2, 0, -1, 2, -2602, 0),
            intArrayOf(2, -1, -2, 0, 2390, 10056),
            intArrayOf(1, 0, 1, 0, -2348, 6322),
            intArrayOf(2, -2, 0, 0, 2236, -9884),
            intArrayOf(0, 1, 2, 0, -2120, 5751),
            intArrayOf(0, 2, 0, 0, -2069, 0),
            intArrayOf(2, -2, -1, 0, 2048, -4950),
            intArrayOf(2, 0, 1, -2, -1773, 4130),
            intArrayOf(2, 0, 0, 2, -1595, 0),
            intArrayOf(4, -1, -1, 0, 1215, -3958),
            intArrayOf(0, 0, 2, 2, -1110, 0),
            intArrayOf(3, 0, -1, 0, -892, 3258),
            intArrayOf(2, 1, 1, 0, -810, 2616),
            intArrayOf(4, -1, -2, 0, 759, -1897),
            intArrayOf(0, 2, -1, 0, -713, -2117),
            intArrayOf(2, 2, -1, 0, -700, 2354),
            intArrayOf(2, 1, -2, 0, 691, 0),
            intArrayOf(2, -1, 0, -2, 596, 0),
            intArrayOf(4, 0, 1, 0, 549, -1423),
            intArrayOf(0, 0, 4, 0, 537, -1117),
            intArrayOf(4, -1, 0, 0, 520, -1571),
            intArrayOf(1, 0, -2, 0, -487, -1739),
            intArrayOf(2, 1, 0, -2, -399, 0),
            intArrayOf(0, 0, 2, -2, -381, -4421),
            intArrayOf(1, 1, 1, 0, 351, 0),
            intArrayOf(3, 0, -2, 0, -340, 0),
            intArrayOf(4, 0, -3, 0, 330, 0),
            intArrayOf(2, -1, 2, 0, 327, 0),
            intArrayOf(0, 2, 1, 0, -323, 1165),
            intArrayOf(1, 1, -1, 0, 299, 0),
            intArrayOf(2, 0, 3, 0, 294, 0),
            intArrayOf(2, 0, -1, -2, 0, 8752)
        )

        /**
         * Meeus table 47.B: multiples of (D, M, M', F), then the latitude coefficient
         * (1e-6 degrees).
         */
        val TABLE_47B = arrayOf(
            intArrayOf(0, 0, 0, 1, 5128122),
            intArrayOf(0, 0, 1, 1, 280602),
            intArrayOf(0, 0, 1, -1, 277693),
            intArrayOf(2, 0, 0, -1, 173237),
            intArrayOf(2, 0, -1, 1, 55413),
            intArrayOf(2, 0, -1, -1, 46271),
            intArrayOf(2, 0, 0, 1, 32573),
            intArrayOf(0, 0, 2, 1, 17198),
            intArrayOf(2, 0, 1, -1, 9266),
            intArrayOf(0, 0, 2, -1, 8822),
            intArrayOf(2, -1, 0, -1, 8216),
            intArrayOf(2, 0, -2, -1, 4324),
            intArrayOf(2, 0, 1, 1, 4200),
            intArrayOf(2, 1, 0, -1, -3359),
            intArrayOf(2, -1, -1, 1, 2463),
            intArrayOf(2, -1, 0, 1, 2211),
            intArrayOf(2, -1, -1, -1, 2065),
            intArrayOf(0, 1, -1, -1, -1870),
            intArrayOf(4, 0, -1, -1, 1828),
            intArrayOf(0, 1, 0, 1, -1794),
            intArrayOf(0, 0, 0, 3, -1749),
            intArrayOf(0, 1, -1, 1, -1565),
            intArrayOf(1, 0, 0, 1, -1491),
            intArrayOf(0, 1, 1, 1, -1475),
            intArrayOf(0, 1, 1, -1, -1410),
            intArrayOf(0, 1, 0, -1, -1344),
            intArrayOf(1, 0, 0, -1, -1335),
            intArrayOf(0, 0, 3, 1, 1107),
            intArrayOf(4, 0, 0, -1, 1021),
            intArrayOf(4, 0, -1, 1, 833),
            intArrayOf(0, 0, 1, -3, 777),
            intArrayOf(4, 0, -2, 1, 671),
            intArrayOf(2, 0, 0, -3, 607),
            intArrayOf(2, 0, 2, -1, 596),
            intArrayOf(2, -1, 1, -1, 491),
            intArrayOf(2, 0, -2, 1, -451),
            intArrayOf(0, 0, 3, -1, 439),
            intArrayOf(2, 0, 2, 1, 422),
            intArrayOf(2, 0, -3, -1, 421),
            intArrayOf(2, 1, -1, 1, -366),
            intArrayOf(2, 1, 0, 1, -351),
            intArrayOf(4, 0, 0, 1, 331),
            intArrayOf(2, -1, 1, 1, 315),
            intArrayOf(2, -2, 0, -1, 302),
            intArrayOf(0, 0, 1, 3, -283),
            intArrayOf(2, 1, 1, -1, -229),
            intArrayOf(1, 1, 0, -1, 223),
            intArrayOf(1, 1, 0, 1, 223),
            intArrayOf(0, 1, -2, -1, -220),
            intArrayOf(2, 1, -1, -1, -220),
            intArrayOf(1, 0, 1, 1, -185),
            intArrayOf(2, -1, -2, -1, 181),
            intArrayOf(0, 1, 2, 1, -177),
            intArrayOf(4, 0, -2, -1, 176),
            intArrayOf(4, -1, -1, -1, 166),
            intArrayOf(1, 0, 1, -1, -164),
            intArrayOf(4, 0, 1, -1, 132),
            intArrayOf(1, 0, -1, -1, -119),
            intArrayOf(4, -1, 0, -1, 115),
            intArrayOf(2, -2, 0, 1, 107)
        )
    }
}
