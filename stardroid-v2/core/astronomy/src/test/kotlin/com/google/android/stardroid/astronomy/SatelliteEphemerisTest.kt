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
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.angularSeparationDeg
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.cos
import kotlin.math.sin

/**
 * The frame chain, checked end to end.
 *
 * [Sgp4Test] validates the propagator, but Vallado's vectors are **ECI only** — they cannot catch
 * a wrong observer model or a botched frame conversion, which is where SGP4 ports actually fail in
 * practice. So this suite pins the *chain*: a frozen ISS element set, propagated to a known pass,
 * with altitude and azimuth asserted against an independent reference.
 *
 * That reference is Skyfield, which implements the full IAU 2000/2006 chain — precession,
 * nutation, polar motion, and a geodetic observer. Agreeing with it to a fraction of a degree
 * means the simplifications this app deliberately makes (no nutation, low-precision GMST, sea
 * level) really do cost what the design says they cost.
 */
class SatelliteEphemerisTest {
    /**
     * A **frozen golden fixture**, not live data. It propagates from a fixed epoch to fixed times
     * and the expected values below were computed against exactly these lines.
     *
     * Do not "helpfully" refresh it to a current element set. Its staleness is the point — a TLE
     * that never changes is what makes the assertions reproducible. A fresh one would invalidate
     * every expected value in this file.
     */
    private val issTle =
        Tle.parse(
            line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
            line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
            name = "ISS (ZARYA)",
        )

    /**
     * Catalog 28057 from Vallado's `SGP4-VER.TLE`: sun-synchronous, 98.4° retrograde, near-circular
     * — the shape most of the `visual` group takes, and geometrically nothing like the ISS. A real
     * published element set, and frozen for the same reason [issTle] is.
     */
    private val sunSynchronousTle =
        Tle.parse(
            line1 = "1 28057U 03049A   06177.78615833  .00000060  00000-0  35940-4 0  1836",
            line2 = "2 28057  98.4283 247.6961 0000884  88.1964 271.9322 14.35478080140550",
        )

    private val london = LatLong(51.5074, -0.1278)

    @Test
    fun `a moderate ISS pass over London matches Skyfield`() {
        // Culminating at 45.5°, so azimuth is well conditioned throughout — the ordinary case the
        // pass predictor will spend its time on.
        assertMatchesSkyfield(
            tle = issTle,
            observer = london,
            samples =
                listOf(
                    Sample("2026-08-15T06:53:40.519Z", 5.594479, 233.080673, 1803.0603),
                    Sample("2026-08-15T06:55:40.519Z", 19.787734, 221.712393, 1029.5578),
                    Sample("2026-08-15T06:57:40.519Z", 45.511317, 156.292862, 570.7024),
                    Sample("2026-08-15T06:59:40.519Z", 19.904797, 90.803655, 1029.7331),
                    Sample("2026-08-15T07:01:40.519Z", 5.725578, 79.457608, 1803.0818),
                ),
        )
    }

    @Test
    fun `a near-zenith ISS pass over London matches Skyfield`() {
        // Culminating at 87.2°, the pass a user is most likely to be watching. Azimuth is not
        // asserted at the peak — see ZENITH_AZIMUTH_CUTOFF_DEG.
        assertMatchesSkyfield(
            tle = issTle,
            observer = london,
            samples =
                listOf(
                    Sample("2026-08-15T08:31:21.763Z", 12.464539, 266.195048, 1347.7679),
                    Sample("2026-08-15T08:32:51.763Z", 30.387294, 267.388223, 766.4232),
                    Sample("2026-08-15T08:34:21.763Z", 87.187657, 357.221866, 420.8057),
                    Sample("2026-08-15T08:35:51.763Z", 30.399439, 84.421695, 767.6082),
                    Sample("2026-08-15T08:37:21.763Z", 12.518369, 85.616340, 1348.9841),
                ),
        )
    }

