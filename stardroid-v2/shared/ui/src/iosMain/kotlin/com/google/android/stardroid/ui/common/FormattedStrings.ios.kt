/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale

internal actual fun formatForLocale(
    template: String,
    args: Array<out Any>,
): String = formatAndroidStyle(template, args, currentNumberSymbols())

/** The current locale's digits and separators, from Foundation's own number formatting. */
private fun currentNumberSymbols(): NumberSymbols {
    val formatter =
        NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterDecimalStyle
        }
    return NumberSymbols(
        zeroDigit = formatter.stringFromNumber(NSNumber(int = 0))?.firstOrNull() ?: '0',
        decimalSeparator = formatter.decimalSeparator.firstOrNull() ?: '.',
        groupingSeparator = formatter.groupingSeparator.firstOrNull() ?: ',',
    )
}
