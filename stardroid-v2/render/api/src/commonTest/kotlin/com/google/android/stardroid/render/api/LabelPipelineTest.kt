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
import com.google.android.stardroid.testing.assertThat
import kotlin.math.floor
import kotlin.test.Test

/**
 * The shared label and icon pipeline — [LabelAtlas], [LabelFrame], [IconSprites],
 * [SpriteInstances] — driven by a fake rasterizer whose text is 6 px per character, 8 px of
 * ascent and 3 of descent, and which fills each cell solid.
 */
class LabelPipelineTest {
    private class FakeRasterizer : GlyphRasterizer {
        val fontSizes = mutableListOf<Float>()

        override fun measure(
            text: String,
            fontSizePx: Float,
        ): GlyphMetrics {
            fontSizes += fontSizePx
            return GlyphMetrics(widthPx = 6 * text.length, ascentPx = 8, descentPx = 3)
        }

        override fun rasterizePage(
            widthPx: Int,
            heightPx: Int,
            glyphs: List<PlacedText>,
        ): ByteArray {
            val page = ByteArray(widthPx * heightPx)
            for (g in glyphs) {
                for (y in g.cell.v until g.cell.v + g.cell.h) {
                    for (x in g.cell.u until g.cell.u + g.cell.w) page[y * widthPx + x] = -1
                }
            }
            return page
        }
    }

