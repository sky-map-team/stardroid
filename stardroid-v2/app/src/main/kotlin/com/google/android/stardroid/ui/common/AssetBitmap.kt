/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a bundled asset into an [ImageBitmap] off the main thread (the info card's
 * `CelestialImage` pattern: v1's Coil `file:///android_asset/` load without the dependency).
 * Returns null while decoding and stays null for a missing or corrupt asset — callers simply
 * compose nothing.
 */
@Composable
fun rememberAssetBitmap(
    path: String,
    minSidePx: Int = DEFAULT_MIN_SIDE_PX,
): ImageBitmap? {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value =
            withContext(Dispatchers.IO) {
                decodeAssetBitmap(assets, path, minSidePx)
            }
    }
    return bitmap
}

/**
 * Decodes [path] with a power-of-two `inSampleSize` so neither side drops below [minSidePx]
 * (the decoded bitmap stays at least that big, never holding more resolution than the UI
 * can show). Null for a missing or corrupt asset.
 */
fun decodeAssetBitmap(
    assets: AssetManager,
    path: String,
    minSidePx: Int,
): ImageBitmap? =
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= minSidePx &&
            bounds.outHeight / (sampleSize * 2) >= minSidePx
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

private const val DEFAULT_MIN_SIDE_PX = 1080
