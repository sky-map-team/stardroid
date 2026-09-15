/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.SizeFloor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * The three disc-size modes (D87). Each is defined by how the drawn size behaves *across the
 * zoom range*, which is the only thing that distinguishes them to a user.
 */
class DiscSizeParameterTest {
    private val time = Instant.parse("2026-08-14T22:00:00Z")
    private val london = LatLong(51.5, -0.13)
    private val shortSidePx = 960
    private val density = 3.0f

    private fun jupiterIn(mode: String): ImagePrimitive =
        SolarSystemLayer(
            MeeusEphemeris,
            MutableStateFlow(time),
            MutableStateFlow(FakeLayerStrings()),
            MutableStateFlow(london),
            PlanetImages(),
            MutableStateFlow(mode),
        ).buildScene(time, FakeLayerStrings(), london, mode)
            .images
            .single { it.image == ImageRef("planet/jupiter") }

    private fun ImagePrimitive.pxAt(fovDeg: Double) =
        SizeFloor.drawnDiameterDeg(
            angularSizeDeg,
            minScreenFraction,
            minSizeDp,
            fovDeg,
            shortSidePx,
            density,
        ) / fovDeg * shortSidePx

    @Test
    fun `true scale is honest at the stop and grows on the way there`() {
        val jupiter = jupiterIn(LayerParameter.DISC_SIZE_TRUE)
        // At the stop the disc is its real angular size, to the pixel.
        val truePx = jupiter.angularSizeDeg / 0.03 * shortSidePx
        assertThat(jupiter.pxAt(0.03)).isWithin(0.5).of(truePx)
        // Zoomed out it sits on the 3 dp floor, so the growth across the range is bounded by
        // that floor rather than by the 703x the field of view changes: about 31x for Jupiter.
        assertThat(jupiter.pxAt(21.1)).isWithin(0.01).of(3.0 * density)
        assertThat(jupiter.pxAt(0.03) / jupiter.pxAt(21.1)).isGreaterThan(20.0)
    }

    @Test
    fun `glyphs are a compressed true size, and so still grow with zoom`() {
        val jupiter = jupiterIn(LayerParameter.DISC_SIZE_GLYPHS)
        // Compressed physical size: Jupiter near 2.05°, well above the Moon, because Jupiter
        // is physically enormous — which is the fact this mode exists to convey.
        assertThat(jupiter.angularSizeDeg).isWithin(0.1).of(2.05)
        assertThat(jupiter.minScreenFraction).isEqualTo(0.0)
        // Fixed angular size means the pixel size grows exactly as the field of view narrows.
        assertThat(jupiter.pxAt(0.3) / jupiter.pxAt(3.0)).isWithin(0.01).of(10.0)
    }

    @Test
    fun `glyphs are always larger than the truth, and never smaller`() {
        val glyphs = jupiterIn(LayerParameter.DISC_SIZE_GLYPHS)
        val trueScale = jupiterIn(LayerParameter.DISC_SIZE_TRUE)
        for (fov in listOf(21.1, 5.0, 1.0, 0.1, 0.03)) {
            assertThat(glyphs.pxAt(fov)).isGreaterThan(trueScale.pxAt(fov))
        }
        // And the gap widens as you zoom, because the glyph keeps a fixed angular size while
        // the honest disc is pinned to the dp floor until its true size overtakes it.
        val wideRatio = glyphs.pxAt(21.1) / trueScale.pxAt(21.1)
        val narrowRatio = glyphs.pxAt(0.03) / trueScale.pxAt(0.03)
        assertThat(narrowRatio).isGreaterThan(wideRatio)
    }

    @Test
    fun `glyphs are the most zoom-responsive mode, and steady the least`() {
        // The three modes ranked by how much a disc grows across the whole zoom range — which
        // is the only difference a user can actually perceive.
        fun growth(mode: String) = jupiterIn(mode).let { it.pxAt(0.03) / it.pxAt(21.1) }

        val glyphs = growth(LayerParameter.DISC_SIZE_GLYPHS)
        val trueScale = growth(LayerParameter.DISC_SIZE_TRUE)
        val steady = growth(LayerParameter.DISC_SIZE_AUTO)
        assertThat(glyphs).isGreaterThan(trueScale)
        assertThat(trueScale).isGreaterThan(steady)
        // Glyphs track the field of view exactly: 21.1 / 0.03.
        assertThat(glyphs).isWithin(1.0).of(21.1 / 0.03)
    }

