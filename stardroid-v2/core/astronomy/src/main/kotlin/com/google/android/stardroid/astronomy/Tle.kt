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
import com.google.android.stardroid.math.TWO_PI
import kotlinx.datetime.Instant
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

private const val MINUTES_PER_DAY: Double = 1440.0

private const val SECONDS_PER_DAY: Double = 86_400.0

/**
 * Orbital period, in minutes, at or above which an element set is "deep space" and must be
 * propagated by SDP4 rather than SGP4 (Spacetrack Report #3). See [Tle.isDeepSpace].
 */
private const val DEEP_SPACE_PERIOD_MINUTES: Double = 225.0

/** Fixed columns of a TLE line, 1-based and inclusive, as the format specifies them. */
private const val TLE_LINE_LENGTH = 69

/**
 * A two-line element set: the mean orbital elements of an Earth satellite at a given epoch, in the
 * form NORAD/CelesTrak distribute them.
 *
 * These are *not* osculating Keplerian elements and must not be fed to [KeplerianEphemeris]. They
 * are mean elements in the particular averaged sense SGP4 defines, and are only meaningful when
 * propagated by [Sgp4], which undoes the same averaging. Angles are radians and the mean motion is
 * radians per minute, matching SGP4's native units so the propagator does no conversion of its own.
 *
 * TLEs go stale: atmospheric drag in low Earth orbit varies with solar activity, so element sets
 * are re-issued roughly daily and positional error grows from about 1 km at one day old to tens of
 * km at ten days. See docs/design/satellite-tracking.md for the freshness policy that follows from
 * that.
 */
