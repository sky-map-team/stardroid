/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.events

import com.google.android.stardroid.astronomy.CIVIL_TWILIGHT_SUN_ALT_DEG
import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.astronomy.KeplerianEphemeris
import com.google.android.stardroid.astronomy.LunarEclipseCircumstances
import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.NAUTICAL_TWILIGHT_SUN_ALT_DEG
import com.google.android.stardroid.astronomy.RiseSetIndicator
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.altitudeDeg
import com.google.android.stardroid.astronomy.azimuthDeg
import com.google.android.stardroid.astronomy.nextLunarEclipse
import com.google.android.stardroid.astronomy.nextLunarPhaseEvent
import com.google.android.stardroid.astronomy.nextRiseSetTime
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The events engine v1 (D75/D76): everything worth saying about tonight's sky, computed
 * offline from the ephemeris and the catalog's shower table. Pure — the tonight and
 * countdown widgets (and, in phase 3, the notification digest) only render this.
 *
 * [quality] ranks the highlight list; the scale is a fixed heuristic table (shower peak >
 * bright planet > moon phase > the bright-moon caveat), not a physical quantity.
 */
sealed interface SkyEvent {
    val quality: Double

    /** A planet comfortably observable during tonight's dark hours. */
    data class WellPlacedPlanet(
        val body: SolarSystemBody,
        /** When it first becomes well placed tonight (already sky-dark and high enough). */
        val from: Instant,
        val bestAltitudeDeg: Double,
        /** Azimuth (degrees from north) at [from] — which way to look. */
        val azimuthDeg: Double,
        override val quality: Double,
    ) : SkyEvent

    /** A meteor shower at ([daysUntilPeak] = 0) or approaching its annual peak. */
    data class ShowerPeak(
        val shower: MeteorShower,
        val peakTime: Instant,
        val daysUntilPeak: Int,
        /** Moon illumination at the peak; below [DARK_MOON_FRACTION] is a dark-sky peak. */
        val moonIllumination: Double,
        override val quality: Double,
    ) : SkyEvent

    /** A full or new moon within the next 24 hours (new = darkest skies of the month). */
    data class MoonPhaseTonight(
        val phase: LunarPhase,
        val time: Instant,
        override val quality: Double,
    ) : SkyEvent

    /**
     * A bright moon up during dark hours, washing out faint objects — the honest downgrade
     * row (mockup: credibility is the retention mechanism). Only ever listed alongside
     * other highlights, never as the lone item of a "quiet" night.
     */
    data class BrightMoon(
        val illumination: Double,
        val moonSet: Instant?,
        override val quality: Double,
    ) : SkyEvent

    /**
     * A visible satellite pass tonight — bright, sunlit, in a dark sky (D92 phase 4b).
     *
     * Joining this hierarchy is what puts passes on the tonight widget and the D77 notification
     * digest with no new UI at all: those surfaces render [SkyEvent]s and nothing else.
     *
     * Purity survives because passes arrive as a **parameter** to [tonightSky], exactly as
     * `showers` already do — the caller computes them and hands them in, so `:core:events` keeps
     * its promise of no network and no Android.
     */
    data class SatellitePassTonight(
        val pass: SatellitePass,
        override val quality: Double,
    ) : SkyEvent

    /**
     * A lunar eclipse starting soon, or already under way (D106).
     *
     * Unlike the other events here, this is pure computation off [MeeusEphemeris] — no network
     * data, so unlike [SatellitePassTonight] it isn't a caller-supplied parameter; [tonightSky]
     * finds it itself, the same way it already finds [MoonPhaseTonight].
     */
    data class LunarEclipseUpcoming(
        val circumstances: LunarEclipseCircumstances,
        override val quality: Double,
    ) : SkyEvent
}

/** What the countdown widget counts to: the nearest shower peak or moon extreme. */
sealed interface CountdownTarget {
    val time: Instant

    data class ToShowerPeak(
        val shower: MeteorShower,
        override val time: Instant,
        /** Moon under [DARK_MOON_FRACTION] at the peak — the "new-moon peak" badge. */
        val darkPeak: Boolean,
    ) : CountdownTarget

