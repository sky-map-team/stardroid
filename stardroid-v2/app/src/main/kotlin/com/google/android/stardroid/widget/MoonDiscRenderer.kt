/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.abs

/**
 * Draws the widget's moon disc from the model's geometry — no image assets, so the terminator
 * is always correct for the actual illuminated fraction and the observer's hemisphere (D75,
 * and it sidesteps the branding-directory licensing rules by shipping no artwork at all).
 */
object MoonDiscRenderer {
    // The widget-world palette (D73): lit disc in a warm off-white, shadow in surfaceVariant
    // navy so the dark limb still reads against the widget background.
    private const val LIT_COLOR = 0xFFE8E4D8.toInt()
    private const val SHADOW_COLOR = 0xFF1B2340.toInt()
    private const val MARIA_COLOR = 0x381B2340

    /**
     * Renders the disc at [sizePx] square. [illuminatedFraction] in `[0, 1]`; [waxing] puts the
     * lit limb on the east (right, as drawn); [mirrored] flips for southern-hemisphere viewing.
     */
    fun render(
        sizePx: Int,
        illuminatedFraction: Double,
        waxing: Boolean,
        mirrored: Boolean,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val radius = sizePx / 2f - 1f
        // Drawn with the lit limb on the right; waning flips it, and the southern hemisphere
        // flips it again (an observer there sees the disc rotated ~180°).
        if (!waxing != mirrored) {
            canvas.scale(-1f, 1f, center, center)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = SHADOW_COLOR
        canvas.drawCircle(center, center, radius, paint)

        // The lit lune: the right half-circle closed by the terminator, an ellipse whose
        // horizontal semi-axis is r·|2f−1| — bulging into the dark side past half full,
        // back into the lit side before it.
        val f = illuminatedFraction.coerceIn(0.0, 1.0)
        val terminatorRx = (radius * abs(2 * f - 1)).toFloat()
        val disc = RectF(center - radius, center - radius, center + radius, center + radius)
        val terminator =
            RectF(center - terminatorRx, center - radius, center + terminatorRx, center + radius)
        val lit =
            Path().apply {
                arcTo(disc, -90f, 180f)
                arcTo(terminator, 90f, if (f >= 0.5) 180f else -180f)
                close()
            }
        paint.color = LIT_COLOR
        canvas.drawPath(lit, paint)

        // Faint maria so a (near-)full disc doesn't read as a plain circle; clipped to the lit
        // shape so they never brighten the shadowed side.
        canvas.save()
        canvas.clipPath(lit)
        paint.color = MARIA_COLOR
        canvas.drawCircle(center + radius * 0.42f, center - radius * 0.28f, radius * 0.14f, paint)
        canvas.drawCircle(center + radius * 0.16f, center + radius * 0.26f, radius * 0.19f, paint)
        canvas.drawCircle(center + radius * 0.52f, center + radius * 0.38f, radius * 0.09f, paint)
        canvas.drawCircle(center + radius * 0.28f, center - radius * 0.58f, radius * 0.08f, paint)
        canvas.restore()

        return bitmap
    }
}
