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
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A higher-precision ephemeris that slots in behind the [Ephemeris] interface (the successor the
 * interface docs anticipate). It sharpens only the two positions that matter for solar-eclipse
 * timing, and delegates everything else to the faithful-port [KeplerianEphemeris] baseline:
 *
 *  - the **Sun**'s apparent RA/Dec, from Meeus, *Astronomical Algorithms* (2nd ed., ch. 25,
 *    "lower accuracy" method, ~0.01°). The baseline derives it from Earth's truncated Keplerian
 *    elements with no aberration/nutation, leaving it ~0.4° off;
 *  - the **Moon**'s geocentric/topocentric position and Earth distance, from the ELP-2000/82
 *    truncated series in Meeus ch. 47 (~10″ longitude, ~4″ latitude). The baseline uses the
 *    six-term Almanac D22 approximation, ~0.3° off.
 *
 * Maximum eclipse is when the topocentric Sun–Moon separation is least; at the ~0.5°/hr rate the
 * Moon closes on the Sun, 0.1° of error in either body shifts the moment by ~13 minutes, so the
 * baseline placed some eclipse maxima ~30 minutes out. Both series here run on Terrestrial Time
 * ([Instant.julianCenturiesTerrestrialJ2000]) and in `Double` throughout.
 *
 * Both series produce coordinates referred to the equinox **of date**, which is not the frame the
 * app draws in: the catalog, the orbital elements and [toEquatorialCoordinates] are all J2000. So
 * every position here is rotated back to J2000 by [precessedToJ2000] before it is returned — see
 * [Precession] and docs/design/ephemeris-accuracy.md. Eclipse timing is untouched by that rotation
 * (it is common to Sun and Moon, and angular separation is invariant under it); what it fixes is
 * the ~0.365° displacement, growing 0.014°/year, that the Sun and Moon would otherwise carry
 * against the star field they are drawn on.
 *
 * Appearance (phase angle, illuminated fraction, magnitude) is left to [KeplerianEphemeris]:
 * those feed disc icons, not eclipse timing, and keeping them on the baseline preserves the
 * lunar-phase golden tests. Planet positions are unchanged.
 */
object MeeusEphemeris : Ephemeris {
    override val validRange: ClosedRange<Instant> = KeplerianEphemeris.validRange

    override fun geocentricPosition(
        body: SolarSystemBody,
        time: Instant,
    ): RaDec =
        when (body) {
            SolarSystemBody.SUN -> sunApparentPosition(time)
            SolarSystemBody.MOON ->
                RaDec.fromGeocentricVector(
                    moonPosition(time).directionCosines().precessedToJ2000(time),
                )
            else -> KeplerianEphemeris.geocentricPosition(body, time)
        }

    override fun topocentricPosition(
        body: SolarSystemBody,
        time: Instant,
        observer: LatLong,
    ): RaDec =
        when (body) {
            SolarSystemBody.MOON -> topocentricMoonPosition(time, observer)
            SolarSystemBody.SUN -> sunApparentPosition(time) // parallax ≤ 0.002°, negligible
            else -> KeplerianEphemeris.topocentricPosition(body, time, observer)
        }

    override fun phaseAngleDeg(
        body: SolarSystemBody,
        time: Instant,
    ): Double = KeplerianEphemeris.phaseAngleDeg(body, time)

    override fun illuminatedFraction(
        body: SolarSystemBody,
        time: Instant,
    ): Double = KeplerianEphemeris.illuminatedFraction(body, time)

    override fun magnitude(
        body: SolarSystemBody,
        time: Instant,
    ): Double = KeplerianEphemeris.magnitude(body, time)

    override fun maxAngularVelocityDegPerDay(body: SolarSystemBody): Double =
        KeplerianEphemeris.maxAngularVelocityDegPerDay(body)