    data class ToMoonPhase(
        val phase: LunarPhase,
        override val time: Instant,
    ) : CountdownTarget
}

/**
 * Tonight, summarized. [sunset]/[darknessStart] are null without a [LatLong] (rise/set math
 * needs somewhere to stand); [highlights] is ranked best-first and capped at three;
 * [countdown] is location-free and so always present while any shower or lunation is ahead.
 */
data class TonightSky(
    val sunset: Instant?,
    /** Sun 12° below the horizon — faint-object observing starts. */
    val darknessStart: Instant?,
    val highlights: List<SkyEvent>,
    val countdown: CountdownTarget?,
) {
    /** The shower alert takes tonight's one notification slot when a peak is tonight (D77). */
    val showerPeakTonight: SkyEvent.ShowerPeak?
        get() =
            highlights
                .filterIsInstance<SkyEvent.ShowerPeak>()
                .firstOrNull { it.daysUntilPeak == 0 }

    /**
     * The digest suppression threshold (D77): a night is worth announcing only when
     * something at least planet-grade made the list — the bright-moon caveat and taper-off
     * shower approach rows alone never interrupt anyone. Silence keeps the signal.
     */
    val worthAnnouncing: Boolean
        get() = highlights.any { it.quality >= ANNOUNCE_THRESHOLD }

    companion object {
        const val ANNOUNCE_THRESHOLD = 0.5
    }
}

/** Planets the engine considers, with their base quality (brightness-ordered heuristic). */
private val PLANET_QUALITY =
    mapOf(
        SolarSystemBody.VENUS to 0.68,
        SolarSystemBody.JUPITER to 0.66,
        SolarSystemBody.SATURN to 0.62,
        SolarSystemBody.MARS to 0.58,
        SolarSystemBody.MERCURY to 0.40,
    )

/**
 * Quality for a satellite pass, banded by peak magnitude.
 *
 * Raw brightness is the wrong scale to borrow here. A −3.1 ISS pass outshines everything in the
 * sky but the Moon, yet it lasts about six minutes, while a well-placed planet is there all
 * evening. So only a genuinely bright pass — brighter than [BRIGHT_PASS_MAGNITUDE] — clears
 * Venus's 0.68; an ordinary one sits below the planets, where a six-minute window belongs.
 */
private fun passQuality(peakMagnitude: Double): Double =
    when {
        peakMagnitude <= BRILLIANT_PASS_MAGNITUDE -> 0.80
        peakMagnitude <= BRIGHT_PASS_MAGNITUDE -> 0.70
        peakMagnitude <= ORDINARY_PASS_MAGNITUDE -> 0.55
        else -> 0.45
    }

/** Brighter than Jupiter at its best; worth interrupting an evening for. */
private const val BRILLIANT_PASS_MAGNITUDE = -3.0

/** The threshold above which a pass outranks Venus (0.68). */
private const val BRIGHT_PASS_MAGNITUDE = -2.0

/** Still an easy naked-eye sight, but not one to reorder the evening around. */
private const val ORDINARY_PASS_MAGNITUDE = -0.5

private const val WELL_PLACED_MIN_ALT_DEG = 25.0
private const val BRIGHT_MOON_FRACTION = 0.6
const val DARK_MOON_FRACTION = 0.3

/**
 * How far ahead an eclipse starts appearing as a highlight — matches [showerEvents]'s
 * highlight-vs-countdown-only cutoff, so an eclipse and a shower approach the list the same way.
 */
private const val ECLIPSE_HIGHLIGHT_HORIZON_DAYS = 3

/**
 * Quality by eclipse type. A total eclipse is rarer and more dramatic than any planet or shower
 * peak this engine ranks, so it outranks them; a barely-perceptible penumbral eclipse sits below
 * [TonightSky.ANNOUNCE_THRESHOLD] deliberately — it isn't worth a notification.
 */
