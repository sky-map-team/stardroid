/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Window insets for the top app bar of an opaque full-screen destination (Options, Gallery,
 * Help, Settings, Diagnostics, compass calibration).
 *
 * The app runs under a fullscreen theme that hides the status bar, so Material 3's default top
 * bar insets (`systemBars`) collapse to ~0 and — crucially — never account for the display
 * cutout. On a notched phone that slides the screen's title under the camera cutout. Using
 * `safeDrawing` keeps the cutout inset (plus the top/side system-bar space when it exists), so
 * titles clear the notch in both portrait and landscape, and every destination's back arrow
 * sits at the same height. The 8 dp floor is air for the arrow on cutout-less devices, where
 * safeDrawing's top is 0 under the fullscreen theme.
 */
@Composable
fun topBarWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        .union(WindowInsets(top = MIN_TOP_DP.dp))

/** Air above the bar when no cutout or status bar pads it (fullscreen theme). */
private const val MIN_TOP_DP = 8
