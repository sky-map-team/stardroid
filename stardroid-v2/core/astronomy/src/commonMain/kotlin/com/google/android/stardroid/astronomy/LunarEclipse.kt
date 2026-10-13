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
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.angularSeparationDeg
import com.google.android.stardroid.math.normalizeDegrees
import kotlinx.datetime.Instant
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.time.Duration.Companion.days

/**
 * Earth's umbra and penumbra, projected onto the sky at the Moon's current distance — the
 * geometry a lunar eclipse is judged against. Standard tangent-cone construction (Meeus,
 * *Astronomical Algorithms*, ch. 54): the umbra is the cone tangent internally to the Sun and
 * Earth, the penumbra tangent externally, both apexed on the Sun–Earth line.
 *
 * [antiSolarDirection] is geocentric, and deliberately never gets a topocentric counterpart —
 * unlike everything else in this module, this is correct for **every** observer on Earth's night
 * side, not an approximation. A lunar eclipse is decided by whether the Moon (a real object out
 * in space, ~384,000 km away) physically sits inside Earth's shadow cone (also a real object in
 * space, anchored to the Earth–Sun line): both are facts about the Solar System, fixed the moment
 * you fix the time, and neither depends on where on a 6,378 km-radius planet someone happens to
 * stand. Moving the observer changes only *which direction they must look* to see the Moon
 * (diurnal parallax, up to ~1°) — it does not move the Moon relative to the shadow, so it cannot
 * change whether the eclipse is happening. Contrast a solar eclipse, where the Moon's shadow on
 * *Earth* is only ~270 km wide, so parallax genuinely decides totality vs. partial vs. nothing —
 * that is the "unlike... a solar eclipse's path of totality" this docstring warns about, and
 * exactly why [Ephemeris.topocentricPosition] exists at all (D54) but has no business here.
 *
 * The practical consequence: [moonShadowSeparationDeg] must compare this against
 * [Ephemeris.geocentricPosition], never [Ephemeris.topocentricPosition] — mixing frames would
 * reintroduce the parallax this geometry is specifically built to ignore — and, per D84, in the
 * same precession frame the rest of the app draws in.
 */
data class ShadowCone(
    val umbraRadiusDeg: Double,
    val penumbraRadiusDeg: Double,
    val antiSolarDirection: RaDec,
)

/**
 * The atmosphere refracts sunlight into the geometric shadow, extending it slightly beyond the
 * bare-cone calculation. The conventional correction (Chauvenet, adopted by Danjon and still used
 * by NASA/USNO eclipse predictions) is to inflate Earth's effective radius by 1% before
 * constructing the cones, rather than modelling the atmosphere directly.
 */
private const val SHADOW_ENLARGEMENT_FACTOR = 1.01

/**
 * Earth's umbra/penumbra as seen from Earth's centre at [time]: their angular radii at the Moon's
 * distance, and the direction of the shadow axis (the antisolar point). Pure geometry off the
 * existing [Ephemeris] — no eclipse-specific ephemeris data needed, since D84 already leaves the
 * Sun and Moon in a shared, precession-correct frame.
 */
fun shadowCone(
    time: Instant,
    ephemeris: Ephemeris = MeeusEphemeris,
): ShadowCone {
    val sunDistanceKm = ephemeris.earthDistanceAu(SolarSystemBody.SUN, time) * KM_PER_AU
    val moonDistanceKm = ephemeris.earthDistanceAu(SolarSystemBody.MOON, time) * KM_PER_AU
    val sunRadiusKm = equatorialRadiusKm(SolarSystemBody.SUN)
    val earthRadiusKm = equatorialRadiusKm(SolarSystemBody.EARTH) * SHADOW_ENLARGEMENT_FACTOR

    // Half-angle of each tangent cone, at the Sun-Earth end.
    val umbraHalfAngleRad = atan((sunRadiusKm - earthRadiusKm) / sunDistanceKm)
    val penumbraHalfAngleRad = atan((sunRadiusKm + earthRadiusKm) / sunDistanceKm)

    // The umbra narrows and the penumbra widens with distance from Earth.
    val umbraRadiusKm = earthRadiusKm - moonDistanceKm * tan(umbraHalfAngleRad)
    val penumbraRadiusKm = earthRadiusKm + moonDistanceKm * tan(penumbraHalfAngleRad)

    val sun = ephemeris.geocentricPosition(SolarSystemBody.SUN, time)
    val antiSolar = RaDec(normalizeDegrees(sun.raDeg + 180.0), -sun.decDeg)

    return ShadowCone(
        umbraRadiusDeg = atan(umbraRadiusKm / moonDistanceKm) * RADIANS_TO_DEGREES,
        penumbraRadiusDeg = atan(penumbraRadiusKm / moonDistanceKm) * RADIANS_TO_DEGREES,
        antiSolarDirection = antiSolar,
    )
}

