/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.android.stardroid.ui.common.toByteArray
import com.google.android.stardroid.ui.theme.NightPhotoTint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Compose Multiplatform has no animated images, so Skia's codec decodes the GIF a frame at a
 * time. Each of its frames draws over the one before (none disposes of anything), so one bitmap
 * holds the frame on show and the next decodes over it: all 203 frames at once would take
 * ~42 MB. Compose copies the bitmap's pixels whenever it draws, so redrawing after each decode
 * shows the new frame.
 */
@Composable
internal actual fun FigureEightAnimation(
    nightMode: Boolean,
    modifier: Modifier,
) {
    val codec by produceState<Codec?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { openBundleCodec(FIGURE_EIGHT_PATH) }
    }
    val current = codec
    if (current == null) {
        Spacer(modifier)
        return
    }
    val bitmap =
        remember(current) {
            Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(current.width, current.height)) }
        }
    val image = remember(bitmap) { bitmap.asComposeImageBitmap() }
    // Read while drawing, so each new frame redraws the canvas without recomposing.
    var shownFrame by remember(current) { mutableIntStateOf(NO_FRAME) }
    LaunchedEffect(current) {
        val durations = current.framesInfo.map { it.duration.coerceAtLeast(MIN_FRAME_MILLIS) }
        var frame = 0
        while (true) {
            // The loop starts over on a clean slate; every later frame builds on the last.
            if (frame == 0) bitmap.erase(TRANSPARENT)
            current.readPixels(bitmap, frame, if (frame == 0) NO_FRAME else frame - 1)
            bitmap.notifyPixelsChanged()
            shownFrame = frame
            delay(durations[frame].toLong())
            frame = (frame + 1) % durations.size
        }
    }
    val tint = if (nightMode) ColorFilter.tint(NightPhotoTint, BlendMode.Modulate) else null
    Canvas(modifier) {
        if (shownFrame == NO_FRAME) return@Canvas
        // Fit the frame inside the canvas, centred, as Android's FIT_CENTER does.
        val scale = min(size.width / image.width, size.height / image.height)
        val width = (image.width * scale).roundToInt()
        val height = (image.height * scale).roundToInt()
        drawImage(
            image,
            dstOffset =
                IntOffset(
                    ((size.width - width) / 2).roundToInt(),
                    ((size.height - height) / 2).roundToInt(),
                ),
            dstSize = IntSize(width, height),
            colorFilter = tint,
        )
    }
}

/** A codec over the bundle's copy of the asset at [path], or null if it is missing or bad. */
private fun openBundleCodec(path: String): Codec? =
    try {
        NSData.dataWithContentsOfFile("${NSBundle.mainBundle.resourcePath}/$path")?.let {
            Codec.makeFromData(Data.makeFromBytes(it.toByteArray()))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/** Skia's "no frame": decode with nothing assumed already in the bitmap. */
private const val NO_FRAME = -1

/** A floor under each frame's delay, as browsers have, for a GIF that asks for none. */
private const val MIN_FRAME_MILLIS = 20

private const val TRANSPARENT = 0
