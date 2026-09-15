/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import kotlin.math.cos

class EclipticLayerTest {
    private val layer = EclipticLayer(strings = flowOf(FakeLayerStrings()))
    private val scene = layer.buildScene(FakeLayerStrings())

    @Test
    fun `draws just above the grid, behind the catalog layers`() {
        // Upstream #925: the line is opaque, so it must stay behind constellations/DSOs/stars.
        assertThat(scene.depth).isEqualTo(5)
    }

    @Test
    fun `main line is the closed five-vertex great circle in the opaque dimmed gold`() {
        val line = scene.lines.first()
        assertThat(line.vertices).hasSize(5)
        assertThat(line.vertices.first()).isEqualTo(line.vertices.last())
        // Northern solstice point sits at (RA 90°, Dec +obliquity).
        assertThat(line.vertices[1]).isEqualTo(RaDec(90.0, 23.439281).toGeocentricVector())
        assertThat(line.color).isEqualTo(SkyColors.ECLIPTIC_LINE)
        assertThat(line.color.a).isEqualTo(1f)
        assertThat(line.widthDp).isEqualTo(1.8)
    }

    @Test
    fun `graduation ticks every 10 degrees, heavier at the zodiac boundaries`() {
        val ticks = scene.lines.drop(1)
        assertThat(ticks).hasSize(36)
        val (major, minor) = ticks.partition { it.widthDp == 2.0 }
        assertThat(major).hasSize(12)
        assertThat(minor).hasSize(24)
        assertThat(minor.map { it.widthDp }.distinct()).containsExactly(1.5)
        // Each tick is anchored on the ecliptic and points off it in ecliptic latitude.
        for (tick in ticks) {
            assertThat(tick.vertices).hasSize(2)
            assertThat(tick.color).isEqualTo(SkyColors.ECLIPTIC_LINE)
        }
        // The 0° major tick starts at the vernal equinox = RA 0 / dec 0.
        val vernal = RaDec(0.0, 0.0).toGeocentricVector()
        assertThat(major[0].vertices[0].x).isWithin(1e-9).of(vernal.x)
        assertThat(major[0].vertices[0].y).isWithin(1e-9).of(vernal.y)
        assertThat(major[0].vertices[0].z).isWithin(1e-9).of(vernal.z)
    }

    @Test
    fun `degree labels at the 30 degree marks except the vernal equinox, plus two name labels`() {
        val (names, degrees) = scene.labels.partition { it.text == "Ecliptic" }
        assertThat(names).hasSize(2)
        // 30°..330°: the 0° label is the grid layer's "0" (shared vernal-equinox label).
        assertThat(degrees.map { it.text })
            .containsExactlyElementsIn((30 until 360 step 30).map { "$it°" })
        for (label in scene.labels) {
            assertThat(label.style.color).isEqualTo(SkyColors.ECLIPTIC_LABEL)
        }
    }

    @Test
    fun `labels sit off the line in ecliptic latitude`() {
        // The 90° degree label is offset +3° in ecliptic latitude from the solstice point.
        val solstice = RaDec(90.0, 23.439281).toGeocentricVector()
        val label90 = scene.labels.single { it.text == "90°" }
        val cos3 = cos(Math.toRadians(3.0))
        val dot =
            label90.pos.x * solstice.x + label90.pos.y * solstice.y + label90.pos.z * solstice.z
        assertThat(dot).isWithin(1e-9).of(cos3)
    }
}
