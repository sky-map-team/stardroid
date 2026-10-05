/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [formatAndroidStyle] against the real thing: Java's `String.format`, in locales whose digits
 * and separators differ, over the specifiers the strings use and awkward values.
 */
class AndroidFormatOracleTest {
    private val locales =
        listOf(
            Locale.US,
            Locale.GERMANY,
            Locale.FRANCE,
            Locale.forLanguageTag("ar-EG"),
            Locale.forLanguageTag("fa-IR"),
            Locale.forLanguageTag("hi-IN"),
        )
    private val templates =
        listOf("%1\$.2f", "%1\$.1f", "%1\$+.1f", "%1\$.4f", "%.6f", "%1\$.0f", "%,.2f")
    private val values =
        listOf(
            0.0, -0.0, 0.125, 2.675, 9.99, -9.95, 0.00001, 0.0000006, 1234567.891,
            1.0e-10, 359.99999, -0.04, 45.0, 1.5, 2.5, 123456789.0, 1.0e21,
        )

    @Test
    fun `decimals match String format`() {
        for (locale in locales) {
            val symbols = symbolsOf(locale)
            for (template in templates) {
                for (value in values) {
                    assertEquals(
                        String.format(locale, template, value),
                        formatAndroidStyle(template, arrayOf(value), symbols),
                        "$template of $value in $locale",
                    )
                }
            }
        }
    }

    @Test
    fun `integers match String format`() {
        for (locale in locales) {
            val symbols = symbolsOf(locale)
            for (template in listOf("%1\$d", "%2\$02d", "%,d", "%+d", "%5d", "%-5d|", "%05d")) {
                for (value in listOf(0, 7, -7, 42, 1234567, Int.MIN_VALUE)) {
                    val args = arrayOf<Any>(value, value)
                    assertEquals(
                        String.format(locale, template, *args),
                        formatAndroidStyle(template, args, symbols),
                        "$template of $value in $locale",
                    )
                }
            }
        }
    }

    private fun symbolsOf(locale: Locale): NumberSymbols {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        return NumberSymbols(symbols.zeroDigit, symbols.decimalSeparator, symbols.groupingSeparator)
    }
}
