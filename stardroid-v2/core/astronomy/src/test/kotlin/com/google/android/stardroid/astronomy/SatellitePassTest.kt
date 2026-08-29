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
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

/**
 * Pass prediction against Skyfield, which finds passes by its own independent search over the full
 * IAU frame chain.
 *
 * The frozen ISS element set is the same one [SatelliteEphemerisTest] uses, and for the same
 * reason — see its KDoc before changing it. Both this code and the reference propagate *that* TLE,
 * so any disagreement is in the search or the frame chain, not in the orbit data.
 */
class SatellitePassTest {
    private val issTle =
        Tle.parse(
            line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
            line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
            name = "ISS (ZARYA)",
        )

    private val london = LatLong(51.5074, -0.1278)

    private fun search(observer: LatLong = london) = PassSearch(issTle, observer)

    @Test
    fun `finds the same passes over 48 hours that Skyfield does`() {
        // Skyfield's own find_events over the same window, above the same 10° cut.
        val passes = search().allPasses(Instant.parse("2026-08-15T12:00:00Z"), 48.hours)
        assertThat(passes).hasSize(9)

        val first = passes.first()
        assertCloseTo(first.start, "2026-08-16T06:06:52Z")
        assertCloseTo(first.culmination, "2026-08-16T06:09:54Z")
        assertCloseTo(first.end, "2026-08-16T06:12:58Z")
        assertThat(first.maxAltitudeDeg).isWithin(0.2).of(33.44)
        assertThat(first.startAzimuthDeg).isWithin(0.5).of(217.4)
        assertThat(first.endAzimuthDeg).isWithin(0.5).of(85.4)

        // A near-overhead one, where the search has to resolve a much faster altitude sweep.
        val overhead = passes[1]
        assertCloseTo(overhead.start, "2026-08-16T07:43:08Z")
        assertCloseTo(overhead.end, "2026-08-16T07:49:51Z")
        assertThat(overhead.maxAltitudeDeg).isWithin(0.5).of(85.85)
    }

    @Test
    fun `passes carry the satellite's identity`() {
        val pass = search().allPasses(Instant.parse("2026-08-15T12:00:00Z"), 48.hours).first()
        assertThat(pass.noradId).isEqualTo(25544)
        assertThat(pass.satelliteName).isEqualTo("ISS (ZARYA)")
        // 4–10 minutes is the whole range a real LEO pass occupies.
        assertThat(pass.duration.inWholeSeconds).isGreaterThan(120L)
        assertThat(pass.duration.inWholeSeconds).isLessThan(700L)
        assertThat(pass.culmination).isGreaterThan(pass.start)
        assertThat(pass.culmination).isLessThan(pass.end)
    }

    @Test
    fun `an all-daylight window yields passes but no visible ones`() {
        // Every one of those nine passes happens with the Sun well up — Skyfield puts the solar
        // altitude between +4° and +50° at each culmination. They are real passes and the map
        // layer would draw them; nobody could see any of them.
        //
        // This is the case a naive predictor gets wrong by reporting geometry as if it were
        // visibility, and it is not an edge case: it is most of the day, most of the year.
        val from = Instant.parse("2026-08-15T12:00:00Z")
        assertThat(search().allPasses(from, 48.hours)).hasSize(9)
        assertThat(search().visiblePasses(from, 48.hours)).isEmpty()
    }

    @Test
    fun `finds the one genuinely visible pass in a window that has exactly one`() {
        // Of the five passes clearing 10° over London that morning, only the 03:45 one happens
        // before dawn with the ISS still sunlit. Skyfield agrees: solar altitude -9.8°, satellite
        // lit. Getting five here would mean the darkness test is missing.
        val visible =
            search().visiblePasses(Instant.parse("2026-08-19T00:00:00Z"), 12.hours)

        assertThat(visible).hasSize(1)
        val pass = visible.single()
        assertCloseTo(pass.start, "2026-08-19T03:45:31Z")
        assertCloseTo(pass.end, "2026-08-19T03:48:03Z")
        assertThat(pass.maxAltitudeDeg).isWithin(0.2).of(11.733)
        // A low pass at ~1380 km: dimmer than a good overhead one, but still naked-eye.
        assertThat(pass.peakMagnitude).isGreaterThan(-2.0)
        assertThat(pass.peakMagnitude).isLessThan(2.0)
        // Skyfield reports the ISS lit for every 5-second sample across this pass, so nothing
        // should be recorded as a shadow entry.
        assertThat(pass.shadowEntry).isNull()
    }