    private val camera = SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, fovDeg = 60.0)
    private val viewport = Viewport(600, 800, density = 2f)
    private val projection = SkyProjection(camera, viewport)
    private val white = Rgba(1f, 1f, 1f, 1f)

    private fun label(
        text: String,
        pos: Vector3 = Vector3.UNIT_X,
        priority: Int = 1,
        hasDetail: Boolean = false,
        size: LabelSize = LabelSize.STANDARD,
    ) = LabelPrimitive(pos, text, LabelStyle(size, white), priority, hasDetail = hasDetail)

    private fun atlas(vararg labels: LabelPrimitive) =
        LabelAtlas.build(labels.toList(), 1.0, 2f, FakeRasterizer(), maxTextureSizePx = 4096)

    /** Lays [atlas] out once with an instant fader, so the frame shows the declutter decision. */
    private fun layout(atlas: LabelAtlas): SpriteInstances {
        val out = SpriteInstances()
        val fader = LabelFader(fadeMillis = 0)
        fader.beginFrame(0)
        LabelFrame().layout(atlas, fader, camera, projection, viewport, out)
        fader.endFrame()
        return out
    }

    private fun instance(
        out: SpriteInstances,
        i: Int,
    ) = out.data.copyOfRange(
        i * SpriteInstances.FLOATS_PER_INSTANCE,
        (i + 1) * SpriteInstances.FLOATS_PER_INSTANCE,
    )

    // ---- LabelAtlas -------------------------------------------------------------------------

    @Test
    fun atlasScalesFontsByDensityAndPreference() {
        val rasterizer = FakeRasterizer()
        LabelAtlas.build(
            listOf(label("a", size = LabelSize.TITLE), label("b", size = LabelSize.MINOR)),
            labelScaleFactor = 1.5,
            density = 2f,
            rasterizer = rasterizer,
            maxTextureSizePx = 4096,
        )
        // TITLE is 15 sp and MINOR 8 sp, as on Android.
        assertThat(rasterizer.fontSizes).containsExactly(45f, 24f).inOrder()
    }

    @Test
    fun atlasCellsCarryTheirTextWithGlyphsTopRowFirst() {
        val built = atlas(label("Sirius"), label("Vega"))
        assertThat(built.pages).hasSize(1)
        val page = built.pages[0]
        val sirius = built.glyphs[0]
        assertThat(sirius.widthPx).isEqualTo(36)
        assertThat(sirius.heightPx).isEqualTo(11)
        // The quad's lower-left takes the larger v; the cell's pixels are covered.
        assertThat(sirius.v0).isGreaterThan(sirius.v1)
        val u = (sirius.u0 * page.widthPx).toInt()
        val v = (sirius.v1 * page.heightPx).toInt()
        assertThat(page.coverage[v * page.widthPx + u].toInt()).isEqualTo(-1)
    }

    // ---- LabelFrame -------------------------------------------------------------------------

    @Test
    fun aLabelHangsItsOffsetBelowItsAnchor() {
        val out = layout(atlas(label("Sirius")))
        assertThat(out.size).isEqualTo(1)
        assertThat(out.runCount).isEqualTo(1)
        val quad = instance(out, 0)
        val anchor = projection.worldToScreen(Vector3.UNIT_X)!!
        // 4 dp at density 2 to the label's top edge, so its centre is 8 + 11/2 px below.
        val expectedY = viewport.heightPx - (anchor.yPx + 8f + 5.5f)
        assertThat(quad[0]).isEqualTo(floor(anchor.xPx) + ScreenSpace.PIXEL_SNAP)
        assertThat(quad[1]).isEqualTo(floor(expectedY) + ScreenSpace.PIXEL_SNAP)
        assertThat(quad[2]).isEqualTo(36f)
        assertThat(quad[12]).isEqualTo(SpriteInstances.MODE_GLYPH.toFloat())
        // No info card: dimmed.
        assertThat(quad[11]).isWithin(1e-6f).of(LabelFrame.NO_DETAIL_ALPHA)
    }

    @Test
    fun aLabelWithAnInfoCardIsUnderlinedAtFullStrength() {
        val out = layout(atlas(label("Sirius", hasDetail = true)))
        assertThat(out.size).isEqualTo(2)
        val glyph = instance(out, 0)
        val rule = instance(out, 1)
        assertThat(glyph[11]).isEqualTo(1f)
        assertThat(rule[12]).isEqualTo(SpriteInstances.MODE_RULE.toFloat())
        assertThat(rule[2]).isWithin(1e-6f).of(36f * LabelFrame.UNDERLINE_WIDTH_FRACTION)
        assertThat(rule[11]).isWithin(1e-6f).of(LabelFrame.UNDERLINE_ALPHA)
        // Below the glyph.
        assertThat(rule[1]).isLessThan(glyph[1])
    }

    @Test
    fun aLabelBehindTheViewerIsNotDrawn() {
        assertThat(layout(atlas(label("Behind", pos = -Vector3.UNIT_X))).size).isEqualTo(0)
    }

    @Test
    fun overlappingLabelsKeepTheHigherPriority() {
        val built = atlas(label("Minor", priority = 1), label("Major", priority = 9))
        val out = layout(built)
        assertThat(out.size).isEqualTo(1)
        // The survivor samples Major's cell.
        assertThat(instance(out, 0)[4]).isEqualTo(built.glyphs[1].u0)
    }

    // ---- IconSprites ------------------------------------------------------------------------

    @Test
    fun iconsAreGroupedIntoOneRunPerImageAtTheirDpSize() {
        val galaxy = ImageRef("icon/galaxy")
        val cluster = ImageRef("icon/cluster")
        val near = Vector3(1.0, 0.05, 0.0).normalized()
        val icons =
            IconSprites.build(
                listOf(
                    PointPrimitive(near, PointAppearance.Icon(galaxy, 10.0)),
                    PointPrimitive(Vector3.UNIT_X, PointAppearance.Icon(cluster, 10.0)),
                    PointPrimitive(near, PointAppearance.Icon(galaxy, 10.0)),
                    PointPrimitive(-Vector3.UNIT_X, PointAppearance.Icon(cluster, 10.0)),
                    PointPrimitive(Vector3.UNIT_X, PointAppearance.Stellar(1.0)),
                ),
                density = 2f,
            )
        assertThat(icons.refs).containsExactly(galaxy, cluster).inOrder()
        val out = SpriteInstances()
        icons.layout(camera, projection, viewport, out)
        // The behind-the-viewer cluster is culled; the stellar point is not an icon.
        assertThat(out.size).isEqualTo(3)
        assertThat(out.runCount).isEqualTo(2)
        assertThat(out.runTexture[0]).isEqualTo(0)
        assertThat(out.runSize[0]).isEqualTo(2)
        assertThat(out.runTexture[1]).isEqualTo(1)
        assertThat(instance(out, 0)[2]).isEqualTo(20f)
        assertThat(instance(out, 0)[12]).isEqualTo(SpriteInstances.MODE_ICON.toFloat())
    }

    // ---- SpriteInstances and ScreenSpace ----------------------------------------------------

    @Test
    fun instancesGrowAndMultiplyAlphaIntoTheTint() {
        val out = SpriteInstances()
        out.beginRun(7)
        repeat(300) { out.add(1f, 2f, 3f, 4f, 0f, 1f, 1f, 0f, Rgba(1f, 1f, 1f, 0.5f), 0.5f, 1) }
        assertThat(out.size).isEqualTo(300)
        assertThat(out.runSize[0]).isEqualTo(300)
        assertThat(instance(out, 299)[11]).isEqualTo(0.25f)
        out.clear()
        assertThat(out.size).isEqualTo(0)
        assertThat(out.runCount).isEqualTo(0)
    }

    @Test
    fun pixelsPerDegreeFollowsTheShortSide() {
        // 90° across a 600 px short side: 300 px per unit tangent, times π/180 per degree.
        val ppd =
            ScreenSpace.pixelsPerDegree(
                SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, 90.0),
                Viewport(600, 800, 1f),
            )
        assertThat(ppd).isWithin(1e-9).of(300.0 * kotlin.math.PI / 180.0)
    }
}