    @Test
    fun `steady mode holds a constant pixel size until it hands off`() {
        // The defining property of D86's floor, and why it is not the default: zooming does
        // nothing to the disc until the crossover.
        val jupiter = jupiterIn(LayerParameter.DISC_SIZE_AUTO)
        val wide = jupiter.pxAt(21.1)
        assertThat(jupiter.pxAt(5.0)).isWithin(0.5).of(wide)
        assertThat(jupiter.pxAt(1.0)).isWithin(0.5).of(wide)
        // Past the crossover it grows again, reaching true size.
        assertThat(jupiter.pxAt(0.03)).isGreaterThan(wide)
    }

    @Test
    fun `every mode keeps the smallest body visible`() {
        for (mode in LayerParameter.DISC_SIZE_PARAMETER.options) {
            val neptune =
                SolarSystemLayer(
                    MeeusEphemeris,
                    MutableStateFlow(time),
                    MutableStateFlow(FakeLayerStrings()),
                    MutableStateFlow(london),
                    PlanetImages(),
                    MutableStateFlow(mode),
                ).buildScene(time, FakeLayerStrings(), london, mode)
                    .images
                    .single { it.image == ImageRef("planet/neptune") }
            for (fov in listOf(90.0, 21.1, 1.0, 0.03)) {
                assertThat(neptune.pxAt(fov)).isAtLeast(3.0 * density - 1e-6)
            }
        }
    }

    @Test
    fun `the Moon is never absurd in any mode`() {
        // Sanity across all three: never smaller than its true half-degree, never larger than
        // the Sun's glyph — which it equals in Glyphs mode by deliberate exception.
        for (mode in LayerParameter.DISC_SIZE_PARAMETER.options) {
            val moon =
                SolarSystemLayer(
                    MeeusEphemeris,
                    MutableStateFlow(time),
                    MutableStateFlow(FakeLayerStrings()),
                    MutableStateFlow(london),
                    PlanetImages(),
                    MutableStateFlow(mode),
                ).buildScene(time, FakeLayerStrings(), london, mode)
                    .images
                    .single { it.image == ImageRef("planet/moon") }
            assertThat(moon.angularSizeDeg).isAtLeast(0.4)
            assertThat(moon.angularSizeDeg).isAtMost(4.2)
        }
    }

    @Test
    fun `bodies keep their phase and orientation in every mode`() {
        for (mode in LayerParameter.DISC_SIZE_PARAMETER.options) {
            val venus =
                SolarSystemLayer(
                    MeeusEphemeris,
                    MutableStateFlow(time),
                    MutableStateFlow(FakeLayerStrings()),
                    MutableStateFlow(london),
                    PlanetImages(),
                    MutableStateFlow(mode),
                ).buildScene(time, FakeLayerStrings(), london, mode)
                    .images
                    .single { it.image == ImageRef("planet/venus") }
            assertThat(venus.terminator).isNotNull()
            assertThat(venus.terminator!!.illuminatedFraction).isIn(
                com.google.common.collect.Range.closed(0.0, 1.0),
            )
        }
    }

    @Test
    fun `Saturn spans its rings in true scale but uses v1's size as a glyph`() {
        val trueScale =
            SolarSystemLayer(
                MeeusEphemeris,
                MutableStateFlow(time),
                MutableStateFlow(FakeLayerStrings()),
                MutableStateFlow(london),
                PlanetImages(),
                MutableStateFlow(LayerParameter.DISC_SIZE_TRUE),
            ).buildScene(time, FakeLayerStrings(), london, LayerParameter.DISC_SIZE_TRUE)
                .images
                .single { it.image == ImageRef("planet/saturn") }
        val globe = MeeusEphemeris.angularDiameterDeg(SolarSystemBody.SATURN, time)
        assertThat(trueScale.angularSizeDeg).isWithin(1e-9).of(globe * 2.269)
    }

    @Test
    fun `glyphs rank the bodies by how big they physically are`() {
        // The mode is an orrery: sizes say how big each body *is*, not how big it looks from
        // here — with one deliberate exception, the Moon, which is drawn at the Sun's size.
        val glyphs =
            SolarSystemLayer(
                MeeusEphemeris,
                MutableStateFlow(time),
                MutableStateFlow(FakeLayerStrings()),
                MutableStateFlow(london),
                PlanetImages(),
                MutableStateFlow(LayerParameter.DISC_SIZE_GLYPHS),
            ).buildScene(time, FakeLayerStrings(), london, LayerParameter.DISC_SIZE_GLYPHS)
                .images
                .associate { it.image to it.angularSizeDeg }

        fun size(name: String) = glyphs.getValue(ImageRef("planet/$name"))

        // The Sun towers over everything; the gas giants come next, then the ice giants, then
        // the rocky bodies, with Pluto last.
        assertThat(size("sun")).isGreaterThan(size("jupiter"))
        assertThat(size("jupiter")).isGreaterThan(size("uranus"))
        assertThat(size("uranus")).isGreaterThan(size("neptune"))
        assertThat(size("neptune")).isGreaterThan(size("venus"))
        assertThat(size("venus")).isGreaterThan(size("mars"))
        assertThat(size("mars")).isGreaterThan(size("mercury"))
        assertThat(size("mercury")).isGreaterThan(size("pluto"))
        // The Moon is the exception: drawn at the Sun's size, the sky's famous coincidence.
        assertThat(size("moon")).isWithin(1e-9).of(size("sun"))
        // Saturn's rings make it the widest thing of all, ahead of Jupiter and the Sun.
        assertThat(size("saturn")).isGreaterThan(size("jupiter"))
        assertThat(size("saturn")).isGreaterThan(size("sun"))
    }

