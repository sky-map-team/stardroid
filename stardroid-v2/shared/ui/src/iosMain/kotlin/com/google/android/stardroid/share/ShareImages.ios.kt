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
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun ImageBitmap.release() = asSkiaBitmap().close()

/** Wraps premultiplied 8-bit pixels, top row first; null if Skia won't take them. */
fun imageBitmapOfPixels(
    pixels: ByteArray,
    width: Int,
    height: Int,
    bgra: Boolean,
): ImageBitmap? {
    val type = if (bgra) ColorType.BGRA_8888 else ColorType.RGBA_8888
    val info = ImageInfo(ColorInfo(type, ColorAlphaType.PREMUL, ColorSpace.sRGB), width, height)
    val bitmap = Bitmap()
    if (!bitmap.installPixels(info, pixels, width * BYTES_PER_PIXEL)) return null
    return bitmap.asComposeImageBitmap()
}

/** A PNG made from the pixels alone, so it carries no EXIF and no location. */
fun ImageBitmap.encodePng(): ByteArray? =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes

private const val BYTES_PER_PIXEL = 4
