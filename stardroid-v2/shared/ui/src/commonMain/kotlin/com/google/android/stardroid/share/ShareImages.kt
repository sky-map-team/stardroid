/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import androidx.compose.ui.graphics.ImageBitmap

/** What the share footer carries. [icon] is the caller's to release. */
class ShareBranding(
    val icon: ImageBitmap?,
    val appName: String,
    val invitation: String,
    val link: String,
)

/** The two AR share layouts, composited and footered. */
class ShareLayouts(
    val overlay: ImageBitmap,
    val sideBySide: ImageBitmap,
)

/**
 * Frees the pixels now: a share stacks several full-screen bitmaps, too many to leave to the GC
 * (audit-2026-08 M5). The bitmap must not be drawn again.
 */
expect fun ImageBitmap.release()
