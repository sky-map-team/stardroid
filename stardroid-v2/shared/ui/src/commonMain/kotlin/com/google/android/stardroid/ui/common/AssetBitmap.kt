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

/**
 * Decodes a bundled asset into an [ImageBitmap] off the main thread (the info card's
 * `CelestialImage` pattern: v1's Coil `file:///android_asset/` load without the dependency).
 * Returns null while decoding and stays null for a missing or corrupt asset — callers simply
 * compose nothing.
 *
 * [path] is relative to Android's `assets/` tree, which the iOS app bundle mirrors folder for
 * folder (ios/SkyMap/project.yml), so one path names the file on both.
 */
@Composable
expect fun rememberAssetBitmap(path: String): ImageBitmap?
