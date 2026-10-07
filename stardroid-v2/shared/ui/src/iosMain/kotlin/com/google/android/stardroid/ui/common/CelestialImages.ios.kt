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
import androidx.compose.ui.window.DialogProperties
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.posix.memcpy

// The photos ship in the app bundle as the celestial_images folder (ios/SkyMap/project.yml).
@Composable
internal actual fun rememberCelestialImage(imageRef: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageRef) {
        value = null
        value =
            withContext(Dispatchers.IO) { decodeBundleImage("celestial_images/$imageRef") }
    }
    return bitmap
}

internal actual fun fullScreenDialogProperties() = DialogProperties(usePlatformDefaultWidth = false)

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray =
    ByteArray(length.toInt()).also { bytes ->
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