    override fun earthDistanceAu(
        body: SolarSystemBody,
        time: Instant,
    ): Double =
        when (body) {
            // The series gives a true geocentric distance; the baseline used a fixed mean. Still
            // only used for D18 image ordering, but now it is also physically right.
            SolarSystemBody.MOON -> moonPosition(time).distanceKm / KM_PER_AU
            else -> KeplerianEphemeris.earthDistanceAu(body, time)
        }

    // --- Sun (Meeus ch. 25) ---

    /** The Sun's apparent position, rotated from the equinox of date into the app's J2000 frame. */
    private fun sunApparentPosition(time: Instant): RaDec =
        RaDec.fromGeocentricVector(sunOfDateDirection(time).precessedToJ2000(time))

    private fun sunOfDateDirection(time: Instant): Vector3 {
        val t = time.julianCenturiesTerrestrialJ2000()
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) * DEGREES_TO_RADIANS
        val c =
            (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
                (0.019993 - 0.000101 * t) * sin(2 * m) +
                0.000289 * sin(3 * m)
        val trueLong = l0 + c
        val omega = (125.04 - 1934.136 * t) * DEGREES_TO_RADIANS
        // Apparent longitude: nutation in longitude and aberration.
        val lambda = (trueLong - 0.00569 - 0.00478 * sin(omega)) * DEGREES_TO_RADIANS
        val epsilon = (23.439291 - 0.0130042 * t + 0.00256 * cos(omega)) * DEGREES_TO_RADIANS
        // Ecliptic (latitude 0, to this method's accuracy) rotated into the equator of date.
        return Vector3(
            cos(lambda),
            cos(epsilon) * sin(lambda),
            sin(epsilon) * sin(lambda),
        )
    }

    // --- Moon (Meeus ch. 47, ELP-2000/82 truncated) ---

    private class MoonState(
        val lambdaDeg: Double,
        val betaDeg: Double,
        val distanceKm: Double,
        val obliquityRad: Double,
    ) {
        /** Apparent ecliptic coordinates rotated into equatorial direction cosines (Meeus 13.3–13.4). */
        fun directionCosines(): Vector3 {
            val lambda = lambdaDeg * DEGREES_TO_RADIANS
            val beta = betaDeg * DEGREES_TO_RADIANS
            val cosBeta = cos(beta)
            return Vector3(
                cosBeta * cos(lambda),
                cos(obliquityRad) * cosBeta * sin(lambda) - sin(obliquityRad) * sin(beta),
                sin(obliquityRad) * cosBeta * sin(lambda) + cos(obliquityRad) * sin(beta),
            )
        }
    }

    private fun topocentricMoonPosition(
        time: Instant,
        observer: LatLong,
    ): RaDec {
        val moon = moonPosition(time)
        val distanceEarthRadii = moon.distanceKm / EARTH_EQUATORIAL_RADIUS_KM
        val lmstRad = meanSiderealTimeDeg(time, observer.longitudeDeg) * DEGREES_TO_RADIANS
        val latRad = observer.latitudeDeg * DEGREES_TO_RADIANS
        val observerFromGeocentre =
            Vector3(cos(latRad) * cos(lmstRad), cos(latRad) * sin(lmstRad), sin(latRad))
        // Sidereal time is of-date, so the observer offset is too: the subtraction has to happen
        // in that frame, and only the result is rotated into J2000.
        val topocentric =
            moon.directionCosines() * distanceEarthRadii - observerFromGeocentre
        return RaDec.fromGeocentricVector(topocentric.precessedToJ2000(time))
    }

