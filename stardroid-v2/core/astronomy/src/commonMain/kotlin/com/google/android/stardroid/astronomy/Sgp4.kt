/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.TWO_PI
import com.google.android.stardroid.math.Vector3
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A satellite's position and velocity in the **TEME** frame (True Equator, Mean Equinox) — the
 * idiosyncratic frame SGP4 is defined in, which is neither J2000 nor GCRF.
 *
 * Nothing should draw or compare against these numbers directly; [SatelliteEphemeris] carries
 * them the rest of the way. See docs/design/satellite-tracking.md §"Frame chain".
 */
data class TemeState(val positionKm: Vector3, val velocityKmPerSec: Vector3)

/**
 * The SGP4 orbital propagator: turns a [Tle]'s mean elements into a position and velocity at an
 * arbitrary time.
 *
 * This is the near-earth half of the model in Spacetrack Report #3, in the corrected form
 * published by Vallado et al., *Revisiting Spacetrack Report #3* (AIAA 2006-6753). **SDP4, the
 * deep-space half, is deliberately absent** — see [Tle.isDeepSpace] for why running SGP4 without
 * it must be prevented rather than merely discouraged. Construction fails for a deep-space set.
 *
 * The model is inseparable from its own constants: it is a fit whose coefficients were derived
 * against WGS-72, so WGS-72 is what it uses regardless of what the rest of the app assumes.
 * Substituting WGS-84 here would silently degrade it rather than improve it.
 *
 * Instances are immutable and cheap to reuse. Element-set-dependent terms are computed once during
 * construction, so a repeated [propagate] over a long time window — which is what pass prediction
 * does, thousands of times — costs only the per-step arithmetic.
 *
 * The variable names below stay close to the published algorithm rather than being expanded into
 * prose. Several hundred lines of unexplained coefficients cannot be renamed into clarity, and a
 * reader checking this against the paper needs the names to match; the surrounding comments carry
 * the meaning instead.
 */
class Sgp4(val tle: Tle) {
    init {
        require(tle.meanMotionRadPerMin > 0.0) {
            "Satellite ${tle.noradId} has a non-positive mean motion; not an orbit"
        }
        require(!tle.isDeepSpace) {
            "SGP4 cannot propagate the deep-space element set for ${tle.noradId} " +
                "(period ${tle.periodMinutes} min); SDP4 is out of scope"
        }
    }

    // --- Element-set-dependent constants, computed once (the reference's `sgp4init`). ---

    private val noUnkozai: Double
    private val eta: Double
    private val con41: Double
    private val x1mth2: Double
    private val x7thm1: Double
    private val mdot: Double
    private val argpdot: Double
    private val nodedot: Double
    private val nodecf: Double
    private val cc1: Double
    private val cc4: Double
    private val cc5: Double
    private val omgcof: Double
    private val xmcof: Double
    private val delmo: Double
    private val sinmao: Double
    private val aycof: Double
    private val xlcof: Double
    private val t2cof: Double
    private val t3cof: Double
    private val t4cof: Double
    private val t5cof: Double
    private val d2: Double
    private val d3: Double
    private val d4: Double

    /**
     * True when perigee is below 220 km, where the model drops its higher-order drag terms. Naive
     * ports often miss this branch, which is why catalog 22312 is one of the verification cases;
     * exposed so that test can assert it is genuinely taking the branch it claims to cover.
     */
    internal val usesSimplifiedDrag: Boolean

