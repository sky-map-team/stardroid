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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.window.DialogProperties

/**
 * A catalog photo (`celestial_images/<imageRef>`: the APK's assets on Android, the app bundle on
 * iOS), decoded off the main thread; null while it loads, and if it cannot be read.
 */
@Composable
internal expect fun rememberCelestialImage(imageRef: String): ImageBitmap?

/**
 * Decodes catalog photos for a grid: each scaled down toward [targetPx] on its short side, so a
 * screenful of tiles never holds full-size photos; null if one cannot be read. Blocking: call
 * the decoder off the main thread.
 */
@Composable
internal expect fun rememberCelestialThumbnailDecoder(
    targetPx: Int,
): (imageRef: String) -> ImageBitmap?

/** A dialog that may cover the whole screen, edge to edge (the expanded photo). */
internal expect fun fullScreenDialogProperties(): DialogProperties