    @Test
    fun `an empty result is a real answer, not a failure`() {
        // Visible passes cluster into multi-day seasons separated by multi-week gaps, so "nothing
        // tonight" is the common case rather than a sign something broke. The predictor must say
        // so plainly rather than degrading to "here is a daylight pass instead".
        val quiet = search().visiblePasses(Instant.parse("2026-08-15T12:00:00Z"), 12.hours)
        assertThat(quiet).isEmpty()
    }

    @Test
    fun `a longer search window finds a superset of a shorter one`() {
        val from = Instant.parse("2026-08-15T12:00:00Z")
        val short = search().allPasses(from, 24.hours)
        val long = search().allPasses(from, 48.hours)
        assertThat(long.size).isGreaterThan(short.size)
        // The passes the shorter search found must be exactly the ones the longer search found
        // first — the window truncates, it does not perturb.
        assertThat(long.take(short.size).map { it.start }).isEqualTo(short.map { it.start })
    }

    @Test
    fun `a pass in progress when the window ends is dropped rather than truncated`() {
        // Ending the window mid-pass must not produce a pass whose "sets at" is really "we
        // stopped looking" — a confidently wrong end time is worse than an omission.
        val from = Instant.parse("2026-08-15T12:00:00Z")
        val full = search().allPasses(from, 48.hours)
        val firstPass = full.first()

        // A window ending between the first pass's rise and set.
        val mid = firstPass.start + (firstPass.end - firstPass.start) / 2
        val truncated = search().allPasses(from, mid - from)
        assertThat(truncated).isEmpty()

        // One that comfortably contains it does report it.
        val contained = search().allPasses(from, firstPass.end + 1.hours - from)
        assertThat(contained).hasSize(1)
    }

    @Test
    fun `raising the minimum elevation discards the marginal passes`() {
        val from = Instant.parse("2026-08-15T12:00:00Z")
        val lenient = PassSearch(issTle, london, minimumElevationDeg = 10.0)
        val strict = PassSearch(issTle, london, minimumElevationDeg = 60.0)
        val all = lenient.allPasses(from, 48.hours)
        val high = strict.allPasses(from, 48.hours)

        assertThat(high.size).isLessThan(all.size)
        // Only the genuinely high passes survive, and each is shorter, since the satellite spends
        // less of the pass above the higher cut.
        assertThat(high.all { it.maxAltitudeDeg > 60.0 }).isTrue()
        assertThat(high).isNotEmpty()
    }

    @Test
    fun `the search is symmetric under observer longitude`() {
        // A sanity check that nothing has hard-coded a hemisphere: an observer on the far side of
        // the world sees the ISS too, just at different times.
        val sydney = PassSearch(issTle, LatLong(-33.8688, 151.2093))
        val passes = sydney.allPasses(Instant.parse("2026-08-15T12:00:00Z"), 48.hours)
        assertThat(passes).isNotEmpty()
        assertThat(passes.all { it.maxAltitudeDeg > 10.0 }).isTrue()
    }

    private fun assertCloseTo(
        actual: Instant,
        expectedIso: String,
    ) {
        val expected = Instant.parse(expectedIso)
        val deltaSeconds = abs((actual - expected).inWholeMilliseconds) / 1000.0
        assertThat(deltaSeconds).isLessThan(TIME_TOLERANCE_SECONDS)
    }

    private companion object {
        /**
         * Skyfield reports event times to the second and refines them by its own root-finder; this
         * search bisects to ~0.5 s and locates the culmination on a 5-second sample grid. Pass
         * times are displayed to the minute, so a few seconds of disagreement is far below
         * anything a user could notice while still catching a real search bug — a missed or
         * mis-bracketed crossing moves times by minutes.
         */
        const val TIME_TOLERANCE_SECONDS = 5.0
    }
}