    init {
        val ecco = tle.eccentricity
        val inclo = tle.inclinationRad
        val argpo = tle.argOfPerigeeRad
        val mo = tle.meanAnomalyRad

        val cosio = cos(inclo)
        val cosio2 = cosio * cosio
        val cosio4 = cosio2 * cosio2
        val sinio = sin(inclo)
        val omeosq = 1.0 - ecco * ecco
        val rteosq = sqrt(omeosq)

        // Recover the original mean motion: the value in the element set has Kozai's secular J2
        // correction folded in, and the propagator needs it taken back out. Two passes of the
        // series converge to well past double precision.
        val ak = (XKE / tle.meanMotionRadPerMin).pow(TWO_THIRDS)
        val d1 = 0.75 * J2 * (3.0 * cosio2 - 1.0) / (rteosq * omeosq)
        var del = d1 / (ak * ak)
        val adel = ak * (1.0 - del * del - del * (1.0 / 3.0 + 134.0 * del * del / 81.0))
        del = d1 / (adel * adel)
        noUnkozai = tle.meanMotionRadPerMin / (1.0 + del)

        val ao = (XKE / noUnkozai).pow(TWO_THIRDS)
        val po = ao * omeosq
        val posq = po * po
        val rp = ao * (1.0 - ecco)
        val con42 = 1.0 - 5.0 * cosio2
        con41 = -con42 - cosio2 - cosio2
        x1mth2 = 1.0 - cosio2
        x7thm1 = 7.0 * cosio2 - 1.0

        // Atmospheric-density fit. The reference altitude drops as perigee does, and below 98 km
        // the fit is pinned rather than extrapolated.
        val perigeeKm = (rp - 1.0) * SGP4_EARTH_RADIUS_KM
        var sfour = S_ATMOSPHERE
        var qzms24 = QZMS2T
        if (perigeeKm < 156.0) {
            sfour = if (perigeeKm < 98.0) 20.0 else perigeeKm - 78.0
            qzms24 = ((120.0 - sfour) / SGP4_EARTH_RADIUS_KM).pow(4.0)
            sfour = sfour / SGP4_EARTH_RADIUS_KM + 1.0
        }

        val pinvsq = 1.0 / posq
        val tsi = 1.0 / (ao - sfour)
        eta = ao * ecco * tsi
        val etasq = eta * eta
        val eeta = ecco * eta
        val psisq = abs(1.0 - etasq)
        val coef = qzms24 * tsi.pow(4.0)
        val coef1 = coef / psisq.pow(3.5)

        val cc2 =
            coef1 * noUnkozai * (
                ao * (1.0 + 1.5 * etasq + eeta * (4.0 + etasq)) +
                    0.375 * J2 * tsi / psisq * con41 * (8.0 + 3.0 * etasq * (8.0 + etasq))
            )
        cc1 = tle.bstar * cc2
        val cc3 =
            if (ecco > 1.0e-4) {
                -2.0 * coef * tsi * J3_OVER_J2 * noUnkozai * sinio / ecco
            } else {
                0.0
            }
        cc4 =
            2.0 * noUnkozai * coef1 * ao * omeosq * (
                eta * (2.0 + 0.5 * etasq) + ecco * (0.5 + 2.0 * etasq) -
                    J2 * tsi / (ao * psisq) * (
                        -3.0 * con41 *
                            (1.0 - 2.0 * eeta + etasq * (1.5 - 0.5 * eeta)) +
                            0.75 * x1mth2 *
                            (2.0 * etasq - eeta * (1.0 + etasq)) * cos(2.0 * argpo)
                    )
            )
        cc5 = 2.0 * coef1 * ao * omeosq * (1.0 + 2.75 * (etasq + eeta) + eeta * etasq)

        // Secular rates from the J2/J4 zonal harmonics.
        val temp1 = 1.5 * J2 * pinvsq * noUnkozai
        val temp2 = 0.5 * temp1 * J2 * pinvsq
        val temp3 = -0.46875 * J4 * pinvsq * pinvsq * noUnkozai
        mdot =
            noUnkozai + 0.5 * temp1 * rteosq * con41 +
            0.0625 * temp2 * rteosq * (13.0 - 78.0 * cosio2 + 137.0 * cosio4)
        argpdot =
            -0.5 * temp1 * con42 +
            0.0625 * temp2 * (7.0 - 114.0 * cosio2 + 395.0 * cosio4) +
            temp3 * (3.0 - 36.0 * cosio2 + 49.0 * cosio4)
        val xhdot1 = -temp1 * cosio
        nodedot =
            xhdot1 +
            (0.5 * temp2 * (4.0 - 19.0 * cosio2) + 2.0 * temp3 * (3.0 - 7.0 * cosio2)) * cosio

        omgcof = tle.bstar * cc3 * cos(argpo)
        xmcof = if (ecco > 1.0e-4) -TWO_THIRDS * coef * tle.bstar / eeta else 0.0
        nodecf = 3.5 * omeosq * xhdot1 * cc1
        t2cof = 1.5 * cc1

        // Guard against the polar singularity at exactly 180° inclination, where the long-period
        // term divides by 1 + cos i.
        val cosioPlusOne = 1.0 + cosio
        val divisor =
            if (abs(cosioPlusOne) > POLAR_SINGULARITY_GUARD) {
                cosioPlusOne
            } else {
                POLAR_SINGULARITY_GUARD
            }
        xlcof = -0.25 * J3_OVER_J2 * sinio * (3.0 + 5.0 * cosio) / divisor
        aycof = -0.5 * J3_OVER_J2 * sinio

        val delmoTemp = 1.0 + eta * cos(mo)
        delmo = delmoTemp * delmoTemp * delmoTemp
        sinmao = sin(mo)

        usesSimplifiedDrag = rp < (220.0 / SGP4_EARTH_RADIUS_KM + 1.0)
        if (usesSimplifiedDrag) {
            d2 = 0.0
            d3 = 0.0
            d4 = 0.0
            t3cof = 0.0
            t4cof = 0.0
            t5cof = 0.0
        } else {
            val cc1sq = cc1 * cc1
            d2 = 4.0 * ao * tsi * cc1sq
            val temp = d2 * tsi * cc1 / 3.0
            d3 = (17.0 * ao + sfour) * temp
            d4 = 0.5 * temp * ao * tsi * (221.0 * ao + 31.0 * sfour) * cc1
            t3cof = d2 + 2.0 * cc1sq
            t4cof = 0.25 * (3.0 * d3 + cc1 * (12.0 * d2 + 10.0 * cc1sq))
            t5cof =
                0.2 * (
                    3.0 * d4 + 12.0 * cc1 * d3 + 6.0 * d2 * d2 +
                        15.0 * cc1sq * (2.0 * d2 + cc1sq)
                )
        }
    }

