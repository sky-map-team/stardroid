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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberCelestialImage(imageRef: String): ImageBitmap? {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageRef) {
        value = null
        value =
            withContext(Dispatchers.IO) {
                decodeAssetSampled(assets, "celestial_images/$imageRef", FULL_SIZE_MIN_SIDE_PX)
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

private fun decodeThumbnail(
    assets: AssetManager,
    imageRef: String,
    targetPx: Int,
): ImageBitmap? = decodeAssetSampled(assets, "celestial_images/$imageRef", targetPx)

internal actual fun fullScreenDialogProperties() =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
