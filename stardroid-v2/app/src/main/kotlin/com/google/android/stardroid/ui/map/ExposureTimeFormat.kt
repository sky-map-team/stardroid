/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import kotlin.math.roundToInt

/**
 * "1/30s" above a second-fraction, "0.5s" below — the AR dev slider's readout. Formatted in the
 * device locale, as on-screen text; kept in :app with the slider when [ArExposureMath] moved to
 * shared code, which has no String.format.
 */
fun formatExposureTime(exposureTimeNs: Long): String {
    val seconds = exposureTimeNs / 1e9
    return if (seconds >= 0.25) {
        "%.1fs".format(seconds)
    } else {
        "1/${(1.0 / seconds).roundToInt()}s"
    }
}
