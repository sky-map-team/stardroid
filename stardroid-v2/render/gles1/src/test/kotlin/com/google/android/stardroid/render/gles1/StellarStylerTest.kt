/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StellarStylerTest {
    private val defaultState = RenderState()

    @Test
    fun `bright star gets boosted size over a faint star`() {
        val bright =
            StellarStyler.style(
                PointAppearance.Stellar(magnitude = 0.0),
                defaultState,
                density = 1f,
            )
        val faint =
            StellarStyler.style(
                PointAppearance.Stellar(magnitude = 5.0),
                defaultState,
                density = 1f,
            )
        assertThat(bright.sizePx).isGreaterThan(faint.sizePx)
    }

    @Test
    fun `size scales with density`() {
        val at1x = StellarStyler.style(PointAppearance.Stellar(5.0), defaultState, density = 1f)
        val at2x = StellarStyler.style(PointAppearance.Stellar(5.0), defaultState, density = 2f)
        assertThat(at2x.sizePx).isEqualTo(at1x.sizePx * 2f)
    }

    @Test
    fun `magnitude well within the limit is fully opaque`() {
        val state = RenderState(magnitudeLimit = 6.0)
        val star = StellarStyler.style(PointAppearance.Stellar(2.0), state, density = 1f)
        assertThat(star.color.a).isEqualTo(1f)
    }

    @Test
    fun `magnitude past the limit fades out, never pops to invisible at the boundary`() {
        val state = RenderState(magnitudeLimit = 6.0)
        val atLimit = StellarStyler.style(PointAppearance.Stellar(6.0), state, density = 1f)
        val wellPast = StellarStyler.style(PointAppearance.Stellar(7.0), state, density = 1f)
        assertThat(atLimit.color.a).isEqualTo(1f)
        assertThat(wellPast.color.a).isEqualTo(0f)
    }

    @Test
    fun `fade is monotonically decreasing and floored before it hits zero`() {
        val state = RenderState(magnitudeLimit = 6.0)
        val justPast = StellarStyler.style(PointAppearance.Stellar(6.1), state, density = 1f)
        val almostFaded = StellarStyler.style(PointAppearance.Stellar(6.49), state, density = 1f)
        assertThat(justPast.color.a).isGreaterThan(almostFaded.color.a)
        assertThat(almostFaded.color.a).isGreaterThan(0f)
    }

    @Test
    fun `brighter stars render brighter than fainter ones, v1 shade ramp`() {
        val bright = StellarStyler.style(PointAppearance.Stellar(1.5), defaultState, density = 1f)
        val faint = StellarStyler.style(PointAppearance.Stellar(4.5), defaultState, density = 1f)
        assertThat(bright.color.r).isGreaterThan(faint.color.r)
        assertThat(bright.color.g).isGreaterThan(faint.color.g)
        assertThat(bright.color.b).isGreaterThan(faint.color.b)
    }

    @Test
    fun `magnitude zero or brighter is full brightness, fainter than the cap stays visible`() {
        val brightest = StellarStyler.style(PointAppearance.Stellar(-1.5), defaultState, 1f)
        assertThat(brightest.color.r).isEqualTo(1f)
        val faintest = StellarStyler.style(PointAppearance.Stellar(9.0), defaultState, 1f)
        // v1 blacked out past its 5.6 data cap; v2 clamps the shade there instead.
        assertThat(faintest.color.r).isGreaterThan(0.3f)
    }

    @Test
    fun `null color index renders grey-white`() {
        val appearance = PointAppearance.Stellar(2.0, colorIndex = null)
        val star = StellarStyler.style(appearance, defaultState, density = 1f)
        assertThat(star.color.r).isEqualTo(star.color.g)
        assertThat(star.color.g).isEqualTo(star.color.b)
    }

    @Test
    fun `negative color index skews blue, positive skews red`() {
        val blueAppearance = PointAppearance.Stellar(2.0, colorIndex = -0.4)
        val redAppearance = PointAppearance.Stellar(2.0, colorIndex = 2.0)
        val blue = StellarStyler.style(blueAppearance, defaultState, density = 1f)
        val red = StellarStyler.style(redAppearance, defaultState, density = 1f)
        assertThat(blue.color.b).isGreaterThan(blue.color.r)
        assertThat(red.color.r).isGreaterThan(red.color.b)
    }

    @Test
    fun `fixed appearance passes color and size-times-density through`() {
        val color = Rgba(0.2f, 0.4f, 0.6f)
        val star =
            StellarStyler.style(
                PointAppearance.Fixed(color, sizeDp = 4.0),
                defaultState,
                density = 2f,
            )
        assertThat(star.color).isEqualTo(color)
        assertThat(star.sizePx).isEqualTo(8f)
    }

    @Test
    fun `night mode collapses color to a red-only luminance transform`() {
        val state = RenderState(nightMode = true)
        val star =
            StellarStyler.style(
                PointAppearance.Fixed(Rgba(0.2f, 0.4f, 0.6f), sizeDp = 1.0),
                state,
                density = 1f,
            )
        assertThat(star.color.g).isEqualTo(0f)
        assertThat(star.color.b).isEqualTo(0f)
        assertThat(star.color.r).isGreaterThan(0f)
    }
}
