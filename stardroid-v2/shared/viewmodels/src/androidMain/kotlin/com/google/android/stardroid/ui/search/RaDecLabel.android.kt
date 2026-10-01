/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.search

import java.util.Locale

internal actual fun formatRaDecLabel(
    hours: Int,
    minutes: Int,
    decDeg: Double,
): String = String.format(Locale.ROOT, "%dh %02dm, %+.1f°", hours, minutes, decDeg)