    /**
     * The satellite's TEME position and velocity [minutesSinceEpoch] minutes after
     * [Tle.epoch]. Negative values propagate backwards, which the model supports equally.
     *
     * Returns `null` when the model has broken down rather than merely become inaccurate: the
     * eccentricity has left `[0, 1)`, the semi-latus rectum has gone negative, or the satellite
     * has descended below one Earth radius — all of which mean decay or a bad element set, and
     * none of which have a position worth reporting. Callers scanning a window should skip such
     * steps.
     */
    fun propagate(minutesSinceEpoch: Double): TemeState? {
        val t = minutesSinceEpoch

        // --- Secular update: gravity, then drag. ---
        val xmdf = tle.meanAnomalyRad + mdot * t
        val argpdf = tle.argOfPerigeeRad + argpdot * t
        val nodedf = tle.raanRad + nodedot * t
        val t2 = t * t
        val nodem = nodedf + nodecf * t2

        var argpm = argpdf
        var mm = xmdf
        var tempa = 1.0 - cc1 * t
        var tempe = tle.bstar * cc4 * t
        var templ = t2cof * t2

        if (!usesSimplifiedDrag) {
            val delomg = omgcof * t
            val delmTemp = 1.0 + eta * cos(xmdf)
            val delm = xmcof * (delmTemp * delmTemp * delmTemp - delmo)
            val temp = delomg + delm
            mm = xmdf + temp
            argpm = argpdf - temp
            val t3 = t2 * t
            val t4 = t3 * t
            tempa = tempa - d2 * t2 - d3 * t3 - d4 * t4
            tempe += tle.bstar * cc5 * (sin(mm) - sinmao)
            templ += t3cof * t3 + t4 * (t4cof + t * t5cof)
        }

        val am = (XKE / noUnkozai).pow(TWO_THIRDS) * tempa * tempa
        val nm = XKE / am.pow(1.5)

        var em = tle.eccentricity - tempe
        if (em >= 1.0 || em < -0.001) return null
        if (em < 1.0e-6) em = 1.0e-6

        mm += noUnkozai * templ
        val xlm = (mm + argpm + nodem) % TWO_PI
        val nodep = nodem % TWO_PI
        val argpp = argpm % TWO_PI
        val mp = (xlm - argpp - nodep) % TWO_PI

        val xincp = tle.inclinationRad
        val sinip = sin(xincp)
        val cosip = cos(xincp)

        // --- Long-period periodics, from the J3 tesseral term. ---
        val axnl = em * cos(argpp)
        val longPeriod = 1.0 / (am * (1.0 - em * em))
        val aynl = em * sin(argpp) + longPeriod * aycof
        val xl = mp + argpp + nodep + longPeriod * xlcof * axnl

        // --- Kepler's equation, by Newton-Raphson with a damped step. ---
        val u = (xl - nodep) % TWO_PI
        var eo1 = u
        var delta = 1.0
        var iterations = 0
        var sineo1 = 0.0
        var coseo1 = 0.0
        while (abs(delta) >= KEPLER_TOLERANCE && iterations < KEPLER_MAX_ITERATIONS) {
            sineo1 = sin(eo1)
            coseo1 = cos(eo1)
            delta =
                (u - aynl * coseo1 + axnl * sineo1 - eo1) /
                (1.0 - coseo1 * axnl - sineo1 * aynl)
            // The damping keeps a near-parabolic step from throwing the iteration off entirely.
            delta = delta.coerceIn(-KEPLER_MAX_STEP, KEPLER_MAX_STEP)
            eo1 += delta
            iterations++
        }

        // --- Short-period periodics and the osculating orbit. ---
        val ecose = axnl * coseo1 + aynl * sineo1
        val esine = axnl * sineo1 - aynl * coseo1
        val el2 = axnl * axnl + aynl * aynl
        val pl = am * (1.0 - el2)
        if (pl < 0.0) return null

        val rl = am * (1.0 - ecose)
        val rdotl = sqrt(am) * esine / rl
        val rvdotl = sqrt(pl) / rl
        val betal = sqrt(1.0 - el2)
        val temp = esine / (1.0 + betal)
        val sinu = am / rl * (sineo1 - aynl - axnl * temp)
        val cosu = am / rl * (coseo1 - axnl + aynl * temp)
        val sin2u = (cosu + cosu) * sinu
        val cos2u = 1.0 - 2.0 * sinu * sinu

        val temp1 = 0.5 * J2 / pl
        val temp2 = temp1 / pl

        val mrt = rl * (1.0 - 1.5 * temp2 * betal * con41) + 0.5 * temp1 * x1mth2 * cos2u
        if (mrt < 1.0) return null

        val su = atan2(sinu, cosu) - 0.25 * temp2 * x7thm1 * sin2u
        val xnode = nodep + 1.5 * temp2 * cosip * sin2u
        val xinc = xincp + 1.5 * temp2 * cosip * sinip * cos2u
        val mvt = rdotl - nm * temp1 * x1mth2 * sin2u / XKE
        val rvdot = rvdotl + nm * temp1 * (x1mth2 * cos2u + 1.5 * con41) / XKE

        // --- Orientation vectors: the unit radial and its in-plane transverse partner. ---
        val sinsu = sin(su)
        val cossu = cos(su)
        val snod = sin(xnode)
        val cnod = cos(xnode)
        val sini = sin(xinc)
        val cosi = cos(xinc)
        val xmx = -snod * cosi
        val xmy = cnod * cosi
        val radial =
            Vector3(
                xmx * sinsu + cnod * cossu,
                xmy * sinsu + snod * cossu,
                sini * sinsu,
            )
        val transverse =
            Vector3(
                xmx * cossu - cnod * sinsu,
                xmy * cossu - snod * sinsu,
                sini * cossu,
            )

        return TemeState(
            positionKm = radial * (mrt * SGP4_EARTH_RADIUS_KM),
            velocityKmPerSec =
                (radial * mvt + transverse * rvdot) * KM_PER_SEC_PER_CANONICAL,
        )
    }