    @Test
    fun `a southern-hemisphere pass over Sydney matches Skyfield`() {
        // Negative latitude, which is where a sign error in the ellipsoid's polar term would hide:
        // the WGS84 flattening enters through sin(latitude), so getting it wrong puts the observer
        // on the *opposite* side of the equator's plane and the London cases would never notice.
        // Azimuth also sweeps through south rather than north here, exercising the other half of
        // azimuthDeg's atan2 quadrants.
        assertMatchesSkyfield(
            tle = issTle,
            observer = LatLong(-33.8688, 151.2093),
            samples =
                listOf(
                    Sample("2026-08-15T07:35:49.387Z", 10.826530, 282.467140, 1474.3293),
                    Sample("2026-08-15T07:37:49.387Z", 26.895864, 252.204537, 864.2775),
                    Sample("2026-08-15T07:38:49.387Z", 32.504472, 217.369815, 755.1719),
                    Sample("2026-08-15T07:39:49.387Z", 26.898568, 182.678326, 867.3988),
                    Sample("2026-08-15T07:41:49.387Z", 10.972124, 152.597134, 1478.8778),
                ),
        )
    }

    @Test
    fun `an equatorial pass over Quito matches Skyfield`() {
        // Latitude ~0 and a western longitude. At the equator the ellipsoid correction is at one
        // extreme (the observer is furthest from the geocentre) and the sphere and ellipsoid agree
        // most closely, so this is the case that would still pass if the correction were dropped
        // entirely — included precisely so the WGS84 term is checked across its whole range rather
        // than at one latitude.
        assertMatchesSkyfield(
            tle = issTle,
            observer = LatLong(-0.1807, -78.4678),
            samples =
                listOf(
                    Sample("2026-08-15T08:09:06.992Z", 12.285681, 219.852397, 1352.5567),
                    Sample("2026-08-15T08:11:06.992Z", 41.468615, 228.736272, 605.6539),
                    Sample("2026-08-15T08:12:06.992Z", 75.735975, 305.490487, 427.9561),
                    Sample("2026-08-15T08:13:06.992Z", 41.335922, 21.944806, 605.1749),
                    Sample("2026-08-15T08:15:06.992Z", 12.110908, 30.886673, 1352.1224),
                ),
        )
    }

    @Test
    fun `a high-latitude pass of a polar satellite matches Skyfield`() {
        // Two gaps closed at once. Tromsø is inside the Arctic Circle, where the ellipsoid
        // correction is at its other extreme and the observer's rotation velocity nearly vanishes;
        // and catalog 28057 is sun-synchronous — 98.4° retrograde inclination, near-circular,
        // which is the shape of most of the `visual` group and a completely different geometry
        // from the ISS's 51.6° prograde orbit. The ISS never reaches 10° elevation from here at
        // all, so a polar satellite is also the only honest way to test this latitude.
        assertMatchesSkyfield(
            tle = sunSynchronousTle,
            observer = LatLong(69.6492, 18.9553),
            samples =
                listOf(
                    Sample("2006-06-27T12:01:40.422Z", 20.993593, 16.660988, 1699.3581),
                    Sample("2006-06-27T12:03:40.422Z", 37.385212, 346.915108, 1191.1522),
                    Sample("2006-06-27T12:04:40.422Z", 41.583196, 318.359910, 1110.8897),
                    Sample("2006-06-27T12:05:40.422Z", 37.365312, 289.795016, 1190.4943),
                    Sample("2006-06-27T12:07:40.422Z", 20.932881, 260.013921, 1697.9677),
                ),
        )
    }

    /**
     * Propagates [tle] to each sample's instant and checks altitude, azimuth and range against the
     * Skyfield-computed expectations.
     *
     * Azimuth is skipped above [ZENITH_AZIMUTH_CUTOFF_DEG], where it is genuinely ill conditioned
     * rather than merely imprecise — within a few degrees of the zenith a hundredth of a degree of
     * position swings the azimuth by degrees, so an assertion there would be testing the reference
     * implementation's rounding, not this one's correctness.
     */
    private fun assertMatchesSkyfield(
        tle: Tle,
        observer: LatLong,
        samples: List<Sample>,
    ) {
        val sgp4 = Sgp4(tle)
        for (sample in samples) {
            val state = checkNotNull(sgp4.propagateAt(sample.time))
            val position = SatelliteEphemeris.topocentricTeme(state, sample.time, observer)
            assertThat(position.altitudeDeg(sample.time, observer))
                .isWithin(ANGLE_TOLERANCE_DEG)
                .of(sample.altitudeDeg)
            assertThat(position.rangeKm).isWithin(RANGE_TOLERANCE_KM).of(sample.rangeKm)
            if (sample.altitudeDeg < ZENITH_AZIMUTH_CUTOFF_DEG) {
                assertThat(position.azimuthDeg(sample.time, observer))
                    .isWithin(ANGLE_TOLERANCE_DEG)
                    .of(sample.azimuthDeg)
            }
        }
    }