/**
 * The angular separation between the Moon's centre and the axis of Earth's shadow (the antisolar
 * point) at [time] — how deep into (or far from) the shadow the Moon currently sits. Compared
 * against [ShadowCone.umbraRadiusDeg]/[ShadowCone.penumbraRadiusDeg] plus the Moon's own angular
 * radius, this is what [LunarEclipseCircumstances] is built from.
 */
fun moonShadowSeparationDeg(
    time: Instant,
    ephemeris: Ephemeris = MeeusEphemeris,
): Double {
    val moon = ephemeris.geocentricPosition(SolarSystemBody.MOON, time)
    val antiSolar = shadowCone(time, ephemeris).antiSolarDirection
    return angularSeparationDeg(moon, antiSolar)
}

/** How deep the Moon's disc penetrates Earth's shadow at greatest eclipse. */
enum class LunarEclipseType { NONE, PENUMBRAL, PARTIAL, TOTAL }

/**
 * The circumstances of a lunar eclipse: its type and magnitude at greatest eclipse, and the
 * contact times bounding each stage. A contact time is null when that stage doesn't occur — e.g.
 * [totalityBegin]/[totalityEnd] (U2/U3) are null unless [type] is [LunarEclipseType.TOTAL], and
 * every contact is null when [type] is [LunarEclipseType.NONE].
 *
 * Naming follows the standard P1/U1/U2/U3/U4/P4 contact convention (Meeus ch. 54, and NASA/USNO
 * eclipse tables), spelled out rather than abbreviated.
 */
data class LunarEclipseCircumstances(
    val type: LunarEclipseType,
    val greatestEclipse: Instant,
    /** (σ_u + ρ_moon − Δ) / 2ρ_moon: the fraction of the Moon's diameter inside the umbra. */
    val umbralMagnitude: Double,
    /** As [umbralMagnitude], but against the penumbra. */
    val penumbralMagnitude: Double,
    /** P1 — the Moon first touches the penumbra. */
    val penumbralBegin: Instant? = null,
    /** U1 — the Moon first touches the umbra; the visible partial eclipse begins. */
    val umbralBegin: Instant? = null,
    /** U2 — the Moon is entirely within the umbra; totality begins. */
    val totalityBegin: Instant? = null,
    /** U3 — the Moon starts to leave the umbra; totality ends. */
    val totalityEnd: Instant? = null,
    /** U4 — the Moon fully leaves the umbra; the visible partial eclipse ends. */
    val umbralEnd: Instant? = null,
    /** P4 — the Moon fully leaves the penumbra. */
    val penumbralEnd: Instant? = null,
)

/**
 * The circumstances of the eclipse (if any) at the full moon nearest [fullMoon] — callers get
 * [fullMoon] from [nextLunarPhaseEvent], since eclipses only occur at full moon. Searches up to
 * 18 hours either side for the true greatest-eclipse instant, since the RA-elongation-based full
 * moon time and the true syzygy (zero ecliptic-latitude-independent moment nearest the shadow
 * axis) can differ by several hours.
 */
