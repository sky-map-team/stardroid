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
import androidx.compose.runtime.Composable

/**
 * Window insets for the top app bar of an opaque full-screen destination (Gallery, Help,
 * Settings, Diagnostics, compass calibration), so its title clears the camera cutout or notch.
 *
 * Android runs fullscreen, where Material 3's default bar insets miss the cutout; passing
 * [title] lets it measure whether the title can ride beside the cutout instead (see the
 * Android actual). iOS keeps its status bar, so the safe area alone is the answer there.
 * Bars with trailing action icons should pass no title.
 */
@Composable
expect fun topBarWindowInsets(title: String? = null): WindowInsets
