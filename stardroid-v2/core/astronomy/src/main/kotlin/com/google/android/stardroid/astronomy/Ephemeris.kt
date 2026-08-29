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
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import kotlinx.datetime.Instant
import kotlin.math.atan

/**
 * Computes apparent positions and appearance of solar-system bodies over time.
 *
 * Implementations are pure and stateless. A future VSOP87/ELP2000 implementation can slot in
 * behind this same interface; [KeplerianEphemeris] is the faithful port of v1's low-precision
 * model.
 */
interface Ephemeris {
    /** Geocentric (Earth-centred, equatorial) apparent position of [body] at [time]. */
    fun geocentricPosition(
        body: SolarSystemBody,
        time: Instant,
    ): RaDec

    /**
     * Topocentric (observer-centred, equatorial) apparent position of [body] at [time] for an
     * observer at [observer] — what the sky actually shows at that location. Defaults to the
     * geocentric position: diurnal parallax only matters for the Moon (up to ~1°, D54); for the
     * Sun (~0.002°) and everything further out it is far below this model class's accuracy.
     */
    fun topocentricPosition(
        body: SolarSystemBody,
        time: Instant,
        observer: LatLong,
    ): RaDec = geocentricPosition(body, time)

    /** Phase angle in degrees (0 = fully lit as seen from Earth, 180 = unlit). */
    fun phaseAngleDeg(
        body: SolarSystemBody,
        time: Instant,
    ): Double

    /** Fraction of the disc illuminated as seen from Earth, in `[0, 1]`. */
    fun illuminatedFraction(
        body: SolarSystemBody,
        time: Instant,
    ): Double

    /** Apparent visual magnitude. */
    fun magnitude(
        body: SolarSystemBody,
        time: Instant,
    ): Double

    /** Maximum apparent angular speed, for layer re-submission thresholds (D13). Degrees/day. */
    fun maxAngularVelocityDegPerDay(body: SolarSystemBody): Double

    /**
     * Distance from Earth's centre in AU. Originally introduced for D18's descending-distance
     * image ordering (so the Moon occludes the Sun during an eclipse), where only the *ordering*
     * mattered; D86 tightened the contract to **good to about 1%**, because [angularDiameterDeg]
     * now divides by it and a body's drawn size is only as honest as this is.
     */
    fun earthDistanceAu(
        body: SolarSystemBody,
        time: Instant,
    ): Double

    /**
     * True apparent equatorial diameter of [body] as seen from Earth, in degrees — what D86 draws
     * bodies at once they are large enough on screen to deserve it.
     *
     * `2·atan(r / d)` from the body's equatorial radius and [earthDistanceAu], so its accuracy is
     * that of the distance. That is what makes a supermoon read as one: the Moon's distance
     * varies by about 12% over an anomalistic month, and its disc with it.
     *
     * Note this is the diameter of the **globe**. Saturn's texture spans its ring system, so the
     * layer scales this up for Saturn rather than the renderer doing it (see
     * `assets/planets/SOURCES.md`).
     */
    fun angularDiameterDeg(
        body: SolarSystemBody,
        time: Instant,
    ): Double {
        val distanceKm = earthDistanceAu(body, time) * KM_PER_AU
        return 2.0 * atan(equatorialRadiusKm(body) / distanceKm) * RADIANS_TO_DEGREES
    }

    /** The range of instants over which this implementation is considered valid. */
    val validRange: ClosedRange<Instant>
}
