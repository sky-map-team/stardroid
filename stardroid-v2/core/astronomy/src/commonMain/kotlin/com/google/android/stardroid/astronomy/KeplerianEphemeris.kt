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
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * The low-precision ephemeris ported from v1 (`space/` + `ephemeris/`): JPL two-term elements,
 * an iterative Kepler solver, the Astronomical Almanac (2008, p. D22) lunar approximation, and
 * Van Flandern-style magnitude polynomials — now in `Double`.
 *
 * Known v1 quirks are preserved deliberately, as the faithful-port baseline pinned by golden
 * tests (see docs/design/core-math-astronomy.md):
 *  - lunar phase angle approximated from elongation, not the true Sun–Moon–Earth angle;
 *  - Saturn magnitude fixed (ring geometry ignored); Sun/Moon magnitudes fixed constants.
 *
 * One v1 quirk is *not* carried into [topocentricPosition]: v1 drew the Moon at its geocentric
 * position, up to ~1° off depending on where on Earth the observer stands. The same Almanac
 * page supplies the horizontal-parallax series, so the topocentric query applies the full
 * diurnal-parallax correction for the Moon (D54); [geocentricPosition] stays geocentric.
 */
object KeplerianEphemeris : Ephemeris {
    private val RANGE_START: Instant = LocalDate(1800, 1, 1).atStartOfDayIn(TimeZone.UTC)
    private val RANGE_END: Instant = LocalDate(2050, 1, 1).atStartOfDayIn(TimeZone.UTC)

    override val validRange: ClosedRange<Instant> = RANGE_START..RANGE_END

    override fun geocentricPosition(
        body: SolarSystemBody,
        time: Instant,
    ): RaDec =
        when (body) {
            SolarSystemBody.MOON -> lunarPosition(time)
            SolarSystemBody.SUN -> sunOrbitingPosition(Vector3.ZERO, time)
            // Earth is the origin of geocentric coordinates: its position would be the degenerate
            // RaDec(0, 0). It's an internal element source, not a viewable target.
            SolarSystemBody.EARTH -> throw IllegalArgumentException(
                "Earth has no geocentric position",
            )
            else ->
                sunOrbitingPosition(
                    heliocentricCoordinates(orbitalElementsFor(body, time)),
                    time,
                )
        }

    override fun topocentricPosition(
        body: SolarSystemBody,
        time: Instant,
        observer: LatLong,
    ): RaDec =
        when (body) {
            SolarSystemBody.MOON -> topocentricLunarPosition(time, observer)
            // Every other body's diurnal parallax (Sun ≤ 0.002°, Venus at closest ≤ 0.01°) is
            // far below this model's own error; the interface default is exact enough.
            else -> geocentricPosition(body, time)
        }

    override fun phaseAngleDeg(
        body: SolarSystemBody,
        time: Instant,
    ): Double =
        when (body) {
            SolarSystemBody.SUN -> 0.0
            SolarSystemBody.MOON -> lunarPhaseAngleDeg(time)
            // Phase angle of Earth from Earth is undefined (0/0 in planetPhaseAngleDeg → NaN).
            SolarSystemBody.EARTH -> throw IllegalArgumentException("Earth has no phase angle")
            else -> {
                val (planet, earth) = heliocentricPair(body, time)
                planetPhaseAngleDeg(planet, earth)
            }
        }

    override fun illuminatedFraction(
        body: SolarSystemBody,
        time: Instant,
    ): Double = 0.5 * (1.0 + cos(phaseAngleDeg(body, time) * DEGREES_TO_RADIANS))

    override fun magnitude(
        body: SolarSystemBody,
        time: Instant,
    ): Double =
        when (body) {
            SolarSystemBody.SUN -> -27.0
            SolarSystemBody.MOON -> -10.0
            SolarSystemBody.EARTH -> throw IllegalArgumentException("Earth has no magnitude")
            else -> {
                val (planet, earth) = heliocentricPair(body, time)
                val earthDistance = planet.distanceTo(earth)
                val p = planetPhaseAngleDeg(planet, earth) / 100.0
                val baseMagnitude =
                    when (body) {
                        SolarSystemBody.MERCURY -> -0.42 + (3.80 - (2.73 - 2.00 * p) * p) * p
                        SolarSystemBody.VENUS -> -4.40 + (0.09 + (2.39 - 0.65 * p) * p) * p
                        SolarSystemBody.MARS -> -1.52 + 1.6 * p
                        SolarSystemBody.JUPITER -> -9.40 + 0.5 * p
                        // Fixed approximations (v1): ring geometry / faint outer planets
                        // not modelled.
                        SolarSystemBody.SATURN -> -8.75
                        SolarSystemBody.URANUS -> -7.19
                        SolarSystemBody.NEPTUNE -> -6.87
                        SolarSystemBody.PLUTO -> -1.0
                        else -> throw IllegalArgumentException("No magnitude model for $body")
                    }
                baseMagnitude + 5.0 * log10(planet.length * earthDistance)
            }
        }

