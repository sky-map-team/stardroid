package com.google.android.stardroid.search

import com.google.android.stardroid.math.RaDec
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

object CoordinateParser {

    private val COORDINATE_PATTERN: Pattern =
        Pattern.compile("([-+]?(?:\\d+\\.\\d+|\\d+|\\.\\d+))\\s*([^\\d\\s\\+-]*)")

    private data class Token(
        val value: Float,
        val unit: String,
        val rawNumStr: String
    ) {
        val hasExplicitSign: Boolean
            get() = rawNumStr.startsWith("-") || rawNumStr.startsWith("+")
        val isNegative: Boolean
            get() = rawNumStr.startsWith("-")
    }

    fun parseCoordinates(query: String?): RaDec? {
        if (query.isNullOrBlank()) return null

        val normalized = query
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace(',', ' ')
            .trim()

        val tokens = ArrayList<Token>()
        val matcher = COORDINATE_PATTERN.matcher(normalized)
        var lastEnd = 0

        while (matcher.find()) {
            val skipped = normalized.substring(lastEnd, matcher.start())
            if (skipped.any { !it.isWhitespace() && it != ',' && it != ':' }) return null
            val numStr = matcher.group(1) ?: ""
            val unit = matcher.group(2) ?: ""
            val value = numStr.toFloatOrNull() ?: return null
            tokens.add(Token(value, unit.lowercase(Locale.US), numStr))
            lastEnd = matcher.end()
        }

        val skippedEnd = normalized.substring(lastEnd)
        if (skippedEnd.any { !it.isWhitespace() && it != ',' && it != ':' }) return null

        if (tokens.isEmpty()) return null

        var splitIdx = -1
        // Pass 1: strong indicators — explicit sign or degree unit on token i
        for (i in 1 until tokens.size) {
            val t = tokens[i]
            if (t.hasExplicitSign || t.unit == "d" || t.unit == "deg" || t.unit == "degree" ||
                t.unit == "degrees" || t.unit == "°" || t.unit == "o"
            ) {
                splitIdx = i
                break
            }
        }
        // Pass 2: weaker heuristics — previous token had a sec/min unit
        if (splitIdx == -1) {
            for (i in 1 until tokens.size) {
                val t = tokens[i]
                val prev = tokens[i - 1]
                if (prev.unit == "s" || prev.unit == "sec" || prev.unit == "second" ||
                    prev.unit == "\""
                ) {
                    splitIdx = i
                    break
                }
                if ((prev.unit == "m" || prev.unit == "min" || prev.unit == "minute" ||
                    prev.unit == "'") && t.unit.isEmpty()
                ) {
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

    private fun parseRa(tokens: List<Token>): Float? {
        return when (tokens.size) {
            1 -> {
                val t = tokens[0]
                val valInDegrees = when (t.unit) {
                    "d", "deg", "degree", "degrees", "°", "o" -> t.value
                    "h", "hr", "hour", "hours" -> t.value * 15.0f
                    "" -> {
                        if (t.value <= 24.0f) {
                            t.value * 15.0f
                        } else {
                            t.value
                        }
                    }
                    else -> return null
                }
                if (valInDegrees < 0f || valInDegrees > 360f) null else valInDegrees
            }
            2 -> {
                val h = tokens[0].value
                val m = tokens[1].value
                if (!isValidRaUnit(tokens[0].unit, isHour = true) ||
                    !isValidRaUnit(tokens[1].unit, isHour = false)
                ) {
                    return null
                }
                if (h < 0f || h > 24f || m < 0f || m >= 60f) return null
                val deg = 15.0f * (h + m / 60.0f)
                if (deg < 0f || deg > 360f) null else deg
            }
            3 -> {
                val h = tokens[0].value
                val m = tokens[1].value
                val s = tokens[2].value
                if (!isValidRaUnit(tokens[0].unit, isHour = true) ||
                    !isValidRaUnit(tokens[1].unit, isHour = false) ||
                    !isValidRaSecUnit(tokens[2].unit)
                ) {
                    return null
                }
                if (h < 0f || h > 24f || m < 0f || m >= 60f || s < 0f || s >= 60f) return null
                val deg = 15.0f * (h + m / 60.0f + s / 3600.0f)
                if (deg < 0f || deg > 360f) null else deg
            }
            else -> null
        }
    }

    private fun isValidRaUnit(unit: String, isHour: Boolean): Boolean {
        if (unit.isEmpty() || unit == ":") return true
        return if (isHour) {
            unit == "h" || unit == "hr" || unit == "hour" || unit == "hours"
        } else {
            unit == "m" || unit == "min" || unit == "minute" || unit == "minutes" ||
                unit == "'" || unit == "′"
        }
    }

    private fun isValidRaSecUnit(unit: String): Boolean {
        return unit.isEmpty() || unit == "s" || unit == "sec" || unit == "second" ||
            unit == "seconds" || unit == "\"" || unit == "″"
    }

    private fun parseDec(tokens: List<Token>): Float? {
        return when (tokens.size) {
            1 -> {
                val t = tokens[0]
                if (t.unit.isNotEmpty() && t.unit != "d" && t.unit != "deg" &&
                    t.unit != "degree" && t.unit != "degrees" && t.unit != "°" && t.unit != "o"
                ) {
                    return null
                }
                if (t.value < -90f || t.value > 90f) null else t.value
            }
            2 -> {
                val dToken = tokens[0]
                val mToken = tokens[1]
                if (!isValidDecUnit(dToken.unit, isDeg = true) ||
                    !isValidDecUnit(mToken.unit, isDeg = false)
                ) {
                    return null
                }
                val sign = if (dToken.isNegative) -1.0f else 1.0f
                val d = abs(dToken.value)
                val m = abs(mToken.value)
                if (d > 90f || m >= 60f) return null
                val deg = sign * (d + m / 60.0f)
                if (deg < -90f || deg > 90f) null else deg
            }
            3 -> {
                val dToken = tokens[0]
                val mToken = tokens[1]
                val sToken = tokens[2]
                if (!isValidDecUnit(dToken.unit, isDeg = true) ||
                    !isValidDecUnit(mToken.unit, isDeg = false) ||
                    !isValidDecSecUnit(sToken.unit)
                ) {
                    return null
                }
                val sign = if (dToken.isNegative) -1.0f else 1.0f
                val d = abs(dToken.value)
                val m = abs(mToken.value)
                val s = abs(sToken.value)
                if (d > 90f || m >= 60f || s >= 60f) return null
                val deg = sign * (d + m / 60.0f + s / 3600.0f)
                if (deg < -90f || deg > 90f) null else deg
            }
            else -> null
        }
    }

    private fun isValidDecUnit(unit: String, isDeg: Boolean): Boolean {
        if (unit.isEmpty() || unit == ":") return true
        return if (isDeg) {
            unit == "d" || unit == "deg" || unit == "degree" || unit == "degrees" ||
                unit == "°" || unit == "o"
        } else {
            unit == "m" || unit == "min" || unit == "minute" || unit == "minutes" ||
                unit == "'" || unit == "′"
        }
    }

    private fun isValidDecSecUnit(unit: String): Boolean {
        return unit.isEmpty() || unit == "s" || unit == "sec" || unit == "second" ||
            unit == "seconds" || unit == "\"" || unit == "″"
    }
}
