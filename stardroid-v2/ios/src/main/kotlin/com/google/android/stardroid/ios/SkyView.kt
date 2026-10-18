/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.ios.render.DrawOp
import com.google.android.stardroid.ios.render.RetainedSkyRenderer
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.Viewport
import org.robovm.apple.coregraphics.CGContext
import org.robovm.apple.coregraphics.CGLineCap
import org.robovm.apple.coregraphics.CGPoint
import org.robovm.apple.coregraphics.CGRect
import org.robovm.apple.dispatch.DispatchQueue
import org.robovm.apple.foundation.NSAttributedString
import org.robovm.apple.uikit.NSAttributedStringAttributes
import org.robovm.apple.uikit.UIColor
import org.robovm.apple.uikit.UIFont
import org.robovm.apple.uikit.UIGraphics
import org.robovm.apple.uikit.UIView

/**
 * The sky surface: a plain [UIView] that replays the [RetainedSkyRenderer]'s draw list through
 * CoreGraphics on every `drawRect:`. Redraws are on demand, like Android's
 * `RENDERMODE_WHEN_DIRTY` (D23): each renderer update posts one `setNeedsDisplay` to the main
 * queue, and UIKit coalesces them into at most one draw per display refresh.
 */
class SkyView : UIView() {
    val renderer =
        RetainedSkyRenderer(
            onInvalidate = { DispatchQueue.getMainQueue().async { setNeedsDisplay() } },
        )

    init {
        backgroundColor = UIColor.black()
        isOpaque = true
    }

    override fun draw(rect: CGRect) {
        val bounds = bounds
        if (bounds.width < 1.0 || bounds.height < 1.0) return
        val viewport = Viewport(bounds.width.toInt(), bounds.height.toInt(), density = 1f)
        val context = UIGraphics.getCurrentContext() ?: return
        context.setRGBFillColor(0.0, 0.0, 0.0, 1.0)
        context.fillRect(bounds)
        context.setLineCap(CGLineCap.Round)
        for (op in renderer.plan(viewport)) {
            when (op) {
                is DrawOp.Dot -> drawDot(context, op)
                is DrawOp.Polyline -> drawPolyline(context, op)
                is DrawOp.Text -> drawText(op)
            }
        }
    }

    private fun drawDot(
        context: CGContext,
        op: DrawOp.Dot,
    ) {
        context.setFill(op.color)
        val r = op.radius.toDouble()
        context.fillEllipseInRect(CGRect(op.x - r, op.y - r, 2 * r, 2 * r))
    }

    private fun drawPolyline(
        context: CGContext,
        op: DrawOp.Polyline,
    ) {
        context.setStroke(op.color)
        context.setLineWidth(op.width.toDouble())
        context.moveToPoint(op.xs[0].toDouble(), op.ys[0].toDouble())
        for (i in 1 until op.xs.size) context.addLineToPoint(
            op.xs[i].toDouble(),
            op.ys[i].toDouble(),
        )
        context.strokePath()
    }

    private fun drawText(op: DrawOp.Text) {
        val attributes =
            NSAttributedStringAttributes()
                .setFont(UIFont.getSystemFont(op.sizePt.toDouble()))
                .setForegroundColor(op.color.toUiColor())
        val text = NSAttributedString(op.text, attributes)
        text.draw(CGPoint(op.x - text.size.width / 2, op.y.toDouble()))
    }

    private fun CGContext.setFill(c: Rgba) =
        setRGBFillColor(
            c.r.toDouble(),
            c.g.toDouble(),
            c.b.toDouble(),
            c.a.toDouble(),
        )

    private fun CGContext.setStroke(c: Rgba) =
        setRGBStrokeColor(
            c.r.toDouble(),
            c.g.toDouble(),
            c.b.toDouble(),
            c.a.toDouble(),
        )

    private fun Rgba.toUiColor() = UIColor(r.toDouble(), g.toDouble(), b.toDouble(), a.toDouble())
}
