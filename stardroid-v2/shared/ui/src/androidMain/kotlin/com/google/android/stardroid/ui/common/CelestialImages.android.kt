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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberCelestialImage(imageRef: String): ImageBitmap? {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageRef) {
        value = null
        value =
            withContext(Dispatchers.IO) {
                try {
                    assets.open("celestial_images/$imageRef").use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
    }
    return bitmap
}

@Composable
internal actual fun rememberCelestialThumbnailDecoder(
    targetPx: Int,
): (imageRef: String) -> ImageBitmap? {
    val assets = LocalContext.current.assets
    return remember(assets, targetPx) {
        {
                imageRef ->
            decodeThumbnail(assets, imageRef, targetPx)
        }
    }
}

/**
 * Power-of-two downsample toward [targetPx] on the short side (v1 delegated this to Coil). A
 * missing or corrupt asset is an empty tile; the name below it is the content of record.
 */
private fun decodeThumbnail(
    assets: AssetManager,
    imageRef: String,
    targetPx: Int,
): ImageBitmap? =
    try {
        val path = "celestial_images/$imageRef"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetPx &&
            bounds.outHeight / (sampleSize * 2) >= targetPx
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        assets.open(path).use {
            BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

internal actual fun fullScreenDialogProperties() =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
