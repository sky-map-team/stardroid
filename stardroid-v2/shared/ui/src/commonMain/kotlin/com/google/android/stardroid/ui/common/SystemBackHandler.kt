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

/**
 * Runs [onBack] for the platform's back gesture while [enabled]: Android's system back, iOS's
 * edge swipe. The most recently composed enabled handler wins, so a host's page stack drawn over
 * a screen takes back before the screen does.
 */
@Composable
internal expect fun SystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
