/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

private const val MILLIS_PER_SECOND = 1000.0

/** The device's short time format, made once and read in the locale current at the call. */
internal fun deviceTimeFormat(): (Instant) -> String {
    val formatter =
        NSDateFormatter().apply {
            timeStyle = NSDateFormatterShortStyle
            dateStyle = NSDateFormatterNoStyle
        }
    return { time ->
        formatter.stringFromDate(
            NSDate.dateWithTimeIntervalSince1970(time.toEpochMilliseconds() / MILLIS_PER_SECOND),
        )
    }
}
