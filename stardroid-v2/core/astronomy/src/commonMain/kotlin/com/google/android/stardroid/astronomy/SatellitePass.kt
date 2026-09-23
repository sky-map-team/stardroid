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
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * One pass of a satellite over an observer: when it rises, culminates and sets, how high and how
 * bright it gets, and — if it happens — when it fades into the Earth's shadow.
 *
 * Azimuths stay numeric. Turning 315° into "NW" needs localized strings and is presentation, so it
 * belongs app-side; `TonightSky` sets the same precedent by returning raw azimuths.
 */
data class SatellitePass(
    val satelliteName: String,
    val noradId: Int,
    /** Acquisition of signal: when the satellite rises past [PassSearch.minimumElevationDeg]. */
    val start: Instant,
    val culmination: Instant,
    /** Loss of signal: when it drops back below the minimum elevation. */
    val end: Instant,
    val maxAltitudeDeg: Double,
    val startAzimuthDeg: Double,
    val endAzimuthDeg: Double,
    /** Brightest (most negative) magnitude reached while visible. */
    val peakMagnitude: Double,
    /**
     * When the satellite entered the Earth's shadow mid-pass and visibly faded out, or `null` if
     * it stayed lit until it set.
     *
     * Worth surfacing prominently: a satellite that vanishes from a clear sky is confusing, and
     * "fades into Earth's shadow at 21:51" turns that into the most memorable thing the app can
     * say.
     */
    val shadowEntry: Instant?,
) {
    val duration: Duration get() = end - start
}

/**
 * Finds visible satellite passes by scanning forward in time.
 *
 * **Why a bespoke search rather than [nextRiseSetTime].** That solver iterates on hour angle with
 * `CONVERGENCE_HOURS = 0.008` over day offsets, tuned for bodies moving ≤15° per *day*. A
 * satellite moves 4° per *minute*; the general solver does not converge for it and cannot be made
 * to. Scan-and-bisect exists precisely because the existing machinery does not generalize this
 * far.
 *
 * **Why a 30-second step.** Near the horizon, where rise and set live, the ISS climbs at
 * 0.15–0.3°/s and a pass lasts 4–10 minutes, so 30 s guarantees 8–20 samples inside any real pass
 * — none can be stepped over. The only thing a coarser scan misses is a marginal sub-degree pass,
 * which [minimumElevationDeg] discards anyway.
 *
 * **Cost.** 48 h at 30 s is 5760 propagations per satellite, at a few microseconds each: tens of
 * milliseconds for one satellite. A hundred-satellite fleet (phase 5) will want a coarse
 * pre-filter and a background dispatcher; one satellite does not.
 */
