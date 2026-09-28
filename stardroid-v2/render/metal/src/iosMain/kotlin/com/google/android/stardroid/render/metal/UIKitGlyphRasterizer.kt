/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import com.google.android.stardroid.render.api.GlyphMetrics
import com.google.android.stardroid.render.api.GlyphRasterizer
import com.google.android.stardroid.render.api.PlacedText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGContextClipToRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPopContext
import platform.UIKit.UIGraphicsPushContext
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes
import kotlin.math.ceil

/**
 * Label text through UIKit's string drawing (CoreText underneath) in the system sans-serif face:
 * the iOS side of [GlyphRasterizer], as `Canvas` with `Typeface.SANS_SERIF` is Android's. Pages
 * are drawn into an alpha-only bitmap, which is exactly the coverage mask the atlas wants.
 */
@OptIn(ExperimentalForeignApi::class)
class UIKitGlyphRasterizer : GlyphRasterizer {
    override fun measure(
        text: String,
        fontSizePx: Float,
    ): GlyphMetrics {
        val font = font(fontSizePx)
        val width = text.asNSString().sizeWithAttributes(attributes(font)).useContents { width }
        return GlyphMetrics(
            widthPx = ceil(width).toInt(),
            ascentPx = ceil(font.ascender).toInt(),
            descentPx = ceil(-font.descender).toInt(),
        )
    }

    override fun rasterizePage(
        widthPx: Int,
        heightPx: Int,
        glyphs: List<PlacedText>,
    ): ByteArray {
        val coverage = ByteArray(widthPx * heightPx)
        coverage.usePinned { pinned ->
            val context =
                CGBitmapContextCreate(
                    pinned.addressOf(0),
                    widthPx.toULong(),
                    heightPx.toULong(),
                    8u,
                    widthPx.toULong(),
                    null,
                    CGImageAlphaInfo.kCGImageAlphaOnly.value,
                ) ?: return coverage
            // UIKit draws top-left-origin; flipping the context makes user space match the page's
            // top-row-first layout, cell coordinates included.
            CGContextTranslateCTM(context, 0.0, heightPx.toDouble())
            CGContextScaleCTM(context, 1.0, -1.0)
            UIGraphicsPushContext(context)
            for (glyph in glyphs) {
                val cell = glyph.cell
                val font = font(glyph.fontSizePx)
                CGContextSaveGState(context)
                CGContextClipToRect(
                    context,
                    CGRectMake(
                        cell.u.toDouble(),
                        cell.v.toDouble(),
                        cell.w.toDouble(),
                        cell.h.toDouble(),
                    ),
                )
                // drawAtPoint puts the line's top at the point and its baseline one ascender below;
                // the atlas wants the baseline ascentPx below the cell's top.
                val top = cell.v + glyph.ascentPx - font.ascender
                glyph.text.asNSString().drawAtPoint(
                    CGPointMake(cell.u.toDouble(), top),
                    attributes(font),
                )
                CGContextRestoreGState(context)
            }
            UIGraphicsPopContext()
            CGContextRelease(context)
        }
        return coverage
    }

    private fun font(sizePx: Float): UIFont = UIFont.systemFontOfSize(sizePx.toDouble())

    private fun attributes(font: UIFont): Map<Any?, *> =
        mapOf(NSFontAttributeName to font, NSForegroundColorAttributeName to UIColor.whiteColor)

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun String.asNSString(): NSString = this as NSString
}