fun lunarEclipseNear(
    fullMoon: Instant,
    ephemeris: Ephemeris = MeeusEphemeris,
): LunarEclipseCircumstances {
    val greatest = greatestEclipseInstant(fullMoon, ephemeris)
    val greatestSeconds = greatest.toEpochSeconds()

    val cone = shadowCone(greatest, ephemeris)
    val moonRadiusDeg = ephemeris.angularDiameterDeg(SolarSystemBody.MOON, greatest) / 2.0
    val separationDeg = moonShadowSeparationDeg(greatest, ephemeris)

    val umbralMagnitude =
        (cone.umbraRadiusDeg + moonRadiusDeg - separationDeg) / (2.0 * moonRadiusDeg)
    val penumbralMagnitude =
        (cone.penumbraRadiusDeg + moonRadiusDeg - separationDeg) / (2.0 * moonRadiusDeg)

    val type =
        when {
            umbralMagnitude >= 1.0 -> LunarEclipseType.TOTAL
            umbralMagnitude > 0.0 -> LunarEclipseType.PARTIAL
            penumbralMagnitude > 0.0 -> LunarEclipseType.PENUMBRAL
            else -> LunarEclipseType.NONE
        }
    if (type == LunarEclipseType.NONE) {
        return LunarEclipseCircumstances(type, greatest, umbralMagnitude, penumbralMagnitude)
    }

    val penumbralThreshold = cone.penumbraRadiusDeg + moonRadiusDeg
    val umbralThreshold = cone.umbraRadiusDeg + moonRadiusDeg
    val totalityThreshold = cone.umbraRadiusDeg - moonRadiusDeg

    val umbralContactsApply = type != LunarEclipseType.PENUMBRAL
    val totalityContactsApply = type == LunarEclipseType.TOTAL

    return LunarEclipseCircumstances(
        type = type,
        greatestEclipse = greatest,
        umbralMagnitude = umbralMagnitude,
        penumbralMagnitude = penumbralMagnitude,
        penumbralBegin =
            findContact(
                greatestSeconds,
                direction = -1,
                penumbralThreshold,
                ephemeris,
            ),
        umbralBegin =
            if (umbralContactsApply) {
                findContact(greatestSeconds, direction = -1, umbralThreshold, ephemeris)
            } else {
                null
            },
        totalityBegin =
            if (totalityContactsApply) {
                findContact(greatestSeconds, direction = -1, totalityThreshold, ephemeris)
            } else {
                null
            },
        totalityEnd =
            if (totalityContactsApply) {
                findContact(greatestSeconds, direction = 1, totalityThreshold, ephemeris)
            } else {
                null
            },
        umbralEnd =
            if (umbralContactsApply) {
                findContact(greatestSeconds, direction = 1, umbralThreshold, ephemeris)
            } else {
                null
            },
        penumbralEnd = findContact(greatestSeconds, direction = 1, penumbralThreshold, ephemeris),
    )
}

/**
 * The lunar eclipse in progress at [after], or the next one after it, whichever is relevant —
 * null if none is found within [MAX_FULL_MOONS_SEARCHED] full moons (about 3 years; lunar
 * eclipses recur at intervals of a few months up to roughly 18 months, so this is a generous
 * bound, not a tuned one).
 *
 * The search seeds from [HALF_SYNODIC_MONTH_BUFFER_DAYS] before [after], not from [after] itself:
 * [nextLunarPhaseEvent] only returns full moons *strictly after* its argument, so seeding from
 * [after] would skip straight past an eclipse already under way (its full moon is in the past
 * relative to [after], even though its penumbral phase may not have ended yet) to next month's.
 * A candidate is only accepted once its own window — [LunarEclipseCircumstances.penumbralEnd], or
 * [LunarEclipseCircumstances.greatestEclipse] when there are no contacts to speak of — is still at
 * or after [after], so a full moon already fully elapsed by [after] is correctly skipped instead
 * of being returned as if it were upcoming.
 */
fun nextLunarEclipse(
    after: Instant,
    ephemeris: Ephemeris = MeeusEphemeris,
): LunarEclipseCircumstances? {
    var fullMoon =
        nextLunarPhaseEvent(LunarPhase.FULL, after - HALF_SYNODIC_MONTH_BUFFER_DAYS, ephemeris)
    repeat(MAX_FULL_MOONS_SEARCHED) {
        val circumstances = lunarEclipseNear(fullMoon, ephemeris)
        val windowEnd = circumstances.penumbralEnd ?: circumstances.greatestEclipse
        if (circumstances.type != LunarEclipseType.NONE && windowEnd >= after) return circumstances
        fullMoon = nextLunarPhaseEvent(LunarPhase.FULL, fullMoon, ephemeris)
    }
    return null
}

private const val MAX_FULL_MOONS_SEARCHED = 40
private val HALF_SYNODIC_MONTH_BUFFER_DAYS = 16.days

