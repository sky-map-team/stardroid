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
import com.google.android.stardroid.math.normalizeRadians
import kotlinx.datetime.Instant
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The six Keplerian elements describing an orbit around the Sun. Angles are in radians.
 *
 * Ported from v1's `OrbitalElements`, in `Double` and with the `android.util.Log` convergence
 * logging removed (the iterative solver simply caps its iterations).
 *
 * Source: JPL's approximations (Van Flandern & Pulkkinen, 1979), good for ~1800–2050.
 */
data class OrbitalElements(
    val distanceAu: Double,
    val eccentricity: Double,
    val inclinationRad: Double,
    val ascendingNodeRad: Double,
    val perihelionRad: Double,
    val meanLongitudeRad: Double,
) {
    /** True anomaly in radians. */
    val anomaly: Double
        get() = trueAnomaly(meanLongitudeRad - perihelionRad, eccentricity)
}

private const val KEPLER_EPSILON = 1.0e-8
private const val KEPLER_MAX_ITERATIONS = 100

/**
 * Solves Kepler's equation for the true anomaly, given mean anomaly [m] (radians) and
 * eccentricity [e], by Newton iteration on the eccentric anomaly.
 */
private fun trueAnomaly(
    m: Double,
    e: Double,
): Double {
    var eccentricAnomaly = m + e * sin(m) * (1.0 + e * cos(m))
    var iterations = 0
    while (iterations++ < KEPLER_MAX_ITERATIONS) {
        val delta =
            (eccentricAnomaly - e * sin(eccentricAnomaly) - m) /
                (1.0 - e * cos(eccentricAnomaly))
        eccentricAnomaly -= delta
        if (kotlin.math.abs(delta) <= KEPLER_EPSILON) break
    }
    val v = 2.0 * atan(sqrt((1 + e) / (1 - e)) * tan(0.5 * eccentricAnomaly))
    return normalizeRadians(v)
}

/**
 * The osculating orbital elements for [body] at [time], from JPL's two-term linear fits
 * (`element = a0 + a1 · centuriesSinceJ2000`).
 *
 * @throws IllegalArgumentException for [SolarSystemBody.SUN] or [SolarSystemBody.MOON], which do
 *   not orbit the Sun on these elements.
 */
fun orbitalElementsFor(
    body: SolarSystemBody,
    time: Instant,
): OrbitalElements {
    val jc = time.julianCenturiesJ2000()
    return when (body) {
        SolarSystemBody.MERCURY ->
            OrbitalElements(
                0.38709927 + 0.00000037 * jc,
                0.20563593 + 0.00001906 * jc,
                (7.00497902 - 0.00594749 * jc) * DEGREES_TO_RADIANS,
                (48.33076593 - 0.12534081 * jc) * DEGREES_TO_RADIANS,
                (77.45779628 + 0.16047689 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((252.25032350 + 149472.67411175 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.VENUS ->
            OrbitalElements(
                0.72333566 + 0.00000390 * jc,
                0.00677672 - 0.00004107 * jc,
                (3.39467605 - 0.00078890 * jc) * DEGREES_TO_RADIANS,
                (76.67984255 - 0.27769418 * jc) * DEGREES_TO_RADIANS,
                (131.60246718 + 0.00268329 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((181.97909950 + 58517.81538729 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.EARTH ->
            OrbitalElements(
                1.00000261 + 0.00000562 * jc,
                0.01671123 - 0.00004392 * jc,
                (-0.00001531 - 0.01294668 * jc) * DEGREES_TO_RADIANS,
                0.0,
                (102.93768193 + 0.32327364 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((100.46457166 + 35999.37244981 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.MARS ->
            OrbitalElements(
                1.52371034 + 0.00001847 * jc,
                0.09339410 + 0.00007882 * jc,
                (1.84969142 - 0.00813131 * jc) * DEGREES_TO_RADIANS,
                (49.55953891 - 0.29257343 * jc) * DEGREES_TO_RADIANS,
                (-23.94362959 + 0.44441088 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((-4.55343205 + 19140.30268499 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.JUPITER ->
            OrbitalElements(
                5.20288700 - 0.00011607 * jc,
                0.04838624 - 0.00013253 * jc,
                (1.30439695 - 0.00183714 * jc) * DEGREES_TO_RADIANS,
                (100.47390909 + 0.20469106 * jc) * DEGREES_TO_RADIANS,
                (14.72847983 + 0.21252668 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((34.39644051 + 3034.74612775 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.SATURN ->
            OrbitalElements(
                9.53667594 - 0.00125060 * jc,
                0.05386179 - 0.00050991 * jc,
                (2.48599187 + 0.00193609 * jc) * DEGREES_TO_RADIANS,
                (113.66242448 - 0.28867794 * jc) * DEGREES_TO_RADIANS,
                (92.59887831 - 0.41897216 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((49.95424423 + 1222.49362201 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.URANUS ->
            OrbitalElements(
                19.18916464 - 0.00196176 * jc,
                0.04725744 - 0.00004397 * jc,
                (0.77263783 - 0.00242939 * jc) * DEGREES_TO_RADIANS,
                (74.01692503 + 0.04240589 * jc) * DEGREES_TO_RADIANS,
                (170.95427630 + 0.40805281 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((313.23810451 + 428.48202785 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.NEPTUNE ->
            OrbitalElements(
                30.06992276 + 0.00026291 * jc,
                0.00859048 + 0.00005105 * jc,
                (1.77004347 + 0.00035372 * jc) * DEGREES_TO_RADIANS,
                (131.78422574 - 0.00508664 * jc) * DEGREES_TO_RADIANS,
                (44.96476227 - 0.32241464 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((-55.12002969 + 218.45945325 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.PLUTO ->
            OrbitalElements(
                39.48211675 - 0.00031596 * jc,
                0.24882730 + 0.00005170 * jc,
                (17.14001206 + 0.00004818 * jc) * DEGREES_TO_RADIANS,
                (110.30393684 - 0.01183482 * jc) * DEGREES_TO_RADIANS,
                (224.06891629 - 0.04062942 * jc) * DEGREES_TO_RADIANS,
                normalizeRadians((238.92903833 + 145.20780515 * jc) * DEGREES_TO_RADIANS),
            )
        SolarSystemBody.SUN, SolarSystemBody.MOON ->
            throw IllegalArgumentException("$body has no heliocentric orbital elements")
    }
}
