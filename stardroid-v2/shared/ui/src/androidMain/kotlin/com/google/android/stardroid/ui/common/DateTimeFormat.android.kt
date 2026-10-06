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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.Instant
import java.text.DateFormat
import java.util.Date

@Composable
internal actual fun rememberDateTimeFormatter(): (Instant) -> String {
    // Key on the configuration so a runtime locale change re-creates the formatter under the
    // new locale (a bare remember would keep the stale one if the activity isn't recreated).
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val formatter: (Instant) -> String = { format.format(Date(it.toEpochMilliseconds())) }
        formatter
    }
}

@Composable
internal actual fun rememberIs24HourClock(): Boolean {
    val context = LocalContext.current
    return remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
}