    override fun maxAngularVelocityDegPerDay(body: SolarSystemBody): Double =
        when (body) {
            // Conservative upper bounds on apparent geocentric motion, used only as re-submission
            // thresholds (D13): err toward over-frequent updates, never under.
            SolarSystemBody.MOON -> 15.0
            SolarSystemBody.MERCURY -> 7.0
            SolarSystemBody.VENUS -> 4.0
            SolarSystemBody.SUN -> 1.1
            SolarSystemBody.MARS -> 1.5
            SolarSystemBody.JUPITER -> 0.25
            SolarSystemBody.SATURN -> 0.13
            SolarSystemBody.URANUS -> 0.06
            SolarSystemBody.NEPTUNE -> 0.04
            SolarSystemBody.PLUTO -> 0.04
            // Consistent with the other geocentric queries: Earth is not a viewable target.
            SolarSystemBody.EARTH -> throw IllegalArgumentException("Earth has no angular velocity")
        }

    override fun earthDistanceAu(
        body: SolarSystemBody,
        time: Instant,
    ): Double =
        when (body) {
            // The Almanac lunar approximation yields direction only; the mean distance is ample
            // for D18 ordering (nothing else orbits between the Moon and Earth).
            SolarSystemBody.MOON -> MOON_MEAN_DISTANCE_AU
            SolarSystemBody.SUN ->
                heliocentricCoordinates(orbitalElementsFor(SolarSystemBody.EARTH, time)).length
            SolarSystemBody.EARTH -> throw IllegalArgumentException(
                "Earth has no distance from itself",
            )
            else -> {
                val (planet, earth) = heliocentricPair(body, time)
                planet.distanceTo(earth)
            }
        }

    private const val MOON_MEAN_DISTANCE_AU = 0.00257

    // --- internals ---

    private fun heliocentricPair(
        body: SolarSystemBody,
        time: Instant,
    ): Pair<Vector3, Vector3> =
        heliocentricCoordinates(orbitalElementsFor(body, time)) to
            heliocentricCoordinates(orbitalElementsFor(SolarSystemBody.EARTH, time))

    private fun sunOrbitingPosition(
        myHeliocentric: Vector3,
        time: Instant,
    ): RaDec {
        val earth = heliocentricCoordinates(orbitalElementsFor(SolarSystemBody.EARTH, time))
        return RaDec.fromGeocentricVector(toEquatorialCoordinates(myHeliocentric - earth))
    }