data class Tle(
    /** The object's common name, e.g. `ISS (ZARYA)`. Empty when parsed from a bare two-line set. */
    val name: String,
    /** NORAD catalog number, the stable identifier for the object. */
    val noradId: Int,
    /** The instant the elements are referred to. */
    val epoch: Instant,
    val inclinationRad: Double,
    /** Right ascension of the ascending node. */
    val raanRad: Double,
    val eccentricity: Double,
    val argOfPerigeeRad: Double,
    val meanAnomalyRad: Double,
    val meanMotionRadPerMin: Double,
    /**
     * The SGP4 drag term, in inverse Earth radii. Not a physical ballistic coefficient — it is
     * whatever value made the fit work, and is regularly negative.
     */
    val bstar: Double,
    /**
     * First derivative of mean motion divided by two, in revolutions per day squared, as the
     * format stores it. SGP4 does not use it; retained because it is part of the element set and
     * is the field a human reads to see how fast the orbit is decaying.
     */
    val meanMotionDotRevPerDay2: Double,
    /** Second derivative of mean motion divided by six. Unused by SGP4, retained for the same reason. */
    val meanMotionDdotRevPerDay3: Double,
) {
    /** Orbital period in minutes, from the mean motion. */
    val periodMinutes: Double get() = TWO_PI / meanMotionRadPerMin

    /**
     * True when this element set needs SDP4's deep-space corrections (period ≥ 225 minutes) and so
     * cannot be propagated by [Sgp4].
     *
     * This guard is load-bearing rather than tidy: running SGP4 on a deep-space element set does
     * not degrade gracefully, it produces grossly wrong positions with no outward sign. Everything
     * this app tracks is low Earth orbit, where periods are ~90–100 minutes, so refusing the rest
     * converts a silent-wrong-answer bug class into a clean absence.
     */
    val isDeepSpace: Boolean get() = periodMinutes >= DEEP_SPACE_PERIOD_MINUTES

    /** Minutes elapsed from [epoch] to [time] — the argument [Sgp4.propagate] expects. */
    fun minutesSinceEpoch(time: Instant): Double =
        (time - epoch).inWholeNanoseconds / 60_000_000_000.0

    /** How old these elements are at [time], in days. Negative before the epoch. */
    fun ageDays(time: Instant): Double = minutesSinceEpoch(time) / MINUTES_PER_DAY

    companion object {
        /**
         * Parses one element set from its two lines, with [name] taken from the preceding title
         * line of a three-line set if there was one.
         *
         * Throws [IllegalArgumentException] with the offending detail if either line is the wrong
         * length, is numbered wrongly, disagrees on the catalog number, or fails its mod-10
         * checksum. Deep-space sets parse successfully — [isDeepSpace] reports them, and it is
         * [Sgp4] that refuses them.
         */
        fun parse(
            line1: String,
            line2: String,
            name: String = "",
        ): Tle {
            val one = validated(line1, expectedLineNumber = '1')
            val two = validated(line2, expectedLineNumber = '2')

            val noradId = one.intField(3, 7)
            require(noradId == two.intField(3, 7)) {
                "TLE lines are for different satellites: $noradId and ${two.intField(3, 7)}"
            }

            return Tle(
                name = name.trim(),
                noradId = noradId,
                epoch = parseEpoch(one.intField(19, 20), one.doubleField(21, 32)),
                inclinationRad = two.doubleField(9, 16).degreesToRadians(),
                raanRad = two.doubleField(18, 25).degreesToRadians(),
                eccentricity = "0.${two.field(27, 33).trim()}".toDouble(),
                argOfPerigeeRad = two.doubleField(35, 42).degreesToRadians(),
                meanAnomalyRad = two.doubleField(44, 51).degreesToRadians(),
                meanMotionRadPerMin = two.doubleField(53, 63) * TWO_PI / MINUTES_PER_DAY,
                bstar = one.assumedDecimalField(54, 61),
                meanMotionDotRevPerDay2 = one.doubleField(34, 43),
                meanMotionDdotRevPerDay3 = one.assumedDecimalField(45, 52),
            )
        }

        /**
         * Parses a CelesTrak catalog response — two-line sets, optionally each preceded by a title
         * line — keeping only the sets this app can actually propagate.
         *
         * Malformed and deep-space entries are skipped rather than raised. A satellite feed is
         * third-party text fetched over the network, and one bad line in a hundred should cost the
         * one satellite, not the whole download.
         */
        fun parseCatalog(text: String): List<Tle> {
            val lines = text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
            val result = mutableListOf<Tle>()
            var index = 0
            var pendingName = ""
            while (index < lines.size) {
                val line = lines[index]
                if (!line.startsWith("1 ")) {
                    pendingName = line
                    index++
                    continue
                }
                val second = lines.getOrNull(index + 1)
                if (second == null || !second.startsWith("2 ")) {
                    index++
                    continue
                }
                runCatching { parse(line, second, pendingName) }
                    .getOrNull()
                    ?.takeUnless { it.isDeepSpace }
                    ?.let(result::add)
                pendingName = ""
                index += 2
            }
            return result
        }

        /**
         * The two-digit epoch year the format still uses, resolved by the conventional pivot: 57
         * through 99 are 1957–1999 (Sputnik onward), 00 through 56 are 2000–2056.
         */
        internal fun epochYear(twoDigitYear: Int): Int =
            if (twoDigitYear >= 57) 1900 + twoDigitYear else 2000 + twoDigitYear

        /**
         * The mod-10 checksum in column 69: the sum of the digits in columns 1–68, counting each
         * minus sign as 1 and every other character as 0.
         */
        internal fun checksum(line: String): Int =
            line.take(TLE_LINE_LENGTH - 1).sumOf {
                when {
                    it.isDigit() -> it - '0'
                    it == '-' -> 1
                    else -> 0
                }
            } % 10

        private fun validated(
            line: String,
            expectedLineNumber: Char,
        ): String {
            // Real feeds pad or append (SGP4-VER.TLE carries trailing test parameters), so only
            // the fixed 69-column prefix is significant.
            require(line.length >= TLE_LINE_LENGTH) {
                "TLE line $expectedLineNumber is ${line.length} chars, need $TLE_LINE_LENGTH: $line"
            }
            require(line[0] == expectedLineNumber) {
                "Expected TLE line $expectedLineNumber, got line number '${line[0]}'"
            }
            val stated = line[TLE_LINE_LENGTH - 1]
            require(stated.isDigit() && stated - '0' == checksum(line)) {
                "TLE line $expectedLineNumber checksum is '$stated', computed ${checksum(line)}"
            }
            return line
        }

        private fun parseEpoch(
            twoDigitYear: Int,
            dayOfYear: Double,
        ): Instant {
            require(dayOfYear >= 1.0) { "TLE epoch day-of-year must be >= 1, was $dayOfYear" }
            // Nanosecond resolution, not milliseconds: the epoch fraction carries eight decimal
            // places of a day (~1 ms), and at 7.5 km/s a rounded millisecond is metres of error —
            // enough to swamp the tolerances the Vallado vectors are checked to.
            val secondsIntoYear = (dayOfYear - 1.0) * SECONDS_PER_DAY
            val wholeSeconds = floor(secondsIntoYear)
            val nanoseconds = ((secondsIntoYear - wholeSeconds) * 1e9).roundToLong()
            val yearStart = Instant.parse("${epochYear(twoDigitYear)}-01-01T00:00:00Z")
            return Instant.fromEpochSeconds(
                yearStart.epochSeconds + wholeSeconds.toLong(),
                nanoseconds,
            )
        }
    }
}

/** Columns [from]..[to], 1-based and inclusive, as the TLE format numbers them. */
private fun String.field(
    from: Int,
    to: Int,
): String = substring(from - 1, to)

private fun String.intField(
    from: Int,
    to: Int,
): Int = field(from, to).trim().toInt()

private fun String.doubleField(
    from: Int,
    to: Int,
): Double = field(from, to).trim().toDouble()

/**
 * A field in the format's exponent notation, where the leading decimal point is implied and the
 * exponent's sign is its delimiter: `12808-3` is 0.12808e-3, `-11606-4` is -0.11606e-4, and a
 * blank or all-zero field is zero.
 */
private fun String.assumedDecimalField(
    from: Int,
    to: Int,
): Double {
    val text = field(from, to).trim()
    if (text.isEmpty()) return 0.0
    val exponentAt = text.indexOfLast { it == '+' || it == '-' }
    val mantissaText = if (exponentAt > 0) text.substring(0, exponentAt) else text
    val exponent = if (exponentAt > 0) text.substring(exponentAt).toInt() else 0
    val digits = mantissaText.trimStart('+', '-')
    require(digits.isNotEmpty() && digits.all(Char::isDigit)) {
        "Malformed assumed-decimal TLE field '$text'"
    }
    val magnitude = "0.$digits".toDouble()
    val signed = if (mantissaText.startsWith("-")) -magnitude else magnitude
    return signed * 10.0.pow(exponent)
}

private fun Double.degreesToRadians(): Double = this * DEGREES_TO_RADIANS
