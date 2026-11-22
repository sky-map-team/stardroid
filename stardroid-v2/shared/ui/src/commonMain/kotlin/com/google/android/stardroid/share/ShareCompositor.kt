/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws every shared sky image, for both apps (camera-ar-launch.md §4.5), so the two can't
 * drift: map-only over black, the AR overlay and side-by-side layouts, and the brand footer
 * (D110). Capturing and encoding stay with each platform.
 *
 * Bitmaps passed in stay the caller's; the ones made along the way are released here. Call from
 * the main thread, which measures and paints the text (Skia's font collection isn't thread-safe);
 * the images are drawn on a background one.
 */
class ShareCompositor(
    private val textMeasurer: TextMeasurer,
) {
    suspend fun mapOnly(
        map: ImageBitmap,
        branding: ShareBranding,
    ): ImageBitmap {
        val footer = measureFooter(map.width, map.height, branding)
        val image = withContext(Dispatchers.Default) { withStrip(map, footer) }
        drawLines(image, footer)
        return image
    }

    /**
     * @param cameraDim how dark to make the camera still: 0 is as shot, 1 black.
     */
    suspend fun arLayouts(
        map: ImageBitmap,
        still: ImageBitmap,
        cameraDim: Double,
        branding: ShareBranding,
    ): ShareLayouts {
        val gap = (map.width * SIDE_BY_SIDE_GAP_FRACTION).toInt()
        val pairWidth = map.width * 2 + gap
        val overlayFooter = measureFooter(map.width, map.height, branding)
        val pairFooter = measureFooter(pairWidth, map.height, branding)
        val layouts =
            withContext(Dispatchers.Default) {
                val overlay =
                    drawBitmap(map.width, map.height) {
                        drawCamera(still, map.width, map.height, cameraDim)
                        drawWhole(map)
                    }
                val pair =
                    drawBitmap(pairWidth, map.height) {
                        drawRect(Color.Black)
                        drawCamera(still, map.width, map.height, cameraDim)
                        drawWhole(map, left = map.width + gap)
                    }
                ShareLayouts(
                    withStrip(overlay, overlayFooter).also { overlay.release() },
                    withStrip(pair, pairFooter).also { pair.release() },
                )
            }
        drawLines(layouts.overlay, overlayFooter)
        drawLines(layouts.sideBySide, pairFooter)
        return layouts
    }

    private class Footer(
        val height: Int,
        val icon: ImageBitmap?,
        val padding: Float,
        val iconSize: Int,
        val lines: List<Pair<TextLayoutResult, Offset>>,
    )

    // Metrics scale with the image, so the strip reads the same at any capture resolution; the
    // side-by-side layout is twice as wide, so they follow the shorter side.
    private fun measureFooter(
        width: Int,
        contentHeight: Int,
        branding: ShareBranding,
    ): Footer {
        val height =
            (minOf(width, contentHeight) * FOOTER_HEIGHT_FRACTION).toInt().coerceAtLeast(1)
        val padding = height * PADDING_FRACTION
        val iconSize = (height - 2 * padding).toInt()
        val left = if (branding.icon == null) padding else padding + iconSize + padding

        // Each line reads in its own language's direction, though the strip is laid out LTR.
        fun measure(
            text: String,
            style: TextStyle,
        ) = textMeasurer.measure(
            text,
            style.copy(textDirection = TextDirection.Content),
            softWrap = false,
            maxLines = 1,
        )

        val titleSize = height * TITLE_FRACTION
        val titleStyle =
            TextStyle(
                color = TITLE_COLOR,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize.sp,
            )
        val title = measure(branding.appName, titleStyle)

        var bodySize = height * BODY_FRACTION

        fun body(text: String) =
            measure(
                text,
                TextStyle(
                    color = BODY_COLOR,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = bodySize.sp,
                ),
            )
        // Shrink the body until its longest line fits, rather than clip it at the edge.
        val available = width - left - padding
        val widest = maxOf(body(branding.invitation).size.width, body(branding.link).size.width)
        if (widest > available) bodySize *= available / widest

        val titleBaseline = contentHeight + padding + titleSize
        val invitationBaseline = titleBaseline + bodySize * LINE_SPACING

        fun placed(
            layout: TextLayoutResult,
            baseline: Float,
        ) = layout to Offset(left, baseline - layout.firstBaseline)

        val lines =
            listOf(
                placed(title, titleBaseline),
                placed(body(branding.invitation), invitationBaseline),
                placed(body(branding.link), invitationBaseline + bodySize * LINE_SPACING),
            )
        return Footer(height, branding.icon, padding, iconSize, lines)
    }

    /** [content] flattened over black, above the strip and its icon; [drawLines] adds the text. */
    private fun withStrip(
        content: ImageBitmap,
        footer: Footer,
    ): ImageBitmap =
        drawBitmap(content.width, content.height + footer.height) {
            drawRect(Color.Black)
            drawWhole(content)
            drawRect(
                FOOTER_BACKGROUND,
                topLeft = Offset(0f, content.height.toFloat()),
                size = Size(content.width.toFloat(), footer.height.toFloat()),
            )
            footer.icon?.let { icon ->
                val inset = footer.padding.toInt()
                drawImage(
                    icon,
                    dstOffset = IntOffset(inset, content.height + inset),
                    dstSize = IntSize(footer.iconSize, footer.iconSize),
                    filterQuality = FilterQuality.Low,
                )
            }
        }

    private fun drawLines(
        image: ImageBitmap,
        footer: Footer,
    ) = drawInto(image) {
        for ((layout, topLeft) in footer.lines) drawText(layout, topLeft = topLeft)
    }

    // Cropped as the preview was, so the still shows what was behind the map.
    private fun DrawScope.drawCamera(
        still: ImageBitmap,
        width: Int,
        height: Int,
        dim: Double,
    ) {
        val crop = centerCrop(still.width, still.height, width.toDouble() / height)
        drawImage(
            still,
            srcOffset = IntOffset(crop.left, crop.top),
            srcSize = IntSize(crop.width, crop.height),
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.Low,
        )
        val alpha = dim.coerceIn(0.0, 1.0).toFloat()
        if (alpha > 0f) {
            drawRect(
                Color.Black.copy(alpha = alpha),
                size = Size(width.toFloat(), height.toFloat()),
            )
        }
    }

    private companion object {
        const val SIDE_BY_SIDE_GAP_FRACTION = 0.008

        /** Footer strip height as a fraction of the image's short side. */
        const val FOOTER_HEIGHT_FRACTION = 0.16

        const val PADDING_FRACTION = 0.18f
        const val TITLE_FRACTION = 0.30f
        const val BODY_FRACTION = 0.17f
        const val LINE_SPACING = 1.4f

        val FOOTER_BACKGROUND = Color(0xFF131720)
        val TITLE_COLOR = Color(0xFFF2B04A)
        val BODY_COLOR = Color(0xFF93A0B4)
    }
}

