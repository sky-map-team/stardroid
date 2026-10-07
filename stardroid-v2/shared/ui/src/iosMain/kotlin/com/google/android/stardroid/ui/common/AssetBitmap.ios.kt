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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

@Composable
actual fun rememberAssetBitmap(path: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeBundleImage(path) }
    }
    return bitmap
}

/**
 * The image at [path] in the app bundle (Android's asset path, which the bundle mirrors), or
 * null if it is missing or won't decode. Blocking: call it off the main thread.
 */
internal fun decodeBundleImage(path: String): ImageBitmap? =
    try {
        NSData.dataWithContentsOfFile("${NSBundle.mainBundle.resourcePath}/$path")?.let {
            Image.makeFromEncoded(it.toByteArray()).toComposeImageBitmap()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