    private fun moonPosition(time: Instant): MoonState {
        val t = time.julianCenturiesTerrestrialJ2000()
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        // Fundamental arguments in degrees (Meeus 47.1–47.5).
        val lp =
            218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
        val mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t
        val e = 1.0 - 0.002516 * t - 0.0000074 * t2 // eccentricity correction (Meeus 47.6)

        var sumL = 0.0 // 1e-6 degrees
        var sumR = 0.0 // 1e-3 km
        for (row in TABLE_47A) {
            val arg = (row[0] * d + row[1] * m + row[2] * mp + row[3] * f) * DEGREES_TO_RADIANS
            val eFactor = eccentricityFactor(row[1], e)
            sumL += row[4] * eFactor * sin(arg)
            sumR += row[5] * eFactor * cos(arg)
        }
        var sumB = 0.0 // 1e-6 degrees
        for (row in TABLE_47B) {
            val arg = (row[0] * d + row[1] * m + row[2] * mp + row[3] * f) * DEGREES_TO_RADIANS
            sumB += row[4] * eccentricityFactor(row[1], e) * sin(arg)
        }

        // Additive terms from Venus, Jupiter and the flattening of the Earth (Meeus, p. 342).
        sumL +=
            3958.0 * sin(a1 * DEGREES_TO_RADIANS) +
            1962.0 * sin((lp - f) * DEGREES_TO_RADIANS) +
            318.0 * sin(a2 * DEGREES_TO_RADIANS)
        sumB +=
            -2235.0 * sin(lp * DEGREES_TO_RADIANS) +
            382.0 * sin(a3 * DEGREES_TO_RADIANS) +
            175.0 * sin((a1 - f) * DEGREES_TO_RADIANS) +
            175.0 * sin((a1 + f) * DEGREES_TO_RADIANS) +
            127.0 * sin((lp - mp) * DEGREES_TO_RADIANS) -
            115.0 * sin((lp + mp) * DEGREES_TO_RADIANS)

        var lambda = lp + sumL / 1_000_000.0
        val beta = sumB / 1_000_000.0
        val distanceKm = 385000.56 + sumR / 1000.0

        // Leading nutation terms (Meeus ch. 22): apparent longitude and true obliquity.
        val omega = 125.04452 - 1934.136261 * t
        val lSun = 280.4665 + 36000.7698 * t
        val lMoon = 218.3165 + 481267.8813 * t
        val dPsiArcsec =
            -17.20 * sin(omega * DEGREES_TO_RADIANS) -
                1.32 * sin(2 * lSun * DEGREES_TO_RADIANS) -
                0.23 * sin(2 * lMoon * DEGREES_TO_RADIANS) +
                0.21 * sin(2 * omega * DEGREES_TO_RADIANS)
        val dEpsilonArcsec =
            9.20 * cos(omega * DEGREES_TO_RADIANS) +
                0.57 * cos(2 * lSun * DEGREES_TO_RADIANS) +
                0.10 * cos(2 * lMoon * DEGREES_TO_RADIANS) -
                0.09 * cos(2 * omega * DEGREES_TO_RADIANS)
        lambda += dPsiArcsec / 3600.0
        val epsilon0 = 23.4392911 - 0.0130042 * t - 1.64e-7 * t2 + 5.04e-7 * t3
        val obliquityRad = (epsilon0 + dEpsilonArcsec / 3600.0) * DEGREES_TO_RADIANS

        return MoonState(lambda, beta, distanceKm, obliquityRad)
    }

    private fun eccentricityFactor(
        sunAnomalyMultiple: Int,
        e: Double,
    ): Double =
        when (abs(sunAnomalyMultiple)) {
            1 -> e
            2 -> e * e
            else -> 1.0
        }

    private const val EARTH_EQUATORIAL_RADIUS_KM = 6378.14

    /**
     * Meeus table 47.A: multiples of (D, M, M′, F), then the longitude coefficient (1e-6 degrees)
     * and the distance coefficient (1e-3 km).
     */
    private val TABLE_47A =
        arrayOf(
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
            intArrayOf(2, 0, -1, -2, 0, 8752),
        )

    /** Meeus table 47.B: multiples of (D, M, M′, F), then the latitude coefficient (1e-6 degrees). */
    private val TABLE_47B =
        arrayOf(
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
            intArrayOf(2, -2, 0, 1, 107),
        )
}