private fun eclipseQuality(type: LunarEclipseType): Double =
    when (type) {
        LunarEclipseType.TOTAL -> 0.95
        LunarEclipseType.PARTIAL -> 0.75
        LunarEclipseType.PENUMBRAL -> 0.35
        LunarEclipseType.NONE -> 0.0
    }

/** How far ahead the countdown looks; showers beyond this fall to the next lunation. */
private const val COUNTDOWN_HORIZON_DAYS = 45

/** Minor showers (low ZHR) never make the countdown; they still highlight on their night. */
private const val COUNTDOWN_MIN_ZHR = 20

fun tonightSky(
    now: Instant,
    location: LatLong?,
    showers: List<MeteorShower>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    ephemeris: Ephemeris = KeplerianEphemeris,
    /**
     * Visible satellite passes for tonight, or empty.
     *
     * A parameter rather than something this engine computes, for two reasons: element sets come
     * from the network, which this module must not touch; and it is the caller that knows whether
     * the satellite layer is even on. `if (layerEnabled) passes else emptyList()` at the boundary
     * gates the widget and the digest line together, consistently, without a preference read
     * reaching into a pure module.
     */
    passes: List<SatellitePass> = emptyList(),
): TonightSky {
    val sunset =
        location?.let {
            nextRiseSetTime(SolarSystemBody.SUN, now, it, RiseSetIndicator.SET, ephemeris)
        }
    val darknessStart =
        location?.let {
            nextRiseSetTime(
                position = { time -> ephemeris.geocentricPosition(SolarSystemBody.SUN, time) },
                after = now,
                location = it,
                indicator = RiseSetIndicator.SET,
                horizonAltitudeDeg = NAUTICAL_TWILIGHT_SUN_ALT_DEG,
            )
        }

    val highlights = mutableListOf<SkyEvent>()
    highlights += showerEvents(now, showers, zone, ephemeris, highlightsOnly = true)
    highlights +=
        passes.map { SkyEvent.SatellitePassTonight(it, passQuality(it.peakMagnitude)) }
    lunarEclipseUpcoming(now, location)?.let { highlights += it }
    if (location != null) {
        highlights += planetEvents(now, sunset ?: now, location, ephemeris)
        moonPhaseTonight(now, ephemeris)?.let { highlights += it }
        brightMoon(now, sunset ?: now, location, ephemeris)?.let { highlights += it }
    }

    val ranked = highlights.sortedByDescending { it.quality }.take(3)
    // The bright-moon caveat qualifies other sights; alone it would just be a nag.
    val pruned = if (ranked.all { it is SkyEvent.BrightMoon }) emptyList() else ranked

    return TonightSky(
        sunset = sunset,
        darknessStart = darknessStart,
        highlights = pruned,
        countdown = countdown(now, showers, zone, ephemeris),
    )
}

/**
 * Shower highlights: quality peaks on the night itself (with a dark-sky bonus) and tapers
 * across the last three approach days; anything further out is countdown material only.
 */
private fun showerEvents(
    now: Instant,
    showers: List<MeteorShower>,
    zone: TimeZone,
    ephemeris: Ephemeris,
    highlightsOnly: Boolean,
): List<SkyEvent.ShowerPeak> =
    showers.mapNotNull { shower ->
        val peakTime = nextOccurrence(shower.peak, now, zone)
        val days = now.toLocalDateTime(zone).date.daysUntil(peakTime.toLocalDateTime(zone).date)
        if (highlightsOnly && days > 3) return@mapNotNull null
        val moon = ephemeris.illuminatedFraction(SolarSystemBody.MOON, peakTime)
        val quality =
            when (days) {
                0 -> 0.85 + if (moon < DARK_MOON_FRACTION) 0.1 else 0.0
                else -> 0.5 - days * 0.05
            }
        SkyEvent.ShowerPeak(shower, peakTime, days, moon, quality)
    }