    @Test
    fun `the spherical-Earth observer used for the Moon would be degrees wrong here`() {
        // The design's single most important correctness claim, asserted rather than trusted:
        // KeplerianEphemeris places the observer at exactly one Earth radius, which costs
        // arcseconds for the Moon at 60 Earth radii and degrees for a satellite at 1.07. If this
        // ever stops failing, someone has quietly reintroduced the spherical shortcut.
        // Closest approach, where the observer's own displacement subtends the largest angle and
        // so the error is at its worst — which is also when the satellite is brightest and most
        // likely to be looked at.
        val time = Instant.parse("2026-08-15T06:57:40.519Z")
        val state = checkNotNull(Sgp4(issTle).propagateAt(time))

        val ellipsoidal = SatelliteEphemeris.observerPositionTeme(time, london)
        val spherical = sphericalObserverPositionTeme(time, london)
        // At London's latitude the ellipsoid's surface is ~22 km inside a 6371 km sphere.
        assertThat(ellipsoidal.distanceTo(spherical)).isGreaterThan(15.0)

        val correct = RaDec.fromGeocentricVector(state.positionKm - ellipsoidal)
        val naive = RaDec.fromGeocentricVector(state.positionKm - spherical)
        // 1.94° on this pass — degrees, not the arcseconds the same shortcut costs the Moon, and
        // squarely inside the 1–3° band the design cites. The bound is loose because the exact
        // figure depends on how much of the 22 km lies across the line of sight rather than along
        // it; what is being pinned is the order of magnitude.
        assertThat(angularSeparationDeg(correct, naive)).isGreaterThan(1.5)
    }

    @Test
    fun `the observer sits on the WGS84 ellipsoid`() {
        val time = Instant.parse("2026-08-15T06:55:40.519Z")
        // Equatorial and polar radii, the two axes the ellipsoid is defined by.
        assertThat(SatelliteEphemeris.observerPositionTeme(time, LatLong(0.0, 0.0)).length)
            .isWithin(1e-6)
            .of(6378.137)
        assertThat(SatelliteEphemeris.observerPositionTeme(time, LatLong(90.0, 0.0)).length)
            .isWithin(1e-3)
            .of(6356.752)
    }

    @Test
    fun `the observer's velocity is Earth rotation and vanishes at the poles`() {
        val time = Instant.parse("2026-08-15T06:55:40.519Z")
        // 465 m/s eastward at the equator, the textbook figure.
        assertThat(SatelliteEphemeris.observerVelocityTeme(time, LatLong(0.0, 0.0)).length)
            .isWithin(0.001)
            .of(0.4651)
        assertThat(SatelliteEphemeris.observerVelocityTeme(time, LatLong(90.0, 0.0)).length)
            .isLessThan(1e-9)
    }

    @Test
    fun `range rate passes through zero at closest approach`() {
        // Culmination is where the satellite stops approaching and starts receding, so the sign
        // change pins that the observer's own rotation velocity is being subtracted correctly.
        val approaching = topocentricAt(Instant.parse("2026-08-15T06:55:40.519Z"))
        val receding = topocentricAt(Instant.parse("2026-08-15T06:59:40.519Z"))
        assertThat(approaching.rangeRateKmPerSec).isLessThan(-5.0)
        assertThat(receding.rangeRateKmPerSec).isGreaterThan(5.0)
    }

