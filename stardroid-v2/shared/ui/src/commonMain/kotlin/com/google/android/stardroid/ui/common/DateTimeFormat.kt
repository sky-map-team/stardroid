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
import kotlinx.datetime.Instant

/**
 * Formats an instant as the locale writes a medium date and a short time ("Oct 5, 2026, 11:04
 * PM"), remembered across recompositions so the 30 Hz time-travel readout does not build a
 * formatter per frame: Java's DateFormat on Android, as before; Foundation's on iOS.
 */
@Composable
internal expect fun rememberDateTimeFormatter(): (Instant) -> String

/** Whether the user's clock is 24-hour, for the time picker. */
@Composable
internal expect fun rememberIs24HourClock(): Boolean

/** A time of day as the user's clock shows it (12- or 24-hour), as Android's getTimeFormat. */
@Composable
internal expect fun rememberTimeFormatter(): (Instant) -> String

/** A medium date ("Oct 5, 2026"), for events weeks away, where a bare time isn't enough. */
@Composable
internal expect fun rememberDateFormatter(): (Instant) -> String
