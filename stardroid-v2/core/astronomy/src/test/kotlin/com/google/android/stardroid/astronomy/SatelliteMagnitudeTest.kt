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

/**
 * The shadow test and the brightness model.
 *
 * The shadow geometry is exercised with **constructed** positions rather than a real pass, and
 * deliberately so: a satellite entering the Earth's shadow mid-pass is the interesting case, and
 * it does not occur in the window the frozen ISS fixture is valid for. Building the geometry
 * directly tests the boundary precisely, which sampling a real pass would not.
 */
class SatelliteMagnitudeTest {
    private val sunTowardPositiveX = Vector3(1.0, 0.0, 0.0)

    /** A typical low-Earth-orbit radius: 6371 km mean Earth radius plus ~400 km. */
    private val orbitRadiusKm = 6771.0

    @Test
    fun `a satellite on the sunward side is always lit`() {
        // Anywhere with a positive component along the Sun direction is in front of the Earth's
        // shadow-casting disc, whatever its offset from the axis.
        assertThat(
            SatelliteMagnitude.isSunlit(Vector3(orbitRadiusKm, 0.0, 0.0), sunTowardPositiveX),
        )
            .isTrue()
        assertThat(
            SatelliteMagnitude.isSunlit(Vector3(100.0, orbitRadiusKm, 0.0), sunTowardPositiveX),
        )
            .isTrue()
    }

    @Test
    fun `a satellite directly behind the Earth is in shadow`() {
        // Anti-sunward and on the axis: squarely inside the shadow cylinder. This is the geometry
        // that makes a satellite vanish from a clear sky.
        assertThat(
            SatelliteMagnitude.isSunlit(Vector3(-orbitRadiusKm, 0.0, 0.0), sunTowardPositiveX),
        ).isFalse()
    }

    @Test
    fun `a satellite behind the Earth but outside the shadow cylinder is lit`() {
        // Anti-sunward, but far enough off the Earth-Sun axis to be clear of the shadow — the
        // situation during a terminator-hugging pass, which is exactly when satellites are visible.
        val offAxis = Vector3(-3000.0, orbitRadiusKm, 0.0)
        assertThat(SatelliteMagnitude.isSunlit(offAxis, sunTowardPositiveX)).isTrue()
    }

    @Test
    fun `the shadow boundary sits at the Earth's radius plus the atmospheric margin`() {
        // Just inside and just outside the cylinder, anti-sunward. The margin stands in for the
        // penumbra and for refraction; without it a bare cylinder overestimates lit time by 10-20
        // seconds at shadow entry.
        val justInside = Vector3(-orbitRadiusKm, 6371.0 + 30.0 - 5.0, 0.0)
        val justOutside = Vector3(-orbitRadiusKm, 6371.0 + 30.0 + 5.0, 0.0)
        assertThat(SatelliteMagnitude.isSunlit(justInside, sunTowardPositiveX)).isFalse()
        assertThat(SatelliteMagnitude.isSunlit(justOutside, sunTowardPositiveX)).isTrue()
    }

    @Test
    fun `standard magnitude is recovered at its own definition point`() {
        // The standard magnitude is *defined* as brightness at 1000 km and 90° phase, so feeding
        // those back in must return it. This pins the -15.75 constant: if it drifts, every
        // magnitude the app displays shifts by the same amount and nothing else would notice.
        assertThat(
            SatelliteMagnitude.apparentMagnitude(
                standardMagnitude = SatelliteMagnitude.ISS_STANDARD_MAGNITUDE,
                rangeKm = 1000.0,
                phaseAngleDeg = 90.0,
            ),
        ).isWithin(0.01).of(SatelliteMagnitude.ISS_STANDARD_MAGNITUDE)
    }

    @Test
    fun `a high overhead ISS pass is around magnitude minus three`() {
        // The figure the design cites and the one observers report: a well-placed ISS pass is
        // brighter than every star and planet, second only to the Moon.
        val overhead =
            SatelliteMagnitude.apparentMagnitude(
                standardMagnitude = SatelliteMagnitude.ISS_STANDARD_MAGNITUDE,
                rangeKm = 420.0,
                phaseAngleDeg = 40.0,
            )
        assertThat(overhead).isGreaterThan(-4.5)
        assertThat(overhead).isLessThan(-3.0)
    }

    @Test
    fun `closer is brighter, and fuller phase is brighter`() {
        val far =
            SatelliteMagnitude.apparentMagnitude(-1.3, rangeKm = 1500.0, phaseAngleDeg = 60.0)
        val near =
            SatelliteMagnitude.apparentMagnitude(-1.3, rangeKm = 500.0, phaseAngleDeg = 60.0)
        // Magnitudes run backwards: smaller is brighter.
        assertThat(near).isLessThan(far)
        // Inverse square in flux is 5 log10 of the distance ratio in magnitudes: 3x closer is
        // about 2.4 magnitudes brighter.
        assertThat(far - near).isWithin(0.05).of(2.385)

        val crescent =
            SatelliteMagnitude.apparentMagnitude(-1.3, rangeKm = 800.0, phaseAngleDeg = 150.0)
        val full =
            SatelliteMagnitude.apparentMagnitude(-1.3, rangeKm = 800.0, phaseAngleDeg = 10.0)
        assertThat(full).isLessThan(crescent)
    }

    @Test
    fun `a phase angle of nearly zero does not produce an infinite magnitude`() {
        // Physically unobservable (the satellite would be between the observer and the Sun), but
        // the search samples blindly and must not emit a NaN or an infinity into a data class the
        // UI will format.
        val magnitude =
            SatelliteMagnitude.apparentMagnitude(
                -1.3,
                rangeKm = 500.0,
                phaseAngleDeg = 180.0,
            )
        assertThat(magnitude.isFinite()).isTrue()
    }

    @Test
    fun `the Sun direction is returned in TEME, not J2000`() {
        // The whole correctness of the shadow test rests on this: MeeusEphemeris returns J2000
        // since D84, and SGP4 emits TEME, so one has to be rotated to meet the other. If this
        // ever silently starts returning J2000, every dot product above carries a 0.365° error.
        val time = Instant.parse("2026-08-15T06:55:40.519Z")
        val teme = SatelliteMagnitude.sunDirectionTeme(time)
        val j2000 =
            MeeusEphemeris.geocentricPosition(SolarSystemBody.SUN, time).toGeocentricVector()

        assertThat(teme.length).isWithin(1e-9).of(1.0)
        // They differ by exactly the precession angle — small, but not zero.
        val separationDeg =
            angularSeparationDeg(
                RaDec.fromGeocentricVector(teme),
                RaDec.fromGeocentricVector(j2000),
            )
        assertThat(separationDeg).isWithin(0.02).of(0.365)
    }

    @Test
    fun `phase angle is zero when the observer looks along the Sun direction`() {
        // Observer, satellite and Sun collinear with the satellite "full": the satellite is
        // between the Sun and the observer's side of it, so the phase angle collapses to zero.
        val satellite = Vector3(orbitRadiusKm, 0.0, 0.0)
        val observer = Vector3(orbitRadiusKm + 400.0, 0.0, 0.0)
        assertThat(
            SatelliteMagnitude.phaseAngleDeg(satellite, observer, sunTowardPositiveX),
        ).isWithin(1e-6).of(0.0)

        // Observer on the far side: fully backlit.
        val behind = Vector3(orbitRadiusKm - 400.0, 0.0, 0.0)
        assertThat(
            SatelliteMagnitude.phaseAngleDeg(satellite, behind, sunTowardPositiveX),
        ).isWithin(1e-6).of(180.0)
    }
}
