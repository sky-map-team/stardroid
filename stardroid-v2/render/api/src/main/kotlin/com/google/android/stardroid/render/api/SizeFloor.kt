/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import kotlin.math.max

/**
 * How big to actually draw a body whose true apparent size would be too small to see (D86).
 *
 * The bodies are drawn at their real angular size, which for everything but the Sun and Moon is
 * a handful of arcseconds — invisible at any normal zoom. So a floor applies, and the shape of
 * that floor is the whole design:
 *
 * - **It is proportional to the field of view.** Zoom in and the floor shrinks exactly as fast,
 *   so it hands off to the true size smoothly and can never push a body back *up* as you zoom.
 *   A hard zoom threshold was rejected for that reason (D55): it would make the Moon appear to
 *   shrink as you zoomed into it.
 * - **There is no discontinuity.** At the crossover the floor equals the true size, so nothing
 *   pops; the disc simply stops growing relative to the screen and starts holding its real size.
 * - **Nothing ever vanishes.** [minSizeDp] is an absolute floor in screen terms, so even Pluto at
 *   a tenth of an arcsecond stays a visible, tappable speck.
 *
 * Calibrating [minScreenFraction] as a body's *current* drawn size divided by the default field
 * of view makes the map pixel-identical at default zoom, so all of this is a reward for zooming
 * in rather than a change to what people already see.
 */
object SizeFloor {
    /**
     * The diameter to draw, in degrees: [trueDiameterDeg], or the floor if that is larger.
     *
     * [minScreenFraction] is a fraction of the short viewport side; [minSizeDp] an absolute
     * screen-space floor. [fovDeg] spans the short side too (D21/D82), which is what makes both
     * terms scale together.
     */
    fun drawnDiameterDeg(
        trueDiameterDeg: Double,
        minScreenFraction: Double,
        minSizeDp: Double,
        fovDeg: Double,
        shortSidePx: Int,
        density: Float,
    ): Double {
        if (shortSidePx <= 0) return trueDiameterDeg
        val fractionFloor = minScreenFraction * fovDeg
        val dpFloor = minSizeDp * density * fovDeg / shortSidePx
        return max(trueDiameterDeg, max(fractionFloor, dpFloor))
    }
}