/**
 * Samples each candidate planet every 30 minutes across the evening window (sunset + 8 h):
 * well placed = above [WELL_PLACED_MIN_ALT_DEG] while the sky is dark
 * ([CIVIL_TWILIGHT_SUN_ALT_DEG]). Reports the first such moment and the window's best altitude.
 */
private fun planetEvents(
    now: Instant,
    eveningStart: Instant,
    location: LatLong,
    ephemeris: Ephemeris,
): List<SkyEvent.WellPlacedPlanet> {
    // The sample grid and the Sun's altitude on it are planet-independent, so they are built once
    // rather than recomputed inside every candidate's loop (audit-2026-08 M1).
    val windowStart = maxOf(now, eveningStart)
    val windowEnd = windowStart + 8.hours
    val darkSamples =
        generateSequence(windowStart) { it + 30.minutes }
            .takeWhile { it <= windowEnd }
            .filter { sample ->
                altitudeDeg(
                    ephemeris.geocentricPosition(SolarSystemBody.SUN, sample),
                    sample,
                    location,
                ) <= CIVIL_TWILIGHT_SUN_ALT_DEG
            }.toList()

    return PLANET_QUALITY.mapNotNull { (body, baseQuality) ->
        var from: Instant? = null
        var bestAlt = 0.0
        for (sample in darkSamples) {
            val alt =
                altitudeDeg(
                    ephemeris.topocentricPosition(body, sample, location),
                    sample,
                    location,
                )
            if (alt >= WELL_PLACED_MIN_ALT_DEG) {
                if (from == null) from = sample
                if (alt > bestAlt) bestAlt = alt
            }
        }
        from?.let {
            SkyEvent.WellPlacedPlanet(
                body = body,
                from = it,
                bestAltitudeDeg = bestAlt,
                azimuthDeg =
                    azimuthDeg(ephemeris.topocentricPosition(body, it, location), it, location),
                // A small altitude bonus so a planet riding high beats one skimming the cut.
                quality = baseQuality + (bestAlt - WELL_PLACED_MIN_ALT_DEG) / 65.0 * 0.1,
            )
        }
    }
}

/** Full or new moon within the next 24 hours; new moon outranks full (dark skies to use). */
private fun moonPhaseTonight(
    now: Instant,
    ephemeris: Ephemeris,
): SkyEvent.MoonPhaseTonight? {
    for ((phase, quality) in listOf(LunarPhase.NEW to 0.5, LunarPhase.FULL to 0.45)) {
        val event = nextLunarPhaseEvent(phase, now, ephemeris)
        if (event < now + 24.hours) return SkyEvent.MoonPhaseTonight(phase, event, quality)
    }
    return null
}

/**
 * The next lunar eclipse, if its window (first penumbral contact to last) overlaps
 * [now, now + ECLIPSE_HIGHLIGHT_HORIZON_DAYS] and, when a [location] is known, the Moon is above
 * the horizon there at some point during it — an eclipse nobody at this location can see isn't a
 * highlight, however rare it is elsewhere.
 *
 * Always uses [MeeusEphemeris] regardless of the [ephemeris] `tonightSky` was called with:
 * eclipse timing needs the accurate Sun/Moon geometry (D84), not whatever baseline the caller is
 * using for cheaper, less timing-sensitive things like planet visibility.
 */
private fun lunarEclipseUpcoming(
    now: Instant,
    location: LatLong?,
): SkyEvent.LunarEclipseUpcoming? {
    val eclipse = nextLunarEclipse(now, MeeusEphemeris) ?: return null
    val startsShowing = eclipse.penumbralBegin ?: eclipse.greatestEclipse
    val endsShowing = eclipse.penumbralEnd ?: eclipse.greatestEclipse
    if (endsShowing < now) return null
    if (startsShowing > now + ECLIPSE_HIGHLIGHT_HORIZON_DAYS.days) return null
    if (location != null && !moonAboveHorizonBetween(startsShowing, endsShowing, location)) {
        return null
    }
    return SkyEvent.LunarEclipseUpcoming(eclipse, eclipseQuality(eclipse.type))
}

