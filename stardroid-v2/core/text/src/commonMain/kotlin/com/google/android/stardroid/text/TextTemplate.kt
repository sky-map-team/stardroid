/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.text

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Fills a string resource's printf-style placeholders where the platform cannot do it for us.
 *
 * Supports the forms the app's strings use: `%1$s`, `%1$d`, `%1$02d`, `%1$.1f`, `%1$+.1f`
 * and `%%`. Anything else fails, so a new form is noticed rather than printed raw.
 */
object TextTemplate {
    private val placeholder = Regex("%(?:(\\d+)\\$([-+0]*)(\\d*)(?:\\.(\\d+))?([sdf])|%)")

    fun fill(
        template: String,
        args: Array<out Any>,
    ): String =
        placeholder.replace(template) { match ->
            if (match.value == "%%") return@replace "%"
            val (index, flags, width, precision, conversion) = match.destructured
            val arg = args.getOrNull(index.toInt() - 1) ?: error("No argument $index for $template")
            pad(
                when (conversion) {
                    "s" -> arg.toString()
                    "d" -> signed((arg as Number).toLong().toString(), flags)
                    else ->
                        signed(
                            fixed((arg as Number).toDouble(), precision.toIntOrNull() ?: 6),
                            flags,
                        )
                },
                flags,
                width.toIntOrNull() ?: 0,
            )
        }

    private fun signed(
        text: String,
        flags: String,
    ) = if ('+' in flags && !text.startsWith("-")) "+$text" else text

    private fun pad(
        text: String,
        flags: String,
        width: Int,
    ): String =
        when {
            text.length >= width -> text
            '-' in flags -> text.padEnd(width)
            '0' in flags -> {
                val sign = text.takeWhile { it == '-' || it == '+' }
                sign + text.drop(sign.length).padStart(width - sign.length, '0')
            }
            else -> text.padStart(width)
        }

    private fun fixed(
        value: Double,
        decimals: Int,
    ): String {
        val scale = 10.0.pow(decimals)
        val scaled = (abs(value) * scale).roundToLong()
        val whole = scaled / scale.toLong()
        val fraction = (scaled % scale.toLong()).toString().padStart(decimals, '0')
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return if (decimals == 0) "$sign$whole" else "$sign$whole.$fraction"
    }
}
