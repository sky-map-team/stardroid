/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.ui.graphics.ImageBitmap
import com.google.android.stardroid.share.imageBitmapOfPixels
import com.google.android.stardroid.share.shareScaledSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIGraphicsPopContext
import platform.UIKit.UIGraphicsPushContext
import platform.UIKit.UIImage
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import kotlin.math.roundToInt

private const val BYTES_PER_PIXEL = 4
private const val BITS_PER_COMPONENT = 8

internal val DisplayRotation.isLandscape: Boolean
    get() = this == DisplayRotation.ROTATION_90 || this == DisplayRotation.ROTATION_270

/**
 * The preview's shape as far as the FOV lock cares, as if held upright: [crossways] means the
 * camera's image lies on its side in it, as in an iPad window that is tall while the interface
 * is landscape. A plain turn keeps the shape, and the specs must not be reported again for one,
 * since that resets the zoom.
 */
internal data class PreviewShape(
    val shortSide: Double,
    val longSide: Double,
    val crossways: Boolean,
) {
    companion object {
        /**
         * A view that fills the screen is never crossways, whatever [landscape] says: mid-turn an
         * iPhone's interface orientation changes one layout before the bounds do.
         */
        fun of(
            width: Double,
            height: Double,
            landscape: Boolean,
            screen: Pair<Double, Double> = screenSizeInPoints(),
        ): PreviewShape {
            val short = minOf(width, height)
            val long = maxOf(width, height)
            val fillsScreen =
                short == minOf(screen.first, screen.second) &&
                    long == maxOf(screen.first, screen.second)
            return PreviewShape(short, long, !fillsScreen && (width > height) != landscape)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun screenSizeInPoints(): Pair<Double, Double> =
    UIScreen.mainScreen.bounds.useContents { size.width to size.height }

@OptIn(ExperimentalForeignApi::class)
internal fun UIView.sizeInPoints(): Pair<Double, Double> =
    bounds.useContents { size.width to size.height }

/**
 * This image upright (UIKit applies its orientation) with its long edge at most [maxEdge], as
 * pixels alone: no metadata survives. Safe off the main thread.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIImage.toImageBitmap(maxEdge: Int): ImageBitmap? {
    val scale = scale
    val (width, height) =
        size.useContents { (width * scale).roundToInt() to (height * scale).roundToInt() }
    if (width <= 0 || height <= 0) return null
    val target = shareScaledSize(width, height, maxEdge)
    val outWidth = target?.width ?: width
    val outHeight = target?.height ?: height
    return drawImageBitmap(outWidth, outHeight) {
        drawInRect(CGRectMake(0.0, 0.0, outWidth.toDouble(), outHeight.toDouble()))
    }
}

/** What UIKit [draw]s, in pixels from the top left. */
@OptIn(ExperimentalForeignApi::class)
internal fun drawImageBitmap(
    width: Int,
    height: Int,
    draw: () -> Unit,
): ImageBitmap? {
    val pixels = ByteArray(width * height * BYTES_PER_PIXEL)
    withBitmapContext(pixels, width, height, draw) { } ?: return null
    return imageBitmapOfPixels(pixels, width, height, bgra = false)
}

@OptIn(ExperimentalForeignApi::class)
internal fun drawUIImage(
    width: Int,
    height: Int,
    draw: () -> Unit,
): UIImage? {
    val pixels = ByteArray(width * height * BYTES_PER_PIXEL)
    return withBitmapContext(pixels, width, height, draw) { context ->
        CGBitmapContextCreateImage(context)?.let { image ->
            UIImage.imageWithCGImage(image).also { CGImageRelease(image) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun <T> withBitmapContext(
    pixels: ByteArray,
    width: Int,
    height: Int,
    draw: () -> Unit,
    result: (CGContextRef) -> T,
): T? =
    pixels.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context =
            CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                BITS_PER_COMPONENT.toULong(),
                (width * BYTES_PER_PIXEL).toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            )
        CGColorSpaceRelease(colorSpace)
        if (context == null) return@usePinned null
        // UIKit draws from the top left, Core Graphics from the bottom.
        CGContextTranslateCTM(context, 0.0, height.toDouble())
        CGContextScaleCTM(context, 1.0, -1.0)
        UIGraphicsPushContext(context)
        draw()
        UIGraphicsPopContext()
        val value = result(context)
        CGContextRelease(context)
        value
    }
