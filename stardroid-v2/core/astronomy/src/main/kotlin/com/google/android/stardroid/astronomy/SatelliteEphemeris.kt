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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The equatorial reference frame a satellite direction is expressed in.
 *
 * The two differ by the precession accumulated since J2000 — about 0.365° in 2026 and growing —
 * which is more than half a Moon-width, so confusing them is a real error rather than a rounding
 * one. It is also a silent error: both frames produce perfectly plausible-looking right ascensions.
 * Hence [TopocentricPosition] carries which one it is rather than leaving it to a KDoc.
 */
enum class ReferenceFrame {
    /**
     * True Equator, Mean Equinox — the of-date frame SGP4 emits and the one
     * [meanSiderealTimeDeg]'s hour angles are consistent with. Horizontal coordinates must be
     * computed in this frame.
     */
    TEME,

    /** The J2000 mean equatorial frame the star catalog and the map layer use. */
    J2000,
}

/**
 * Where a satellite is as seen from a point on the ground: a direction, the [frame] that direction
 * is expressed in, and the range it is at.
 *
 * Range matters here in a way it never did for the rest of the app. Every other body is far
 * enough away that its geocentric and topocentric directions agree to arcseconds, so [RaDec]
 * alone suffices; a satellite at 400 km is close enough that the observer's own offset from the
 * geocentre swings the direction by tens of degrees, and near enough that range drives brightness
 * and shadow geometry too.
 *
 * Prefer [altitudeDeg] and [azimuthDeg] over reading [raDec] directly — they check the frame for
 * you, which is the whole reason [frame] is a field.
 */
data class TopocentricPosition(
    val raDec: RaDec,
    val rangeKm: Double,
    /** Positive when the satellite is receding. Drives Doppler and, later, pass geometry. */
    val rangeRateKmPerSec: Double,
    val frame: ReferenceFrame,
)

/**
 * The satellite's altitude above the horizon, in degrees.
 *
 * Requires a [ReferenceFrame.TEME] position: [altitudeDeg]'s hour angle comes from
 * [meanSiderealTimeDeg], which is of-date, so handing it a J2000 direction quietly costs the
 * ~0.365° precession angle. That is exactly the mistake this overload exists to make impossible.
 */
fun TopocentricPosition.altitudeDeg(
    time: Instant,
    observer: LatLong,
): Double = altitudeDeg(requireTeme("altitude"), time, observer)

/** The satellite's azimuth, degrees east of north. Requires TEME for the same reason. */
fun TopocentricPosition.azimuthDeg(
    time: Instant,
    observer: LatLong,
): Double = azimuthDeg(requireTeme("azimuth"), time, observer)

private fun TopocentricPosition.requireTeme(quantity: String): RaDec {
    require(frame == ReferenceFrame.TEME) {
        "Horizontal $quantity must be computed from a TEME position; this one is $frame, which " +
            "would carry the ~0.365° precession angle into the result"
    }
    return raDec
}

/**
 * Carries [Sgp4]'s output the rest of the way: from the TEME frame it is defined in, to the
 * observer's horizon and to the J2000 sky the map draws.
 *
 * Most third-party SGP4 ports are correct in the propagator and wrong here, so the two decisions
 * that matter are stated rather than buried:
 *
 * **The observer sits on the WGS84 ellipsoid, not a sphere.** [KeplerianEphemeris] places the
 * observer at exactly one Earth radius because for the Moon, at 60 Earth radii, the ~21 km
 * difference between the equatorial and polar radii costs arcseconds. For a satellite at ~1.07
 * Earth radii the same shortcut costs **1–3°**, which is the single largest error available to
 * this feature. That pattern is deliberately not reused.
 *
 * **Alt/az needs no frame conversion; the map layer does.** TEME's defining property is that GMST
 * is exactly the angle relating it to the Earth-fixed frame, so an observer built with GMST and a
 * satellite emitted by SGP4 are already in the same frame — differencing them gives altitude and
 * azimuth good to arcseconds. Only the map layer, which draws against a J2000 catalog, needs the
 * ~0.36° precession rotation out to J2000.
 *
 * See docs/design/satellite-tracking.md §"Frame chain" for the full error budget.
 */
object SatelliteEphemeris {
    /** WGS84 equatorial radius, in km. */
    private const val WGS84_EQUATORIAL_RADIUS_KM = 6378.137

    private const val WGS84_FLATTENING = 1.0 / 298.257223563

    /** Square of the first eccentricity, `f(2 - f)`. */
    private val WGS84_ECCENTRICITY_SQUARED =
        WGS84_FLATTENING * (2.0 - WGS84_FLATTENING)