    /** [propagate] for an absolute instant, which is what every caller outside tests has. */
    fun propagateAt(time: Instant): TemeState? = propagate(tle.minutesSinceEpoch(time))

    private companion object {
        /**
         * WGS-72, not WGS-84. SGP4's coefficients were fitted against this geoid and the model is
         * only self-consistent with it; see the class KDoc.
         */
        const val SGP4_EARTH_RADIUS_KM = 6378.135
        const val MU_KM3_PER_SEC2 = 398600.8
        const val J2 = 0.001082616
        const val J3 = -0.00000253881
        const val J4 = -0.00000165597
        val J3_OVER_J2 = J3 / J2

        /** Earth's gravitational constant in the model's canonical units (radians per minute). */
        val XKE = 60.0 / sqrt(SGP4_EARTH_RADIUS_KM.pow(3.0) / MU_KM3_PER_SEC2)

        /** Canonical speed (Earth radii per canonical time unit) to km/s. */
        val KM_PER_SEC_PER_CANONICAL = SGP4_EARTH_RADIUS_KM * XKE / 60.0

        const val TWO_THIRDS = 2.0 / 3.0

        /** Atmospheric-model reference altitude of 78 km, in Earth radii above the surface. */
        val S_ATMOSPHERE = 78.0 / SGP4_EARTH_RADIUS_KM + 1.0

        /** The density fit's upper bound of 120 km, to the fourth power. */
        val QZMS2T = ((120.0 - 78.0) / SGP4_EARTH_RADIUS_KM).pow(4.0)

        const val POLAR_SINGULARITY_GUARD = 1.5e-12
        const val KEPLER_TOLERANCE = 1.0e-12
        const val KEPLER_MAX_ITERATIONS = 10
        const val KEPLER_MAX_STEP = 0.95
    }
}