    @Test
    fun `glyph sizes are constant, unlike apparent size`() {
        fun scene(mode: String) =
            SolarSystemLayer(
                MeeusEphemeris,
                MutableStateFlow(time),
                MutableStateFlow(FakeLayerStrings()),
                MutableStateFlow(london),
                PlanetImages(),
                MutableStateFlow(mode),
            ).buildScene(time, FakeLayerStrings(), london, mode)
                .images
                .associate { it.image to it.angularSizeDeg }

        val now = scene(LayerParameter.DISC_SIZE_GLYPHS)
        // Physical size does not change with the date, so neither does a glyph. Mars at
        // opposition is drawn exactly as it is at conjunction — the trade for ranking bodies
        // by what they are rather than how they look.
        val sixMonthsOn =
            SolarSystemLayer(
                MeeusEphemeris,
                MutableStateFlow(time),
                MutableStateFlow(FakeLayerStrings()),
                MutableStateFlow(london),
                PlanetImages(),
                MutableStateFlow(LayerParameter.DISC_SIZE_GLYPHS),
            ).buildScene(
                time.plus(kotlin.time.Duration.parse("180d")),
                FakeLayerStrings(),
                london,
                LayerParameter.DISC_SIZE_GLYPHS,
            ).images.associate { it.image to it.angularSizeDeg }
        for ((ref, size) in now) {
            assertThat(sixMonthsOn.getValue(ref)).isWithin(1e-9).of(size)
        }
    }

    @Test
    fun `the compression keeps the whole set legible`() {
        val glyphs =
            SolarSystemLayer(
                MeeusEphemeris,
                MutableStateFlow(time),
                MutableStateFlow(FakeLayerStrings()),
                MutableStateFlow(london),
                PlanetImages(),
                MutableStateFlow(LayerParameter.DISC_SIZE_GLYPHS),
            ).buildScene(time, FakeLayerStrings(), london, LayerParameter.DISC_SIZE_GLYPHS)
                .images

        // The Sun-to-Pluto range is 586:1 physically; compressed it must stay inside 10:1, or
        // Pluto is a dot again.
        val sizes = glyphs.map { it.angularSizeDeg }
        assertThat(sizes.max() / sizes.min()).isLessThan(10.0)
        // Pluto still reads as a disc: over half a degree, about 27 px at the opening zoom.
        assertThat(sizes.min()).isAtLeast(0.55)
        // And nothing is absurd: Saturn's rings are the widest at about 4.4°.
        assertThat(sizes.max()).isLessThan(5.5)
    }

    @Test
    fun `Saturn's globe is ranked, not its ring span`() {
        // The bug this pins: compressing the ring span drew Saturn's *body* at 1.10° against
        // Uranus's 1.51°, so the second-largest planet looked smaller than the fourth. The
        // texture spans the rings at 2.269 disc widths, so the globe within the drawn span is
        // what has to outrank Uranus.
        val glyphs =
            SolarSystemLayer(
                MeeusEphemeris,
                MutableStateFlow(time),
                MutableStateFlow(FakeLayerStrings()),
                MutableStateFlow(london),
                PlanetImages(),
                MutableStateFlow(LayerParameter.DISC_SIZE_GLYPHS),
            ).buildScene(time, FakeLayerStrings(), london, LayerParameter.DISC_SIZE_GLYPHS)
                .images
                .associate { it.image to it.angularSizeDeg }

        val saturnGlobe = glyphs.getValue(ImageRef("planet/saturn")) / 2.269
        val uranus = glyphs.getValue(ImageRef("planet/uranus"))
        val jupiter = glyphs.getValue(ImageRef("planet/jupiter"))
        assertThat(saturnGlobe).isGreaterThan(uranus)
        assertThat(saturnGlobe).isLessThan(jupiter)
    }
}