/**
 * Samples every 30 minutes across an eclipse's few-hour span — cheap, and never misses a rise.
 *
 * Public (not `private`) so the app-side eclipse alert scheduler (D106) can apply the same
 * "is this actually visible from here" gate before arming a notification, rather than
 * re-deriving it — the two call sites want exactly the same answer to exactly the same question.
 */
fun moonAboveHorizonBetween(
    start: Instant,
    end: Instant,
    location: LatLong,
): Boolean =
    generateSequence(start) { it + 30.minutes }
        .takeWhile { it <= end }
        .any {
            altitudeDeg(
                MeeusEphemeris.topocentricPosition(SolarSystemBody.MOON, it, location),
                it,
                location,
            ) > 0.0
        }

/** A ≥60%-lit moon above the horizon during the evening window earns the caveat row. */
private fun brightMoon(
    now: Instant,
    eveningStart: Instant,
    location: LatLong,
    ephemeris: Ephemeris,
): SkyEvent.BrightMoon? {
    val illumination = ephemeris.illuminatedFraction(SolarSystemBody.MOON, now)
    if (illumination < BRIGHT_MOON_FRACTION) return null
    val start = maxOf(now, eveningStart)
    val moonUp =
        generateSequence(start) { it + 1.hours }
            .takeWhile { it <= start + 8.hours }
            .any {
                altitudeDeg(
                    ephemeris.topocentricPosition(SolarSystemBody.MOON, it, location),
                    it,
                    location,
                ) > 0.0
            }
    if (!moonUp) return null
    val moonSet =
        nextRiseSetTime(SolarSystemBody.MOON, now, location, RiseSetIndicator.SET, ephemeris)
    return SkyEvent.BrightMoon(illumination, moonSet, quality = 0.25)
}

/**
 * The next major shower peak within the horizon; only when none qualifies, the next
 * full/new moon. Showers outrank lunations even when a lunation is nearer — a moon extreme
 * is never more than ~15 days out, so "nearest wins" would show showers almost never, and
 * anticipation of the rare event is the widget's whole point (mockup, widget B).
 */
private fun countdown(
    now: Instant,
    showers: List<MeteorShower>,
    zone: TimeZone,
    ephemeris: Ephemeris,
): CountdownTarget? {
    val today = now.toLocalDateTime(zone).date
    val shower =
        showers
            .filter { it.peakZhr >= COUNTDOWN_MIN_ZHR }
            .map { it to nextOccurrence(it.peak, now, zone) }
            .filter {
                    (_, peak) ->
                today.daysUntil(peak.toLocalDateTime(zone).date) <= COUNTDOWN_HORIZON_DAYS
            }
            .minByOrNull { (_, peak) -> peak }
            ?.let { (shower, peak) ->
                CountdownTarget.ToShowerPeak(
                    shower = shower,
                    time = peak,
                    darkPeak =
                        ephemeris.illuminatedFraction(SolarSystemBody.MOON, peak) <
                            DARK_MOON_FRACTION,
                )
            }
    if (shower != null) return shower
    return listOf(LunarPhase.FULL, LunarPhase.NEW)
        .map { CountdownTarget.ToMoonPhase(it, nextLunarPhaseEvent(it, now, ephemeris)) }
        .minBy { it.time }
}

/**
 * The next occurrence of a year-agnostic [MonthDay] at 22:00 local — mid-evening, so "days
 * until" counts nights, and a peak date still counts as 0 all of that evening. Feb 29 rows
 * fall back to Feb 28 in non-leap years.
 */
private fun nextOccurrence(
    day: MonthDay,
    now: Instant,
    zone: TimeZone,
): Instant {
    val today = now.toLocalDateTime(zone).date

    fun onYear(year: Int): LocalDate =
        try {
            LocalDate(year, day.month, day.day)
        } catch (e: IllegalArgumentException) {
            LocalDate(year, day.month, day.day - 1)
        }
    var date = onYear(today.year)
    if (date < today) date = onYear(today.year + 1)
    return date.atTime(LocalTime(22, 0)).toInstant(zone)
}