private const val COARSE_STEP_SECONDS = 20 * 60.0
private const val SEARCH_HALF_WINDOW_SECONDS = 18 * 3600.0
private const val CONTACT_STEP_SECONDS = 10 * 60.0
private const val MAX_CONTACT_STEPS = 90 // 15 hours out from greatest eclipse
private const val GOLDEN_SECTION_ITERATIONS = 40
private const val BISECTION_ITERATIONS = 40

/**
 * The true instant of least Moon-shadow separation near [fullMoon]: a coarse scan across
 * ±[SEARCH_HALF_WINDOW_SECONDS] to bracket the minimum, then a golden-section refinement. The
 * separation curve is smooth and unimodal within this window (the next eclipse "season" is
 * months away), so no derivative or higher-order solver is needed.
 */
private fun greatestEclipseInstant(
    fullMoon: Instant,
    ephemeris: Ephemeris,
): Instant {
    val center = fullMoon.toEpochSeconds()
    var bestT = center
    var bestSeparation = moonShadowSeparationDeg(fullMoon, ephemeris)
    var t = center - SEARCH_HALF_WINDOW_SECONDS
    val end = center + SEARCH_HALF_WINDOW_SECONDS
    while (t <= end) {
        val separation = moonShadowSeparationDeg(t.toInstant(), ephemeris)
        if (separation < bestSeparation) {
            bestSeparation = separation
            bestT = t
        }
        t += COARSE_STEP_SECONDS
    }
    return goldenSectionMinimize(
        bestT - COARSE_STEP_SECONDS,
        bestT + COARSE_STEP_SECONDS,
        ephemeris,
    )
}

private fun goldenSectionMinimize(
    aSeconds: Double,
    bSeconds: Double,
    ephemeris: Ephemeris,
): Instant {
    var a = aSeconds
    var b = bSeconds
    val goldenRatio = (sqrt(5.0) - 1.0) / 2.0
    var c = b - (b - a) * goldenRatio
    var d = a + (b - a) * goldenRatio
    repeat(GOLDEN_SECTION_ITERATIONS) {
        val fc = moonShadowSeparationDeg(c.toInstant(), ephemeris)
        val fd = moonShadowSeparationDeg(d.toInstant(), ephemeris)
        if (fc < fd) b = d else a = c
        c = b - (b - a) * goldenRatio
        d = a + (b - a) * goldenRatio
    }
    return ((a + b) / 2.0).toInstant()
}

/**
 * Searches outward from [centerSeconds] (greatest eclipse) in [direction] (-1 earlier, +1 later)
 * for the instant the Moon-shadow separation first reaches [thresholdDeg], or null if it doesn't
 * within [MAX_CONTACT_STEPS] steps. Assumes separation is monotonically increasing away from
 * greatest eclipse across this range, true for the few-hour span an eclipse spans.
 */
private fun findContact(
    centerSeconds: Double,
    direction: Int,
    thresholdDeg: Double,
    ephemeris: Ephemeris,
): Instant? {
    var previous = centerSeconds
    val insideGap = moonShadowSeparationDeg(previous.toInstant(), ephemeris) - thresholdDeg
    if (insideGap >= 0.0) return null // already outside this boundary at greatest eclipse
    repeat(MAX_CONTACT_STEPS) {
        val next = previous + direction * CONTACT_STEP_SECONDS
        val gap = moonShadowSeparationDeg(next.toInstant(), ephemeris) - thresholdDeg
        if (gap >= 0.0) return bisectContact(previous, next, thresholdDeg, ephemeris)
        previous = next
    }
    return null
}

/** Bisects between [insideSeconds] (gap < 0) and [outsideSeconds] (gap >= 0) for the crossing. */
private fun bisectContact(
    insideSeconds: Double,
    outsideSeconds: Double,
    thresholdDeg: Double,
    ephemeris: Ephemeris,
): Instant {
    var lo = insideSeconds
    var hi = outsideSeconds
    repeat(BISECTION_ITERATIONS) {
        val mid = (lo + hi) / 2.0
        val gap = moonShadowSeparationDeg(mid.toInstant(), ephemeris) - thresholdDeg
        if (gap < 0.0) lo = mid else hi = mid
    }
    return ((lo + hi) / 2.0).toInstant()
}

private fun Instant.toEpochSeconds(): Double = toEpochMilliseconds() / 1000.0

private fun Double.toInstant(): Instant = Instant.fromEpochMilliseconds((this * 1000.0).toLong())
