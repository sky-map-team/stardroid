/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

// iOS restarts an app whose language or region changes, so the formatter needs no re-keying.
@Composable
internal actual fun rememberDateTimeFormatter(): (Instant) -> String =
    remember {
        val format =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterMediumStyle
                timeStyle = NSDateFormatterShortStyle
                locale = NSLocale.currentLocale
            }
        val formatter: (Instant) -> String = {
            format.stringFromDate(
                NSDate.dateWithTimeIntervalSince1970(it.toEpochMilliseconds() / 1000.0),
            )
        }
        formatter
    }

// The locale's hour template: an AM/PM marker ("a") in it means a 12-hour clock.
@Composable
internal actual fun rememberIs24HourClock(): Boolean =
    remember {
        val template = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)
        template?.contains('a') != true
    }
