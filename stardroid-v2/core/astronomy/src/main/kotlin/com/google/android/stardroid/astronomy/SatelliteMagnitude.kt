/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import kotlinx.datetime.Instant
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.log10

/**
 * Whether a satellite is in sunlight, and how bright it looks when it is.
 *
 * These two go together because they share a geometry: both need the direction from the satellite
 * to the Sun, and both need it **in the same frame as the satellite's own position**. SGP4 emits
 * TEME, which is of-date; [MeeusEphemeris] returns J2000 (D84). Rotating one to meet the other is
 * the single detail that makes this file correct, and it is done once in [sunDirectionTeme].
 *
 * See docs/design/satellite-tracking.md §"Visibility" and §"Magnitude".
 */
object SatelliteMagnitude {
    /**
     * The Sun's direction as a unit vector in **TEME**, ready to compare against an SGP4 position.
     *
     * [MeeusEphemeris] hands back J2000 since D84, so this rotates it *inward* to the of-date
     * frame rather than precessing the satellite outward — the same direction D84 established for
     * its own callers, and the cheaper one here since only one vector needs rotating. Skipping it
     * would leave a ~0.365° mismatch between the two vectors in every dot product below.
     */
    fun sunDirectionTeme(time: Instant): Vector3 =
        MeeusEphemeris
            .geocentricPosition(SolarSystemBody.SUN, time)
            .toGeocentricVector()
            .precessedFromJ2000(time)

    /**
     * Whether a satellite at [positionTemeKm] is lit by the Sun rather than inside the Earth's
     * shadow.
     *
     * A cylindrical shadow model: the satellite is lit if it is on the sunward side of the Earth
     * at all, and otherwise lit only if it lies outside the cylinder the Earth casts. Real shadows
     * are conical and have a penumbra, and the atmosphere refracts light into the geometric
     * shadow; [SHADOW_MARGIN_KM] stands in for both, since a bare cylinder overestimates lit time
     * by 10–20 s at shadow entry.
     *
     * This is what makes a pass a *pass*. A satellite crossing a dark sky unlit is simply not
     * there to see, and one entering the shadow mid-sky visibly fades out — the single most-asked
     * question after someone watches it happen.
     */
    fun isSunlit(
        positionTemeKm: Vector3,
        sunDirectionTeme: Vector3,
    ): Boolean {
        val towardSun = positionTemeKm dot sunDirectionTeme
        if (towardSun > 0.0) return true
        // Distance from the Earth-Sun axis: the radius of the shadow cylinder at the satellite.
        val axialOffset = positionTemeKm - sunDirectionTeme * towardSun
        return axialOffset.length > MEAN_EARTH_RADIUS_KM + SHADOW_MARGIN_KM
    }

    /**
     * Apparent visual magnitude of a satellite of standard magnitude [standardMagnitude], seen at
     * [rangeKm] with the given [phaseAngleDeg].
     *
     * The standard magnitude is the satellite's brightness at 1000 km and 90° phase — a per-object
     * constant, since it encodes how big and how reflective the thing is. The two terms that vary
     * are the inverse-square fall-off with range and the phase function: a satellite near "full",
     * with the Sun behind the observer, is far brighter than one seen as a crescent.
     *
     * **Accurate to about ±0.5 magnitude, and no better.** Real satellites are not Lambertian
     * spheres; the ISS's solar arrays swing its brightness by a magnitude either way as they
     * rotate to track the Sun. Display one decimal place and imply no more precision than that.
     */
    fun apparentMagnitude(
        standardMagnitude: Double,
        rangeKm: Double,
        phaseAngleDeg: Double,
    ): Double {
        // (1 + cos θ)/2 — the illuminated fraction of a sphere, the same identity the planetary
        // phase uses. Floored so a satellite seen almost exactly against the Sun does not produce
        // an infinite magnitude; that geometry is unobservable anyway.
        val phaseFactor =
            ((1.0 + cos(phaseAngleDeg / RADIANS_TO_DEGREES)) / 2.0)
                .coerceAtLeast(MIN_PHASE_FACTOR)
        return standardMagnitude - 15.75 +
            2.5 * log10(rangeKm * rangeKm / phaseFactor)
    }

    /**
     * The Sun–satellite–observer angle in degrees: 0° means the satellite is "full" as seen from
     * the ground, 180° means it is backlit.
     *
     * Both inputs must be in the same frame — pass the TEME satellite position and
     * [sunDirectionTeme]'s result.
     */
    fun phaseAngleDeg(
        positionTemeKm: Vector3,
        observerTemeKm: Vector3,
        sunDirectionTeme: Vector3,
    ): Double {
        val towardObserver = (observerTemeKm - positionTemeKm).normalized()
        // The Sun is ~1 AU away, so its direction from the satellite and from the geocentre differ
        // by under an arcsecond — using the geocentric direction here is exact for our purposes.
        return acos((towardObserver dot sunDirectionTeme).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
    }

    /** Standard magnitude of the ISS: its brightness at 1000 km range and 90° phase. */
    const val ISS_STANDARD_MAGNITUDE: Double = -1.3

    /** Mean Earth radius used for the shadow cylinder. */
    private const val MEAN_EARTH_RADIUS_KM = 6371.0

    /**
     * Added to the Earth's radius when testing for shadow, standing in for the penumbra and for
     * atmospheric refraction bending sunlight into the geometric shadow.
     */
    private const val SHADOW_MARGIN_KM = 30.0

    /** Keeps a near-zero phase angle from driving the magnitude to negative infinity. */
    private const val MIN_PHASE_FACTOR = 1.0e-4
}
