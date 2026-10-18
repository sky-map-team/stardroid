/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.render

import com.google.android.stardroid.render.api.Rgba

/**
 * One screen-space drawing command, in UIKit points with a top-left origin. [FramePlanner] turns
 * the retained scenes into a list of these; `SkyView` replays them through CoreGraphics. Keeping
 * the split lets everything but the final CoreGraphics calls run (and be tested) on the JVM.
 */
sealed interface DrawOp {
    val color: Rgba

    /** A filled circle centred on ([x], [y]). */
    data class Dot(
        val x: Float,
        val y: Float,
        val radius: Float,
        override val color: Rgba,
    ) : DrawOp

    /** An open polyline through ([xs]`[i]`, [ys]`[i]`). */
    class Polyline(
        val xs: FloatArray,
        val ys: FloatArray,
        val width: Float,
        override val color: Rgba,
    ) : DrawOp {
        init {
            require(xs.size == ys.size && xs.size >= 2) { "A polyline needs 2+ matching vertices" }
        }
    }

    /** A text label horizontally centred on [x], its top edge at [y]. */
    data class Text(
        val x: Float,
        val y: Float,
        val text: String,
        val sizePt: Float,
        override val color: Rgba,
    ) : DrawOp
}
