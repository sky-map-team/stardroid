/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import kotlin.math.sqrt

/**
 * The lit/dark geometry of a sphere lit from one side and seen as a disc — the shape of a phase,
 * with no reference to pixels, Android, or GL.
 *
 * A sphere's terminator is a circle, and its projection onto the disc is half of an ellipse
 * sharing the disc's vertical semi-axis. So the lit region is a half-disc closed by that
 * half-ellipse: past half-full the ellipse bulges across into the dark side (gibbous), before it
 * the ellipse cuts back into the lit side (crescent). This is the same construction the moon
 * widget has drawn since D75, lifted here so the renderer's compositor and the widget cannot
 * drift apart — and so a GLES3 fragment shader can evaluate exactly the same function per pixel.
 */
object PhaseGeometry {
    /**
     * How lit the point ([x], [y]) is, in disc radii from the terminator: positive inside the lit
     * region, negative in shadow, zero on the terminator itself. The disc is the unit circle and
     * the lit limb faces **+x**; callers rotate into that frame.
     *
     * With `s = 2f − 1`, the terminator is `x = −s·√(1 − y²)`, so the signed offset is
     * `x + s·√(1 − y²)`. At `f = 1` that is `x + √(1 − y²)`, non-negative across the whole disc;
     * at `f = 0` it is `x − √(1 − y²)`, non-positive across it; at `f = 0.5` it is `x`, the
     * straight terminator of a quarter phase.
     *
     * It is a horizontal offset rather than a true perpendicular distance, which matters only
     * for how wide the softened edge looks near the poles of the disc, where the terminator runs
     * nearly vertical.
     */
    fun litOffset(
        x: Double,
        y: Double,
        illuminatedFraction: Double,
    ): Double {
        val s = 2.0 * illuminatedFraction.coerceIn(0.0, 1.0) - 1.0
        val yy = y.coerceIn(-1.0, 1.0)
        return x + s * sqrt(1.0 - yy * yy)
    }
}