    @Test
    fun `the J2000 direction differs from TEME by the precession since J2000`() {
        // ~0.365° in 2026 — more than half a Moon-width, which is why the map layer cannot skip
        // this rotation. Range is unaffected: precession is a rigid rotation.
        val time = Instant.parse("2026-08-15T06:55:40.519Z")
        val state = checkNotNull(Sgp4(issTle).propagateAt(time))
        val teme = SatelliteEphemeris.topocentricTeme(state, time, london)
        val j2000 = SatelliteEphemeris.topocentricJ2000(state, time, london)

        assertThat(angularSeparationDeg(teme.raDec, j2000.raDec)).isWithin(0.02).of(0.365)
        assertThat(j2000.rangeKm).isWithin(1e-9).of(teme.rangeKm)
        assertThat(j2000.rangeRateKmPerSec).isWithin(1e-9).of(teme.rangeRateKmPerSec)

        assertThat(teme.frame).isEqualTo(ReferenceFrame.TEME)
        assertThat(j2000.frame).isEqualTo(ReferenceFrame.J2000)
    }

    @Test
    fun `computing horizontal coordinates from a J2000 position is refused`() {
        // The 0.365° above is exactly what a caller would silently absorb by reaching for the map
        // layer's position when computing a pass. Both frames yield plausible right ascensions, so
        // nothing but this guard would catch it — which is why TopocentricPosition carries its
        // frame rather than documenting it.
        val time = Instant.parse("2026-08-15T06:55:40.519Z")
        val state = checkNotNull(Sgp4(issTle).propagateAt(time))
        val j2000 = SatelliteEphemeris.topocentricJ2000(state, time, london)

        assertThrows<IllegalArgumentException> { j2000.altitudeDeg(time, london) }
        assertThrows<IllegalArgumentException> { j2000.azimuthDeg(time, london) }

        // The TEME position, the one the pass predictor uses, is accepted.
        val teme = SatelliteEphemeris.topocentricTeme(state, time, london)
        assertThat(teme.altitudeDeg(time, london)).isWithin(ANGLE_TOLERANCE_DEG).of(19.787734)
    }

    @Test
    fun `the frozen fixture parses as the ISS`() {
        assertThat(issTle.noradId).isEqualTo(25544)
        assertThat(issTle.name).isEqualTo("ISS (ZARYA)")
        assertThat(issTle.isDeepSpace).isFalse()
        // ~93 minutes, the period every "how long until it comes back?" answer rests on.
        assertThat(issTle.periodMinutes).isWithin(0.5).of(92.9)
    }

    private fun topocentricAt(time: Instant): TopocentricPosition {
        val state = checkNotNull(Sgp4(issTle).propagateAt(time))
        return SatelliteEphemeris.topocentricTeme(state, time, london)
    }

    private fun topocentricAt(isoTime: String) = topocentricAt(Instant.parse(isoTime))

    /**
     * [KeplerianEphemeris.topocentricLunarPosition]'s observer model, reproduced here purely so
     * the test above can show what it would cost a satellite. Not for use.
     */
    private fun sphericalObserverPositionTeme(
        time: Instant,
        observer: LatLong,
    ): Vector3 {
        val lmstRad = meanSiderealTimeDeg(time, observer.longitudeDeg) * DEG_TO_RAD
        val latRad = observer.latitudeDeg * DEG_TO_RAD
        return Vector3(
            cos(latRad) * cos(lmstRad),
            cos(latRad) * sin(lmstRad),
            sin(latRad),
        ) * MEAN_EARTH_RADIUS_KM
    }

    private data class Sample(
        val isoTime: String,
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val rangeKm: Double,
    ) {
        val time: Instant get() = Instant.parse(isoTime)
    }

    private companion object {
        const val DEG_TO_RAD = kotlin.math.PI / 180.0
        const val MEAN_EARTH_RADIUS_KM = 6371.0

        /**
         * The chain omits nutation and the equation of the equinoxes (together under 0.02°), uses
         * the low-precision GMST series (~0.0004°), and assumes sea level. Skyfield includes all
         * of it, so this bound is the total budget of those omissions with room to spare — and it
         * is far tighter than the ~0.5° the design requires, because at 570 km even a small frame
         * slip would show up here immediately.
         */
        const val ANGLE_TOLERANCE_DEG = 0.05

        /** Range is barely frame-sensitive; it moves only with the observer's assumed elevation. */
        const val RANGE_TOLERANCE_KM = 0.5

        /** Above this altitude azimuth is ill conditioned — see [assertMatchesSkyfield]. */
        const val ZENITH_AZIMUTH_CUTOFF_DEG = 80.0
    }
}