    /**
     * Earth's rotation rate in radians per second, which is what carries the observer sideways
     * through inertial space and so contributes to range rate.
     */
    private const val EARTH_ROTATION_RAD_PER_SEC = 7.292115e-5

    /**
     * The observer's geocentric position in TEME, in km, for a point at sea level on the WGS84
     * ellipsoid.
     *
     * [LatLong] carries no altitude, so sea level is assumed. That is worth about 0.1° of
     * direction for an observer 1 km up — an order of magnitude below the spherical-Earth error
     * this function exists to avoid, and it is the accuracy ceiling of the whole chain until
     * `LatLong` grows an elevation.
     *
     * Geodetic latitude (what a phone reports) is not geocentric latitude, which is why the
     * ellipsoid's radius of curvature appears rather than a single radius.
     */
    fun observerPositionTeme(
        time: Instant,
        observer: LatLong,
    ): Vector3 {
        val latRad = observer.latitudeDeg * DEGREES_TO_RADIANS
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        // Radius of curvature in the prime vertical, scaled to the ellipsoid's two axes.
        val curvature =
            1.0 / sqrt(1.0 - WGS84_ECCENTRICITY_SQUARED * sinLat * sinLat)
        val equatorial = WGS84_EQUATORIAL_RADIUS_KM * curvature * cosLat
        val polar =
            WGS84_EQUATORIAL_RADIUS_KM * curvature * (1.0 - WGS84_ECCENTRICITY_SQUARED) * sinLat

        // Local mean sidereal time is exactly the angle from TEME's x axis round to the
        // observer's meridian; reusing meanSiderealTimeDeg rather than introducing a second GMST
        // keeps this consistent with RiseSet's hour angles, at a cost of ~0.0004°.
        val lmstRad = meanSiderealTimeDeg(time, observer.longitudeDeg) * DEGREES_TO_RADIANS
        return Vector3(equatorial * cos(lmstRad), equatorial * sin(lmstRad), polar)
    }

    /**
     * The observer's velocity in TEME, in km/s: rigid rotation with the Earth, `ω × r`.
     *
     * Only range rate needs this. It is a few hundred m/s against a satellite's ~7.7 km/s, so
     * omitting it would be a several-percent error in a quantity that is otherwise exact.
     */
    fun observerVelocityTeme(
        time: Instant,
        observer: LatLong,
    ): Vector3 {
        val position = observerPositionTeme(time, observer)
        return Vector3(
            -EARTH_ROTATION_RAD_PER_SEC * position.y,
            EARTH_ROTATION_RAD_PER_SEC * position.x,
            0.0,
        )
    }

    /**
     * The satellite as seen from [observer], with the direction left in TEME.
     *
     * This is what the pass predictor wants: pass [TopocentricPosition.raDec] straight to
     * [altitudeDeg] and [azimuthDeg], whose hour angles come from the same
     * [meanSiderealTimeDeg], and the two agree to arcseconds with no conversion in between.
     * **Do not draw this direction** — it is of-date, not J2000; use [topocentricJ2000].
     */
    fun topocentricTeme(
        state: TemeState,
        time: Instant,
        observer: LatLong,
    ): TopocentricPosition {
        val offset = state.positionKm - observerPositionTeme(time, observer)
        val relativeVelocity = state.velocityKmPerSec - observerVelocityTeme(time, observer)
        val range = offset.length
        return TopocentricPosition(
            raDec = RaDec.fromGeocentricVector(offset),
            rangeKm = range,
            rangeRateKmPerSec = if (range > 0.0) (relativeVelocity dot offset) / range else 0.0,
            frame = ReferenceFrame.TEME,
        )
    }

    /**
     * The satellite as seen from [observer], with the direction rotated into J2000 — the frame the
     * star catalog and everything else the map draws are in.
     *
     * TEME differs from J2000 by the precession accumulated since the epoch: about 0.365° in 2026
     * and growing, more than half a Moon-width, so this rotation is not optional. Nutation and the
     * equation of the equinoxes are together under 0.02° and stay omitted, matching what
     * [Precession] already documents for its other callers.
     */
    fun topocentricJ2000(
        state: TemeState,
        time: Instant,
        observer: LatLong,
    ): TopocentricPosition {
        val teme = topocentricTeme(state, time, observer)
        return teme.copy(
            raDec =
                RaDec.fromGeocentricVector(
                    teme.raDec.toGeocentricVector().precessedToJ2000(time),
                ),
            frame = ReferenceFrame.J2000,
        )
    }
}
