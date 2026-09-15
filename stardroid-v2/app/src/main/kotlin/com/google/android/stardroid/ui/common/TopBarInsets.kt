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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Window insets for the top app bar of an opaque full-screen destination (Gallery, Help,
 * Settings, Diagnostics, compass calibration).
 *
 * The app runs under a fullscreen theme that hides the status bar, so Material 3's default top
 * bar insets (`systemBars`) collapse to ~0 and — crucially — never account for the display
 * cutout. On a notched phone that slides the screen's title under the camera cutout. Using
 * `safeDrawing` keeps the cutout inset (plus the top/side system-bar space when it exists), so
 * titles clear the notch in both portrait and landscape.
 *
 * Passing [title] — for bars whose only top-band content is the nav icon and the title —
 * enables a refinement: when no cutout horizontally overlaps the bar's leading band (nav icon
 * plus the measured collapsed title), the top inset is dropped and the title rides high beside
 * the cutout instead of always ducking below it. The check is inherently language-specific: a
 * short English title can clear a right-shifted punch hole that its longer German translation
 * would hit, which is why the title is measured rather than assumed. Bars with trailing action
 * icons should pass no title — the band check ignores the trailing edge.
 */
@Composable
fun topBarWindowInsets(title: String? = null): WindowInsets {
    val full =
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    if (title == null) return full
    val density = LocalDensity.current
    // A visible status bar owns the top band regardless of where the cutout sits.
    if (WindowInsets.statusBars.getTop(density) > 0) return full
    val view = LocalView.current
    val cutouts = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
    // No cutout: safeDrawing's top is already ~0 under the fullscreen theme.
    if (cutouts.isEmpty()) return full
    val titleWidthPx =
        rememberTextMeasurer().measure(title, MaterialTheme.typography.titleLarge).size.width
    val bandPx =
        with(density) { (TITLE_START_DP + TITLE_SLACK_DP).dp.toPx() } + titleWidthPx
    val barHeightPx = with(density) { COLLAPSED_BAR_HEIGHT_DP.dp.toPx() }
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val clashes =
        cutouts.any { rect ->
            rect.top < barHeightPx &&
                if (ltr) rect.left < bandPx else rect.right > view.width - bandPx
        }
    return if (clashes) full else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
}

/** M3 collapsed-bar title x-origin: 16 dp start padding + the 48 dp nav-icon slot. */
private const val TITLE_START_DP = 64

/** The collapsed bar band a cutout must vertically reach into to matter. */
private const val COLLAPSED_BAR_HEIGHT_DP = 64

/** Air between the measured title and the cutout before the bar ducks below it. */
private const val TITLE_SLACK_DP = 16
