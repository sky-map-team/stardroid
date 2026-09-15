/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import com.google.android.stardroid.math.RaDec
import kotlin.math.abs

/**
 * Parses free-text sky coordinates ("12h 30m, +45°", "187.5 −45.2", "12:30:45 -45:12:30") into
 * a [RaDec], or `null` when the text is not a coordinate. Layered over the same search entry
 * point by the ViewModel: name search runs first, this parser catches what the catalog can't.
 *
 * Ported from v1's `search/CoordinateParser.kt` (already Kotlin) with `Float` → `Double` and
 * `java.util.regex` → `kotlin.text.Regex`; the splitting heuristics and unit vocabulary are
 * unchanged, pinned by the carried-over v1 test suite.
 *
 * Notable behavior: an unsuffixed single RA number ≤ 24 is treated as **hours**, larger values
 * as degrees; RA/Dec halves split on the first token with an explicit sign or degree unit,
 * falling back to sec/min-unit boundaries, then to an even split.
 */
object CoordinateParser {
    private val COORDINATE_PATTERN = Regex("""([-+]?(?:\d+\.\d+|\d+|\.\d+))\s*([^\d\s+-]*)""")

    private data class Token(
        val value: Double,
        val unit: String,
        val rawNumStr: String,
    ) {
        val hasExplicitSign: Boolean
            get() = rawNumStr.startsWith("-") || rawNumStr.startsWith("+")
        val isNegative: Boolean
            get() = rawNumStr.startsWith("-")
    }

    fun parseCoordinates(query: String?): RaDec? {
        if (query.isNullOrBlank()) return null

        val normalized =
            query
                .replace('−', '-')
                .replace('–', '-')
                .replace('—', '-')
                // Mobile keyboards and copy-pasted text smart-quote ' and " into ’/‘ and ”/“;
                // fold them (and true prime marks) back so the v1-exact unit sets below match
                // and primes act as pass-2 split markers like their straight-quote forms.
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('′', '\'')
                .replace('”', '"')
                .replace('“', '"')
                .replace('″', '"')
                .replace(',', ' ')
                // Non-breaking spaces (Wikipedia et al. format coordinates with them) fail
                // both `\s` and Char.isWhitespace, so fold them to plain spaces.
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .trim()

        val tokens = ArrayList<Token>()
        var lastEnd = 0
        for (match in COORDINATE_PATTERN.findAll(normalized)) {
            val skipped = normalized.substring(lastEnd, match.range.first)
            if (skipped.any { !it.isWhitespace() && it != ',' && it != ':' }) return null
            val numStr = match.groupValues[1]
            val unit = match.groupValues[2]
            val value = numStr.toDoubleOrNull() ?: return null
            tokens.add(Token(value, unit.lowercase(), numStr))
            lastEnd = match.range.last + 1
        }

        val skippedEnd = normalized.substring(lastEnd)
        if (skippedEnd.any { !it.isWhitespace() && it != ',' && it != ':' }) return null

        if (tokens.isEmpty()) return null

        var splitIdx = -1
        // Pass 1: strong indicators — explicit sign or degree unit on token i.
        for (i in 1 until tokens.size) {
            val t = tokens[i]
            if (t.hasExplicitSign || t.unit in DEGREE_UNITS) {
                splitIdx = i
                break
            }
        }
        // Pass 2: weaker heuristics — previous token had a sec/min unit.
        if (splitIdx == -1) {
            for (i in 1 until tokens.size) {
                val t = tokens[i]
                val prev = tokens[i - 1]
                if (prev.unit in SECOND_UNITS_STRONG) {
                    splitIdx = i
                    break
                }
                if (prev.unit in MINUTE_UNITS_STRONG && t.unit.isEmpty()) {
                    splitIdx = i
                    break
                }
            }
        }

        if (splitIdx == -1) {
            if (tokens.size % 2 == 0) {
                splitIdx = tokens.size / 2
            } else {
                return null
            }
        }

        val raTokens = tokens.subList(0, splitIdx)
        val decTokens = tokens.subList(splitIdx, tokens.size)

        if (raTokens.isEmpty() || decTokens.isEmpty()) return null

        val raDegrees = parseRa(raTokens) ?: return null
        val decDegrees = parseDec(decTokens) ?: return null

        return RaDec(raDegrees, decDegrees)
    }

