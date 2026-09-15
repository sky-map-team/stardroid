/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.angularSeparationDeg
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class PrecessionTest {
    private val vernalEquinox = RaDec(0.0, 0.0).toGeocentricVector()

    private fun utc(
        year: Int,
        month: Int,
        day: Int,
    ): Instant = Instant.parse("$year-${pad(month)}-${pad(day)}T12:00:00Z")

    private fun pad(v: Int) = v.toString().padStart(2, '0')

    @Test
    fun isTheIdentityAtItsOwnEpoch() {
        // Not exactly J2000 (the polynomials take TT, and deltaTSeconds is ~64 s here), so the
        // residual is the rotation over ~64 seconds — well under a milliarcsecond.
        val rotated = vernalEquinox.precessedFromJ2000(utc(2000, 1, 1))
        assertThat(angularSeparationDeg(RaDec.fromGeocentricVector(rotated), RaDec(0.0, 0.0)))
            .isLessThan(1e-6)
    }

    @Test
    fun roundTripsThroughBothDirections() {
        val time = utc(2026, 8, 12)
        for (position in listOf(RaDec(0.0, 0.0), RaDec(83.6, 22.0), RaDec(201.3, -47.3))) {
            val original = position.toGeocentricVector()
            val there = original.precessedFromJ2000(time)
            val back = there.precessedToJ2000(time)
            assertThat(angularSeparationDeg(RaDec.fromGeocentricVector(back), position))
                .isLessThan(1e-9)
        }
    }

    @Test
    fun preservesAngularSeparationBetweenObjects() {
        // Precession is a rigid rotation of the whole sphere. This is exactly why applying it does
        // not disturb the eclipse timing in MeeusEphemerisTest: the Sun-Moon separation is
        // invariant under a rotation common to both.
        val time = utc(2040, 6, 1)
        val a = RaDec(10.0, 30.0)
        val b = RaDec(14.0, 26.0)
        val rotatedSeparation =
            angularSeparationDeg(
                RaDec.fromGeocentricVector(a.toGeocentricVector().precessedFromJ2000(time)),
                RaDec.fromGeocentricVector(b.toGeocentricVector().precessedFromJ2000(time)),
            )
        assertThat(rotatedSeparation).isWithin(1e-9).of(angularSeparationDeg(a, b))
    }

    @Test
    fun isOrthonormal() {
        val m = Precession.dateFromJ2000(utc(2035, 1, 1))
        val shouldBeIdentity = m * m.transposed()
        assertThat(shouldBeIdentity.xx).isWithin(1e-12).of(1.0)
        assertThat(shouldBeIdentity.yy).isWithin(1e-12).of(1.0)
        assertThat(shouldBeIdentity.zz).isWithin(1e-12).of(1.0)
        assertThat(shouldBeIdentity.xy).isWithin(1e-12).of(0.0)
        assertThat(shouldBeIdentity.xz).isWithin(1e-12).of(0.0)
        assertThat(shouldBeIdentity.yz).isWithin(1e-12).of(0.0)
        assertThat(m.determinant).isWithin(1e-12).of(1.0)
    }

    /**
     * General precession is 50.29″/year, so a point on the celestial equator at the equinox should
     * move ~1.396° per century. This is the magnitude check: an inverted sign or a factor slip
     * would still pass the structural tests above but fail here.
     */
    @Test
    fun movesAtTheGeneralPrecessionRate() {
        val perCenturyDeg =
            angularSeparationDeg(
                RaDec.fromGeocentricVector(vernalEquinox.precessedFromJ2000(utc(2100, 1, 1))),
                RaDec(0.0, 0.0),
            )
        assertThat(perCenturyDeg).isWithin(0.01).of(50.29 * 100.0 / 3600.0)
    }

    /**
     * Direction check: precession moves the equinox *westward* along the ecliptic, so a fixed star
     * gains right ascension over time. A J2000 position re-referred to a later equinox must
     * therefore come out at a larger RA, not a smaller one.
     */
    @Test
    fun movesTheEquinoxInTheRightDirection() {
        val star = RaDec(80.0, 0.0)
        val later =
            RaDec.fromGeocentricVector(
                star.toGeocentricVector().precessedFromJ2000(utc(2050, 1, 1)),
            )
        assertThat(later.raDeg).isGreaterThan(star.raDeg)
        // Half a century at 3.07 s of RA per year is roughly 0.64°.
        assertThat(later.raDeg - star.raDeg).isWithin(0.1).of(0.64)
    }

    /**
     * The celestial pole traces a cone about the ecliptic pole, so it moves at the general
     * precession rate projected onto that cone: 50.29″ · sin ε ≈ 20.0″/year, which is the θ
     * polynomial (2004.31″/century). Half a century is ~0.278° — deliberately *not* the 0.70° the
     * equinox itself travels in the same span, since confusing the two rates is the easy mistake.
     */
    @Test
    fun movesTheCelestialPoleAtTheThetaRate() {
        val poleOfDate = Vector3(0.0, 0.0, 1.0).precessedToJ2000(utc(2050, 1, 1))
        val offsetDeg =
            angularSeparationDeg(RaDec.fromGeocentricVector(poleOfDate), RaDec(0.0, 90.0))
        assertThat(offsetDeg).isWithin(0.005).of(0.278)
    }
}
