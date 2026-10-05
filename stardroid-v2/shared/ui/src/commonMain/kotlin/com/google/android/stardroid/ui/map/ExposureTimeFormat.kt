/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import com.google.android.stardroid.ui.common.formatForLocale
import kotlin.math.roundToInt

/**
 * "1/30s" above a second-fraction, "0.5s" below — the AR dev slider's readout. Formatted in the
 * device locale, as on-screen text, through the shared UI's String.format stand-in.
 */
fun formatExposureTime(exposureTimeNs: Long): String {
    val seconds = exposureTimeNs / 1e9
    return if (seconds >= 0.25) {
        formatForLocale("%.1fs", arrayOf(seconds))
    } else {
        "1/${(1.0 / seconds).roundToInt()}s"
    }
}
