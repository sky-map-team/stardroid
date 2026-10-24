/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.search

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

/**
 * Foundation's printf formatting, which is locale-independent and rounds as the JVM does. Only
 * numbers go through the C varargs: a Kotlin String there is not an NSString (see
 * SkyMapDatabaseFactory's log).
 */
internal actual fun formatRaDecLabel(
    hours: Int,
    minutes: Int,
    decDeg: Double,
): String = NSString.stringWithFormat("%dh %02dm, %+.1f°", hours, minutes, decDeg)