/** A [ShareCompositor] measuring in pixels, whatever the screen's density or font scale. */
@Composable
fun rememberShareCompositor(): ShareCompositor {
    val fonts = LocalFontFamilyResolver.current
    return remember(fonts) {
        ShareCompositor(TextMeasurer(fonts, Density(1f), LayoutDirection.Ltr, cacheSize = 0))
    }
}

/** This image at share size ([MAX_SHARE_EDGE_PX]), releasing it if that took a copy. */
fun ImageBitmap.toShareSize(): ImageBitmap {
    val target = shareScaledSize(width, height, MAX_SHARE_EDGE_PX) ?: return this
    val scaled =
        drawBitmap(target.width, target.height) {
            drawImage(
                this@toShareSize,
                dstSize = IntSize(target.width, target.height),
                // A copy: the map plane's alpha must survive untouched.
                blendMode = BlendMode.Src,
                filterQuality = FilterQuality.Low,
            )
        }
    release()
    return scaled
}

/**
 * Longest edge of a capture before compositing (audit-2026-08 M5): above 1080p, and share
 * targets re-encode anyway. It decides the shared image's resolution.
 */
const val MAX_SHARE_EDGE_PX = 2048

private fun DrawScope.drawWhole(
    image: ImageBitmap,
    left: Int = 0,
) = drawImage(
    image,
    dstOffset = IntOffset(left, 0),
    dstSize = IntSize(image.width, image.height),
)

private fun drawBitmap(
    width: Int,
    height: Int,
    draw: DrawScope.() -> Unit,
): ImageBitmap = ImageBitmap(width, height).also { drawInto(it, draw) }

private fun drawInto(
    image: ImageBitmap,
    draw: DrawScope.() -> Unit,
) {
    val size = Size(image.width.toFloat(), image.height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size, draw)
}
