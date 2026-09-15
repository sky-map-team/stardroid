/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PrimitivesTest {
    @Test
    fun rgba_fromArgb_unpacksChannels() {
        val orange = Rgba.fromArgb(0xFF_FF_88_00.toInt())
        assertThat(orange.a).isWithin(1e-6f).of(1f)
        assertThat(orange.r).isWithin(1e-6f).of(1f)
        assertThat(orange.g).isWithin(1e-6f).of(0x88 / 255f)
        assertThat(orange.b).isWithin(1e-6f).of(0f)
    }

    @Test
    fun pointAppearance_isSealedAndExhaustive() {
        val appearances =
            listOf(
                PointAppearance.Stellar(magnitude = 2.5, colorIndex = 0.6),
                PointAppearance.Fixed(color = Rgba.WHITE, sizeDp = 4.0),
                PointAppearance.Icon(image = ImageRef("icon/galaxy"), sizeDp = 20.0),
            )
        for (a in appearances) {
            val label =
                when (a) { // exhaustive without an else branch
                    is PointAppearance.Stellar -> "stellar"
                    is PointAppearance.Fixed -> "fixed"
                    is PointAppearance.Icon -> "icon"
                }
            assertThat(label).isNotEmpty()
        }
    }

    @Test
    fun stellar_colorIndexDefaultsToNull() {
        assertThat(PointAppearance.Stellar(magnitude = 1.0).colorIndex).isNull()
    }

    @Test
    fun layerScene_defaultsToEmptyPrimitiveLists() {
        val scene = LayerScene(depth = 7)
        assertThat(scene.depth).isEqualTo(7)
        assertThat(scene.points).isEmpty()
        assertThat(scene.lines).isEmpty()
        assertThat(scene.images).isEmpty()
        assertThat(scene.labels).isEmpty()
        assertThat(scene.glows).isEmpty()
    }

    @Test
    fun glowPrimitive_acceptsEqualLengthRings() {
        val glow =
            GlowPrimitive(
                listOf(
                    GlowRing(listOf(Vector3.UNIT_X, Vector3.UNIT_Y), Rgba.WHITE),
                    GlowRing(listOf(Vector3.UNIT_Y, Vector3.UNIT_Z), Rgba.TRANSPARENT),
                ),
            )
        assertThat(glow.rings).hasSize(2)
    }

    @Test
    fun glowPrimitive_rejectsRaggedRings() {
        // The backend indexes every ring by ring 0's vertex count, so ragged input would corrupt
        // the mesh; the primitive enforces the invariant at construction.
        assertThrows<IllegalArgumentException> {
            GlowPrimitive(
                listOf(
                    GlowRing(listOf(Vector3.UNIT_X, Vector3.UNIT_Y), Rgba.WHITE),
                    GlowRing(listOf(Vector3.UNIT_Z), Rgba.TRANSPARENT),
                ),
            )
        }
    }

    @Test
    fun viewport_aspectIsWidthOverHeight() {
        assertThat(Viewport(1200, 600, 2f).aspect).isWithin(1e-6f).of(2f)
    }

    @Test
    fun viewport_rejectsNonPositiveDimensionsOrDensity() {
        assertThrows<IllegalArgumentException> { Viewport(0, 600, 1f) }
        assertThrows<IllegalArgumentException> { Viewport(600, 0, 1f) }
        assertThrows<IllegalArgumentException> { Viewport(600, 600, 0f) }
    }

    @Test
    fun skyCamera_rejectsOutOfRangeFov() {
        assertThrows<IllegalArgumentException> { SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, 0.0) }
        assertThrows<IllegalArgumentException> { SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, 180.0) }
    }

    @Test
    fun skyCamera_rejectsCollinearLineOfSightAndUp() {
        assertThrows<IllegalArgumentException> {
            SkyCamera(Vector3.UNIT_X, Vector3.UNIT_X, fovDeg = 60.0)
        }
    }

    @Test
    fun labelPrimitive_carriesStyleAndOptionalMagnitude() {
        val style = LabelStyle(LabelSize.TITLE, Rgba.WHITE, offsetDp = 8.0)
        val label =
            LabelPrimitive(
                Vector3.UNIT_X,
                "Sirius",
                style,
                priority = 10,
                magnitudeForThresholding = -1.46,
            )
        assertThat(label.style.size).isEqualTo(LabelSize.TITLE)
        assertThat(label.magnitudeForThresholding).isWithin(1e-9).of(-1.46)
        // Magnitude is optional (labels without one always pass the FOV depth filter).
        assertThat(
            LabelPrimitive(Vector3.UNIT_X, "Grid", style, priority = 0).magnitudeForThresholding,
        ).isNull()
    }
}
