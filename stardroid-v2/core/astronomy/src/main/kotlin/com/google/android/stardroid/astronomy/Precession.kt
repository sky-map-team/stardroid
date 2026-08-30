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
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.Vector3
import kotlinx.datetime.Instant
import kotlin.math.cos
import kotlin.math.sin

/**
 * Precession of the equinoxes: the rigid rotation between the J2000 mean equatorial frame and the
 * mean equator and equinox of another date.
 *
 * **Sky Map v2 draws a J2000 sky.** The catalog ships J2000 positions
 * ([com.google.android.stardroid.catalog.CatalogModel]), [orbitalElementsFor] carries J2000
 * elements, and [toEquatorialCoordinates] rotates by the J2000 obliquity. Analytic series such as
 * those in [MeeusEphemeris], by contrast, natively produce coordinates referred to the equinox *of
 * date* — the convention almanacs publish. Mixing the two costs the precession angle between them:
 * about 0.365° in 2026, growing 0.014°/year, which is larger than the Sun's own disc.
 *
 * So of-date series results are rotated back to J2000 before they leave the ephemeris, using
 * [j2000FromDate]. See docs/design/ephemeris-accuracy.md for the full frame audit and the
 * remaining known inconsistency (sidereal time is of-date, so hour angles carry the same offset).
 *
 * Angles are the IAU 1976 (Lieske) equatorial precession polynomials, good to well under an
 * arcsecond across this app's 1800–2050 validity range.
 */
object Precession {
    /**
     * Rotation taking a vector in the J2000 mean equatorial frame to the mean equator and equinox
     * of [time].
     */
    fun dateFromJ2000(time: Instant): Matrix3 = rotationsFor(time).dateFromJ2000

    /**
     * Rotation taking a vector referred to the mean equator and equinox of [time] back to J2000 —
     * the transpose of [dateFromJ2000], which is orthogonal.
     */
    fun j2000FromDate(time: Instant): Matrix3 = rotationsFor(time).j2000FromDate

    /** Both rotations for one instant; see [cache] for why they are held together. */
    private class Rotations(
        val time: Instant,
        val dateFromJ2000: Matrix3,
        val j2000FromDate: Matrix3,
    )

    /**
     * Last instant's rotations, kept because callers ask for the same one repeatedly: every
     * precessed vector in a scene, and every sample of a satellite pass, shares a single time, yet
     * each call rebuilt the matrix from six trig calls and three cubic polynomials
     * (audit-2026-08 M1).
     *
     * One entry is enough — the access pattern is a burst per instant, not an interleaving — and a
     * miss only costs the computation that used to happen unconditionally. The holder is
     * immutable and published through a volatile reference, so a race between threads working at
     * different instants can waste a rebuild but never hand back a matrix for the wrong time.
     */
    @Volatile
    private var cache: Rotations? = null

    private fun rotationsFor(time: Instant): Rotations {
        cache?.let { if (it.time == time) return it }
        val dateFromJ2000 = computeDateFromJ2000(time)
        return Rotations(time, dateFromJ2000, dateFromJ2000.transposed())
            .also { cache = it }
    }

    private fun computeDateFromJ2000(time: Instant): Matrix3 {
        val t = time.julianCenturiesTerrestrialJ2000()
        val zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t).arcsecondsToRadians()
        val z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t).arcsecondsToRadians()
        val theta = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t).arcsecondsToRadians()

        val cosZeta = cos(zeta)
        val sinZeta = sin(zeta)
        val cosZ = cos(z)
        val sinZ = sin(z)
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)

        // Rz(-z) · Ry(theta) · Rz(-zeta), expanded (Meeus ch. 21; Explanatory Supplement 3.21).
        return Matrix3(
            cosZeta * cosTheta * cosZ - sinZeta * sinZ,
            -sinZeta * cosTheta * cosZ - cosZeta * sinZ,
            -sinTheta * cosZ,
            cosZeta * cosTheta * sinZ + sinZeta * cosZ,
            -sinZeta * cosTheta * sinZ + cosZeta * cosZ,
            -sinTheta * sinZ,
            cosZeta * sinTheta,
            -sinZeta * sinTheta,
            cosTheta,
        )
    }

    private fun Double.arcsecondsToRadians() = this / 3600.0 * DEGREES_TO_RADIANS
}

/**
 * This vector, referred to the mean equinox of [time], expressed in the J2000 frame the app
 * draws in.
 */
fun Vector3.precessedToJ2000(time: Instant): Vector3 = Precession.j2000FromDate(time) * this

/** This vector, referred to J2000, expressed against the mean equinox of [time]. */
fun Vector3.precessedFromJ2000(time: Instant): Vector3 = Precession.dateFromJ2000(time) * this