class PassSearch(
    private val tle: Tle,
    private val observer: LatLong,
    /**
     * Below this the satellite is lost to buildings and horizon haze, so a pass that never gets
     * higher is not an event. Also what makes the 30-second scan safe.
     */
    val minimumElevationDeg: Double = DEFAULT_MINIMUM_ELEVATION_DEG,
    /** The satellite's brightness at 1000 km and 90° phase. */
    private val standardMagnitude: Double = SatelliteMagnitude.ISS_STANDARD_MAGNITUDE,
) {
    private val sgp4 = Sgp4(tle)

    /**
     * Every **visible** pass beginning within [searchWindow] of [from] — geometrically above the
     * horizon, in a dark enough sky, with the satellite itself sunlit.
     *
     * An empty list is a real answer, not a failure: visible ISS passes cluster into multi-day
     * seasons separated by multi-week gaps, so "nothing tonight" is the honest and common result.
     *
     * Use [allPasses] instead for passes regardless of illumination — that is what the map layer
     * wants, since it draws the satellite whether or not anyone could see it.
     */
    fun visiblePasses(
        from: Instant,
        searchWindow: Duration = DEFAULT_SEARCH_WINDOW,
    ): List<SatellitePass> =
        allPasses(
            from,
            searchWindow,
        ).filter { it.peakMagnitude < UNLIT_MAGNITUDE }

    /**
     * Every pass that clears [minimumElevationDeg], visible or not.
     *
     * A pass that is never both sunlit and in a dark sky gets [UNLIT_MAGNITUDE] as its
     * [SatellitePass.peakMagnitude], which is how [visiblePasses] filters it out.
     */
    fun allPasses(
        from: Instant,
        searchWindow: Duration = DEFAULT_SEARCH_WINDOW,
    ): List<SatellitePass> {
        val passes = mutableListOf<SatellitePass>()
        val end = from + searchWindow
        var previousTime = from
        var previousAltitude = altitudeAt(from)
        var passStart: Instant? = if (previousAltitude > minimumElevationDeg) from else null

        var time = from + SCAN_STEP
        while (time <= end) {
            val altitude = altitudeAt(time)
            if (previousAltitude <= minimumElevationDeg && altitude > minimumElevationDeg) {
                passStart = bisectCrossing(previousTime, time, rising = true)
            } else if (previousAltitude > minimumElevationDeg && altitude <= minimumElevationDeg) {
                val start = passStart
                if (start != null) {
                    val setsAt = bisectCrossing(previousTime, time, rising = false)
                    passes += describePass(start, setsAt)
                }
                passStart = null
            }
            previousTime = time
            previousAltitude = altitude
            time += SCAN_STEP
        }
        // A pass still in progress when the window ends is deliberately dropped rather than
        // reported with a truncated end time — a pass whose "sets at" is really "we stopped
        // looking" would be a lie, and the caller can always ask for a longer window.
        return passes
    }

    /**
     * Refines a horizon crossing (the observer's horizon, not the search window) bracketed by
     * [before] and [after] to about half a second.
     *
     * Bisection rather than anything cleverer because the altitude curve is smooth and monotonic
     * across a 30-second bracket near the horizon, and [BISECTION_STEPS] halvings of 30 s reach
     * ~0.5 s — ample when pass times are displayed to the minute.
     */
    private fun bisectCrossing(
        before: Instant,
        after: Instant,
        rising: Boolean,
    ): Instant {
        var low = before
        var high = after
        repeat(BISECTION_STEPS) {
            val middle = low + (high - low) / 2
            val aboveAtMiddle = altitudeAt(middle) > minimumElevationDeg
            if (aboveAtMiddle == rising) high = middle else low = middle
        }
        return low + (high - low) / 2
    }

    /**
     * Samples an identified pass to find its peak, its brightest visible moment, and whether it
     * enters the Earth's shadow along the way.
     */
    private fun describePass(
        start: Instant,
        end: Instant,
    ): SatellitePass {
        var culmination = start
        var maxAltitude = Double.NEGATIVE_INFINITY
        var peakMagnitude = UNLIT_MAGNITUDE
        var shadowEntry: Instant? = null
        var wasLitAndDark = false

        // The Sun's *inertial* direction and its equatorial coordinates are hoisted out of the
        // sample loop: both are time-only and each ran the full Meeus solar series plus a
        // precession-matrix build at every 5-second sample, ~120 times per pass (audit-2026-08
        // M1). Over a pass the Sun moves under 0.01° along its orbit, far below this model's
        // accuracy. Its *altitude* still changes — 15°/hour of Earth rotation — so that is
        // recomputed per sample from the frozen coordinates, which costs three trig calls and
        // keeps the twilight test exact.
        val sunDirection = SatelliteMagnitude.sunDirectionTeme(start)
        val sunPosition = MeeusEphemeris.topocentricPosition(SolarSystemBody.SUN, start, observer)

        val steps = ((end - start) / SAMPLE_STEP).toInt().coerceAtLeast(1)
        for (index in 0..steps) {
            val time = start + SAMPLE_STEP * index.toDouble()
            val state = sgp4.propagateAt(time) ?: continue
            val position = SatelliteEphemeris.topocentricTeme(state, time, observer)
            val altitude = position.altitudeDeg(time, observer)
            if (altitude > maxAltitude) {
                maxAltitude = altitude
                culmination = time
            }

            val skyIsDark =
                altitudeDeg(sunPosition, time, observer) <= CIVIL_TWILIGHT_SUN_ALT_DEG
            val lit = SatelliteMagnitude.isSunlit(state.positionKm, sunDirection)

            if (lit && skyIsDark) {
                wasLitAndDark = true
                val magnitude =
                    SatelliteMagnitude.apparentMagnitude(
                        standardMagnitude = standardMagnitude,
                        rangeKm = position.rangeKm,
                        phaseAngleDeg =
                            SatelliteMagnitude.phaseAngleDeg(
                                state.positionKm,
                                SatelliteEphemeris.observerPositionTeme(time, observer),
                                sunDirection,
                            ),
                    )
                if (magnitude < peakMagnitude) peakMagnitude = magnitude
            } else if (wasLitAndDark && !lit && shadowEntry == null) {
                // Lit, then not: the satellite has crossed into the Earth's shadow mid-pass, which
                // is the fade-out a watcher actually sees. Only the first crossing is recorded.
                shadowEntry = time
            }
        }

        return SatellitePass(
            satelliteName = tle.name,
            noradId = tle.noradId,
            start = start,
            culmination = culmination,
            end = end,
            maxAltitudeDeg = maxAltitude,
            startAzimuthDeg = azimuthAt(start),
            endAzimuthDeg = azimuthAt(end),
            peakMagnitude = peakMagnitude,
            shadowEntry = shadowEntry,
        )
    }

    /** Altitude in degrees, or well below the horizon when the model has broken down. */
    private fun altitudeAt(time: Instant): Double {
        val state = sgp4.propagateAt(time) ?: return BELOW_HORIZON_SENTINEL
        return SatelliteEphemeris
            .topocentricTeme(state, time, observer)
            .altitudeDeg(time, observer)
    }

    private fun azimuthAt(time: Instant): Double {
        val state = sgp4.propagateAt(time) ?: return 0.0
        return SatelliteEphemeris
            .topocentricTeme(state, time, observer)
            .azimuthDeg(time, observer)
    }

    companion object {
        /** See the class KDoc for why 30 seconds is both safe and sufficient. */
        private val SCAN_STEP = 30.seconds

        /** Finer sampling within a known pass, for the peak, magnitude and shadow entry. */
        private val SAMPLE_STEP = 5.seconds

        /** Six halvings of a 30-second bracket reach ~0.5 s. */
        private const val BISECTION_STEPS = 6

        /**
         * How far ahead to search — "tonight and tomorrow", answered honestly.
         *
         * Named a *window* rather than a horizon on purpose: everything else in this file means
         * the observer's horizon by that word, and one term cannot carry both a time and an
         * altitude without eventually misleading someone.
         *
         * Beyond about five days TLE drift makes predicted times uncertain by tens of seconds,
         * which is a reason not to promise a fortnight; the parameter exists so a "more passes"
         * screen can ask for more.
         */
        val DEFAULT_SEARCH_WINDOW: Duration = 48.hours

        /** Below 10° buildings and haze make a pass a non-event. */
        const val DEFAULT_MINIMUM_ELEVATION_DEG: Double = 10.0

        /**
         * The magnitude assigned to a pass that was never both sunlit and in a dark sky — dimmer
         * than anything nameable, so it sorts last and [visiblePasses] can filter on it.
         */
        const val UNLIT_MAGNITUDE: Double = 99.0

        private const val BELOW_HORIZON_SENTINEL = -90.0
    }
}