    private fun planetPhaseAngleDeg(
        planet: Vector3,
        earth: Vector3,
    ): Double {
        val earthDistance = planet.distanceTo(earth)
        val cosPhase =
            (earthDistance * earthDistance + planet.length2 - earth.length2) /
                (2.0 * earthDistance * planet.length)
        return acos(cosPhase.coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
    }

    private fun lunarPhaseAngleDeg(time: Instant): Double {
        val moon = lunarGeocentricDirection(time)
        // Earth's heliocentric vector points Sun->Earth, i.e. toward the anti-solar point as seen
        // from Earth. v1 leaves it in ecliptic coords (no toEquatorialCoordinates) and dots it
        // against the equatorial Moon vector: a deliberate part of the ~1% elongation
        // approximation (D25), preserved here — not an accidental frame mismatch. v1's RaDec
        // round-trip to unitize it is just a normalize, so call that directly.
        val antiSolar =
            heliocentricCoordinates(orbitalElementsFor(SolarSystemBody.EARTH, time)).normalized()
        // acos(antiSolar·moon) is the Sun–Moon–Earth phase angle: 0° at full moon (Moon at the
        // anti-solar point), 180° at new moon — matching the interface contract and the planets.
        // v1 returned `180 - this` (the elongation), inverting illuminatedFraction into a 0%-lit
        // full moon; v1 flagged that as wrong and disabled its own test. Corrected here (D25).
        return acos((antiSolar dot moon).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
    }

    private fun lunarPosition(time: Instant): RaDec =
        RaDec.fromGeocentricVector(lunarGeocentricDirection(time))

    /**
     * Topocentric lunar RA/Dec: the geocentric Almanac direction shifted by the observer's
     * offset from the geocentre (same source, p. D22). The horizontal-parallax series puts the
     * Moon at `1/sin(π)` Earth radii; the observer sits one Earth radius out at
     * `(cos φ cos LMST, cos φ sin LMST, sin φ)` in the same equatorial frame. Geographic
     * latitude stands in for geocentric and the observer for the geoid surface — both
     * arcsecond-scale simplifications, far below the series' own accuracy.
     */
    private fun topocentricLunarPosition(
        time: Instant,
        observer: LatLong,
    ): RaDec {
        val distanceEarthRadii =
            1.0 / sin(lunarHorizontalParallaxDeg(time) * DEGREES_TO_RADIANS)
        val lmstRad = meanSiderealTimeDeg(time, observer.longitudeDeg) * DEGREES_TO_RADIANS
        val latRad = observer.latitudeDeg * DEGREES_TO_RADIANS
        val observerFromGeocentre =
            Vector3(cos(latRad) * cos(lmstRad), cos(latRad) * sin(lmstRad), sin(latRad))
        return RaDec.fromGeocentricVector(
            lunarGeocentricDirection(time) * distanceEarthRadii - observerFromGeocentre,
        )
    }

    /** Lunar equatorial horizontal parallax in degrees, from the same Almanac page. */
    private fun lunarHorizontalParallaxDeg(time: Instant): Double {
        val t = time.julianCenturiesJ2000()
        return 0.9508 +
            0.0518 * cos((134.9 + 477198.85 * t) * DEGREES_TO_RADIANS) +
            0.0095 * cos((259.2 - 413335.38 * t) * DEGREES_TO_RADIANS) +
            0.0078 * cos((235.7 + 890534.23 * t) * DEGREES_TO_RADIANS) +
            0.0028 * cos((269.9 + 954397.70 * t) * DEGREES_TO_RADIANS)
    }

    /**
     * Geocentric lunar direction from the Astronomical Almanac (2008) p. D22 approximation.
     * `lambda` is ecliptic longitude, `beta` ecliptic latitude (both degrees); the result is
     * the equatorial unit direction cosines (l, m, n).
     */
    private fun lunarGeocentricDirection(time: Instant): Vector3 {
        val t = time.julianCenturiesJ2000()
        val lambda =
            218.32 + 481267.881 * t +
                6.29 * sin((135.0 + 477198.87 * t) * DEGREES_TO_RADIANS) -
                1.27 * sin((259.3 - 413335.36 * t) * DEGREES_TO_RADIANS) +
                0.66 * sin((235.7 + 890534.22 * t) * DEGREES_TO_RADIANS) +
                0.21 * sin((269.9 + 954397.74 * t) * DEGREES_TO_RADIANS) -
                0.19 * sin((357.5 + 35999.05 * t) * DEGREES_TO_RADIANS) -
                0.11 * sin((186.5 + 966404.03 * t) * DEGREES_TO_RADIANS)
        val beta =
            5.13 * sin((93.3 + 483202.02 * t) * DEGREES_TO_RADIANS) +
                0.28 * sin((228.2 + 960400.89 * t) * DEGREES_TO_RADIANS) -
                0.28 * sin((318.3 + 6003.15 * t) * DEGREES_TO_RADIANS) -
                0.17 * sin((217.6 - 407332.21 * t) * DEGREES_TO_RADIANS)
        val lambdaRad = lambda * DEGREES_TO_RADIANS
        val betaRad = beta * DEGREES_TO_RADIANS
        val l = cos(betaRad) * cos(lambdaRad)
        val m = 0.9175 * cos(betaRad) * sin(lambdaRad) - 0.3978 * sin(betaRad)
        val n = 0.3978 * cos(betaRad) * sin(lambdaRad) + 0.9175 * sin(betaRad)
        return Vector3(l, m, n)
    }
}