    private fun parseRa(tokens: List<Token>): Double? =
        when (tokens.size) {
            1 -> {
                val t = tokens[0]
                val valInDegrees =
                    when {
                        t.unit in DEGREE_UNITS -> t.value
                        t.unit in HOUR_UNITS -> t.value * 15.0
                        // Unsuffixed: small values read as hours, larger as degrees.
                        t.unit.isEmpty() -> if (t.value <= 24.0) t.value * 15.0 else t.value
                        else -> null
                    }
                valInDegrees?.takeIf { it in 0.0..360.0 }
            }
            2 -> {
                val h = tokens[0].value
                val m = tokens[1].value
                if (!isValidRaUnit(tokens[0].unit, isHour = true) ||
                    !isValidRaUnit(tokens[1].unit, isHour = false) ||
                    h !in 0.0..24.0 || m < 0.0 || m >= 60.0
                ) {
                    null
                } else {
                    (15.0 * (h + m / 60.0)).takeIf { it in 0.0..360.0 }
                }
            }
            3 -> {
                val h = tokens[0].value
                val m = tokens[1].value
                val s = tokens[2].value
                if (!isValidRaUnit(tokens[0].unit, isHour = true) ||
                    !isValidRaUnit(tokens[1].unit, isHour = false) ||
                    !isValidRaSecUnit(tokens[2].unit) ||
                    h !in 0.0..24.0 || m < 0.0 || m >= 60.0 || s < 0.0 || s >= 60.0
                ) {
                    null
                } else {
                    (15.0 * (h + m / 60.0 + s / 3600.0)).takeIf { it in 0.0..360.0 }
                }
            }
            else -> null
        }

    private fun parseDec(tokens: List<Token>): Double? =
        when (tokens.size) {
            1 -> {
                val t = tokens[0]
                if (t.unit.isNotEmpty() && t.unit !in DEGREE_UNITS) {
                    null
                } else {
                    t.value.takeIf { it in -90.0..90.0 }
                }
            }
            2 -> {
                val dToken = tokens[0]
                val mToken = tokens[1]
                if (!isValidDecUnit(dToken.unit, isDeg = true) ||
                    !isValidDecUnit(mToken.unit, isDeg = false)
                ) {
                    null
                } else {
                    val sign = if (dToken.isNegative) -1.0 else 1.0
                    val d = abs(dToken.value)
                    val m = abs(mToken.value)
                    if (d > 90.0 || m >= 60.0) {
                        null
                    } else {
                        (sign * (d + m / 60.0)).takeIf { it in -90.0..90.0 }
                    }
                }
            }
            3 -> {
                val dToken = tokens[0]
                val mToken = tokens[1]
                val sToken = tokens[2]
                if (!isValidDecUnit(dToken.unit, isDeg = true) ||
                    !isValidDecUnit(mToken.unit, isDeg = false) ||
                    !isValidDecSecUnit(sToken.unit)
                ) {
                    null
                } else {
                    val sign = if (dToken.isNegative) -1.0 else 1.0
                    val d = abs(dToken.value)
                    val m = abs(mToken.value)
                    val s = abs(sToken.value)
                    if (d > 90.0 || m >= 60.0 || s >= 60.0) {
                        null
                    } else {
                        (sign * (d + m / 60.0 + s / 3600.0)).takeIf { it in -90.0..90.0 }
                    }
                }
            }
            else -> null
        }

    private fun isValidRaUnit(
        unit: String,
        isHour: Boolean,
    ): Boolean {
        if (unit.isEmpty() || unit == ":") return true
        return if (isHour) unit in HOUR_UNITS else unit in MINUTE_UNITS
    }

    private fun isValidRaSecUnit(unit: String): Boolean = unit.isEmpty() || unit in SECOND_UNITS

    private fun isValidDecUnit(
        unit: String,
        isDeg: Boolean,
    ): Boolean {
        if (unit.isEmpty() || unit == ":") return true
        return if (isDeg) unit in DEGREE_UNITS else unit in MINUTE_UNITS
    }

    private fun isValidDecSecUnit(unit: String): Boolean = unit.isEmpty() || unit in SECOND_UNITS

    private val DEGREE_UNITS = setOf("d", "deg", "degree", "degrees", "°", "o")
    private val HOUR_UNITS = setOf("h", "hr", "hour", "hours")

    // The normalization pass above folds ′/″ (and smart quotes) to '/" before tokenizing, so
    // only the straight-quote forms can ever reach these sets.
    private val MINUTE_UNITS = setOf("m", "min", "minute", "minutes", "'")
    private val SECOND_UNITS = setOf("s", "sec", "second", "seconds", "\"")

    /** The units pass 2 of the splitter treats as end-of-RA markers (v1's exact sets). */
    private val SECOND_UNITS_STRONG = setOf("s", "sec", "second", "\"")
    private val MINUTE_UNITS_STRONG = setOf("m", "min", "minute", "'")
}
