/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** The strings' own specifiers, with the outputs Java's String.format gives. */
class AndroidFormatTest {
    private val german = NumberSymbols(decimalSeparator = ',', groupingSeparator = '.')
    private val arabic = NumberSymbols(zeroDigit = '\u0660', decimalSeparator = '\u066B')

    @Test
    fun `positional arguments pick their argument`() {
        assertEquals("b a", formatAndroidStyle("%2\$s %1\$s", arrayOf("a", "b")))
    }

    @Test
    fun `unnumbered arguments are taken in order`() {
        assertEquals("a-b", formatAndroidStyle("%s-%s", arrayOf("a", "b")))
    }

    @Test
    fun `integers and zero padding`() {
        assertEquals("5h 07m", formatAndroidStyle("%1\$dh %2\$02dm", arrayOf(5, 7)))
    }

    @Test
    fun `signed decimals`() {
        assertEquals("+1.3° -0.5°", formatAndroidStyle("%1\$+.1f° %2\$+.1f°", arrayOf(1.25, -0.5)))
    }

    @Test
    fun `rounding is half up from the shortest decimal form`() {
        assertEquals("2.68", formatAndroidStyle("%.2f", arrayOf(2.675)))
        assertEquals("0.13", formatAndroidStyle("%.2f", arrayOf(0.125)))
        assertEquals("10.0", formatAndroidStyle("%.1f", arrayOf(9.99)))
        assertEquals("0.000010", formatAndroidStyle("%.6f", arrayOf(0.00001)))
    }

    @Test
    fun `a percent sign`() {
        assertEquals("50%", formatAndroidStyle("%1\$d%%", arrayOf(50)))
    }

    @Test
    fun `the locale's decimal separator and digits`() {
        assertEquals("1,5", formatAndroidStyle("%.1f", arrayOf(1.5), german))
        assertEquals("\u0661\u066B\u0665", formatAndroidStyle("%.1f", arrayOf(1.5), arabic))
        assertEquals("\u0660\u0667", formatAndroidStyle("%02d", arrayOf(7), arabic))
    }

    @Test
    fun `grouping only when asked`() {
        assertEquals("1234567", formatAndroidStyle("%d", arrayOf(1234567), german))
        assertEquals("1.234.567", formatAndroidStyle("%,d", arrayOf(1234567), german))
    }

    @Test
    fun `text that is not a specifier is kept`() {
        assertEquals("100% in view", formatAndroidStyle("100% in view", arrayOf()))
    }
}
