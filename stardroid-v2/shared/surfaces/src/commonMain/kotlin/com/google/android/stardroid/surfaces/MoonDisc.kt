/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import kotlin.math.abs

/**
 * The widgets' moon disc as geometry, so Android's Canvas and iOS's SwiftUI `Path` draw the same
 * shape from no image assets (D75). Lengths are in units of the disc's radius, x to the right and
 * y down, about the disc's centre.
 *
 * Drawn unflipped, the lit limb is on the right: the right half-circle, closed by the terminator,
 * an ellipse of horizontal semi-axis [terminatorRx] (vertical semi-axis 1). Past half full the
 * terminator bulges into the dark half ([terminatorBulgesLeft]), before it into the lit half.
 */
data class MoonDisc(
    /** Mirror left to right before drawing: waning, or the southern hemisphere, but not both. */
    val flipped: Boolean,
    val terminatorRx: Double,
    val terminatorBulgesLeft: Boolean,
) {
    /** A faint mare, clipped to the lit shape so it never brightens the shadowed side. */
    data class Mare(
        val x: Double,
        val y: Double,
        val r: Double,
    )

    companion object {
        /** So a (near-)full disc doesn't read as a plain circle. */
        val MARIA: List<Mare> =
            listOf(
                Mare(0.42, -0.28, 0.14),
                Mare(0.16, 0.26, 0.19),
                Mare(0.52, 0.38, 0.09),
                Mare(0.28, -0.58, 0.08),
            )
    }
}

/**
 * [illuminatedFraction] in `[0, 1]`; [waxing] puts the lit limb on the east (right, as drawn);
 * [mirrored] flips it for a southern-hemisphere observer, who sees the disc rotated ~180°.
 */
fun moonDisc(
    illuminatedFraction: Double,
    waxing: Boolean,
    mirrored: Boolean,
): MoonDisc {
    val f = illuminatedFraction.coerceIn(0.0, 1.0)
    return MoonDisc(
        flipped = !waxing != mirrored,
        terminatorRx = abs(2 * f - 1),
        terminatorBulgesLeft = f >= 0.5,
    )
}
