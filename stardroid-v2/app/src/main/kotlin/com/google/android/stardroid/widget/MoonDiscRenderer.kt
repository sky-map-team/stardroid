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
import com.google.android.stardroid.surfaces.MoonDisc
import com.google.android.stardroid.surfaces.WidgetColors
import com.google.android.stardroid.surfaces.moonDisc

/**
 * Draws the widget's moon disc from the shared [MoonDisc] geometry, which the iOS widgets draw
 * too — no image assets, so the terminator is always right for the actual illuminated fraction
 * and the observer's hemisphere (D75).
 */
object MoonDiscRenderer {
    /** [sizePx] square; the other arguments are [moonDisc]'s. */
    fun render(
        sizePx: Int,
        illuminatedFraction: Double,
        waxing: Boolean,
        mirrored: Boolean,
    ): Bitmap {
        val disc = moonDisc(illuminatedFraction, waxing, mirrored)
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val radius = sizePx / 2f - 1f
        if (disc.flipped) {
            canvas.scale(-1f, 1f, center, center)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = WidgetColors.MOON_SHADOW.toInt()
        canvas.drawCircle(center, center, radius, paint)

        val terminatorRx = (radius * disc.terminatorRx).toFloat()
        val bounds = RectF(center - radius, center - radius, center + radius, center + radius)
        val terminator =
            RectF(center - terminatorRx, center - radius, center + terminatorRx, center + radius)
        val lit =
            Path().apply {
                arcTo(bounds, -90f, 180f)
                arcTo(terminator, 90f, if (disc.terminatorBulgesLeft) 180f else -180f)
                close()
            }
        paint.color = WidgetColors.MOON_LIT.toInt()
        canvas.drawPath(lit, paint)

        canvas.save()
        canvas.clipPath(lit)
        paint.color = WidgetColors.MOON_MARIA.toInt()
        for (mare in MoonDisc.MARIA) {
            canvas.drawCircle(
                center + radius * mare.x.toFloat(),
                center + radius * mare.y.toFloat(),
                radius * mare.r.toFloat(),
                paint,
            )
        }
        canvas.restore()

        return bitmap
    }
}
