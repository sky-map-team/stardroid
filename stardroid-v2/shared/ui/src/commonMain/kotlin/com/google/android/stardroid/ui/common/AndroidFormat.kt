/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

/** The locale's number symbols, as `java.text.DecimalFormatSymbols` gives them to `Formatter`. */
data class NumberSymbols(
    val zeroDigit: Char = '0',
    val decimalSeparator: Char = '.',
    val groupingSeparator: Char = ',',
)

/**
 * `String.format(locale, template, *args)` for the specifiers the strings use, where Java's
 * `Formatter` is not available (iOS): `%[n$][flags][width][.precision]conversion` with the `s`,
 * `d`, `f` and `%` conversions and the `-`, `+`, ` `, `0` and `,` flags, as Java formats them.
 *
 * - An argument index (`%2$s`) picks that argument; a specifier without one takes the next.
 * - `d` and `f` write the locale's digits (from [NumberSymbols.zeroDigit]), and `f` its decimal
 *   separator; signs stay ASCII, as Java writes them.
 * - `f` rounds half up from the number's shortest decimal form, as Java does: 2.675 is "2.68"
 *   at two places, where C's printf gives "2.67".
 *
 * Anything else is copied as is, as Android would have failed on it at the format call.
 */
fun formatAndroidStyle(
    template: String,
    args: Array<out Any?>,
    symbols: NumberSymbols = NumberSymbols(),
): String {
    val out = StringBuilder(template.length + 16)
    var next = 0
    var index = 0
    while (index < template.length) {
        val match = SPECIFIER.matchAt(template, index)
        if (template[index] != '%' || match == null) {
            out.append(template[index++])
            continue
        }
        index = match.range.last + 1
        val (position, flags, width, precision, conversion) = match.destructured
        if (conversion == "%") {
            out.append('%')
            continue
        }
        if (conversion == "n") {
            out.append('\n')
            continue
        }
        val argument =
            if (position.isEmpty()) args.getOrNull(next++) else args.getOrNull(position.toInt() - 1)
        val body =
            when (conversion) {
                "s", "S" ->
                    argument.toString()
                        .let { if (precision.isEmpty()) it else it.take(precision.toInt()) }
                        .let { if (conversion == "S") it.uppercase() else it }
                "d" -> integer((argument as Number).toLong(), flags, width, symbols)
                else -> decimal((argument as Number).toDouble(), flags, width, precision, symbols)
            }
        out.append(pad(body, flags, width))
    }
    return out.toString()
}

private val SPECIFIER = Regex("""%(?:(\d+)\$)?([-+ 0,]*)(\d*)(?:\.(\d+))?([sSdf%n])""")

private fun integer(
    value: Long,
    flags: String,
    width: String,
    symbols: NumberSymbols,
): String {
    val digits = value.toString().removePrefix("-")
    return signed(value < 0, flags, width, grouped(digits, flags, symbols), symbols)
}

private fun decimal(
    value: Double,
    flags: String,
    width: String,
    precision: String,
    symbols: NumberSymbols,
): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    val places = if (precision.isEmpty()) 6 else precision.toInt()
    val (whole, fraction) = roundHalfUp(kotlin.math.abs(value), places)
    val magnitude =
        grouped(whole, flags, symbols) +
            if (places == 0) "" else symbols.decimalSeparator + localized(fraction, symbols)
    val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
    return signed(negative, flags, width, magnitude, symbols)
}

/** The sign Java writes before the magnitude, and its zero padding (which goes after the sign). */
private fun signed(
    negative: Boolean,
    flags: String,
    width: String,
    magnitude: String,
    symbols: NumberSymbols,
): String {
    val sign =
        when {
            negative -> "-"
            '+' in flags -> "+"
            ' ' in flags -> " "
            else -> ""
        }
    if ('0' !in flags || width.isEmpty()) return sign + magnitude
    val zeros = (width.toInt() - sign.length - magnitude.length).coerceAtLeast(0)
    return sign + symbols.zeroDigit.toString().repeat(zeros) + magnitude
}

private fun pad(
    body: String,
    flags: String,
    width: String,
): String {
    if (width.isEmpty() || body.length >= width.toInt()) return body
    val spaces = " ".repeat(width.toInt() - body.length)
    return if ('-' in flags) body + spaces else spaces + body
}

/** The integer digits, localized, and grouped in threes when the `,` flag asks. */
private fun grouped(
    digits: String,
    flags: String,
    symbols: NumberSymbols,
): String {
    val local = localized(digits, symbols)
    if (',' !in flags) return local
    return local.reversed().chunked(3).joinToString(symbols.groupingSeparator.toString()).reversed()
}

private fun localized(
    digits: String,
    symbols: NumberSymbols,
): String {
    if (symbols.zeroDigit == '0') return digits
    return digits.map { if (it in '0'..'9') symbols.zeroDigit + (it - '0') else it }.joinToString(
        "",
    )
}

/**
 * [value] (not negative) rounded half up to [places] decimals, from its shortest decimal form,
 * as Java's `FormattedFloatingDecimal` does: the whole and fractional digit strings.
 */
internal fun roundHalfUp(
    value: Double,
    places: Int,
): Pair<String, String> {
    // The shortest digits that read back as this double, and where the point goes among them.
    val text = value.toString()
    val mantissa = text.substringBefore('E').substringBefore('e')
    val exponent =
        if (mantissa.length < text.length) {
            text.substring(
                mantissa.length + 1,
            ).toInt()
        } else {
            0
        }
    val point = mantissa.indexOf('.').let { if (it < 0) mantissa.length else it }
    var digits = mantissa.replace(".", "").trimStart('0')
    var pointAt = point + exponent - (mantissa.replace(".", "").length - digits.length)
    if (digits.isEmpty()) {
        digits = "0"
        pointAt = 1
    }
    // Keep pointAt + places digits, rounding on the first dropped one.
    val keep = pointAt + places
    if (keep < 0) return "0" to "0".repeat(places)
    var kept = digits.take(keep).padEnd(keep, '0')
    val dropped = digits.getOrNull(keep)
    if (dropped != null && dropped >= '5') kept = increment(kept)
    // Rounding up may have added a digit at the front: 9.99 to one place is 10.0.
    val wholeLength = kept.length - places
    val whole = kept.take(wholeLength.coerceAtLeast(0)).trimStart('0').ifEmpty { "0" }
    val fraction = kept.takeLast(places).padStart(places, '0')
    return whole to fraction
}

private fun increment(digits: String): String {
    if (digits.isEmpty()) return "1"
    val chars = digits.toCharArray()
    var i = chars.lastIndex
    while (i >= 0) {
        if (chars[i] != '9') {
            chars[i] = chars[i] + 1
            return chars.concatToString()
        }
        chars[i] = '0'
        i--
    }
    return "1" + chars.concatToString()
}
