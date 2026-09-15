/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.PointAppearance
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CatalogLayersTest {
    private fun obj(
        id: String,
        kind: LayerKind,
        type: String,
        name: String = "",
        magnitude: Double? = null,
        colorIndex: Double? = null,
        nameIsPrimary: Boolean = true,
    ) = CatalogObject(
        id = CelestialObjectId(id),
        layerKind = kind,
        type = TypeCode(type),
        position = RaDec(101.3, -16.7),
        magnitude = magnitude,
        colorIndex = colorIndex,
        name = name,
        nameIsPrimary = nameIsPrimary,
        searchFovDeg = null,
    )

    // ---- stars mapping ---------------------------------------------------------------

    @Test
    fun `stars map to stellar points carrying magnitude and color index`() {
        val sirius =
            obj(
                "star/sirius",
                LayerKind.STARS,
                "star",
                "Sirius",
                magnitude = -1.46,
                colorIndex = 0.0,
            )
        val scene = CatalogLayers.StarsMapping.toScene(30, listOf(sirius), emptyList())

        assertThat(scene.depth).isEqualTo(30)
        assertThat(scene.points).hasSize(1)
        val appearance = scene.points.single().appearance as PointAppearance.Stellar
        assertThat(appearance.magnitude).isEqualTo(-1.46)
        assertThat(appearance.colorIndex).isEqualTo(0.0)
        assertThat(scene.points.single().pos.length).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `unnamed stars render as points but get no label`() {
        val unnamed = obj("star/j0000", LayerKind.STARS, "star", name = "", magnitude = 5.5)
        val scene = CatalogLayers.StarsMapping.toScene(30, listOf(unnamed), emptyList())

        assertThat(scene.points).hasSize(1)
        assertThat(scene.labels).isEmpty()
    }

    @Test
    fun `named stars get a label carrying text and magnitude`() {
        val sirius = obj("star/sirius", LayerKind.STARS, "star", "Sirius", magnitude = -1.46)
        val scene = CatalogLayers.StarsMapping.toScene(30, listOf(sirius), emptyList())

        val label = scene.labels.single()
        assertThat(label.text).isEqualTo("Sirius")
        assertThat(label.magnitudeForThresholding).isEqualTo(-1.46)
    }

    @Test
    fun `stars whose best name is a secondary designation get no label`() {
        val gammaCas =
            obj(
                "star/j005648+604300",
                LayerKind.STARS,
                "star",
                name = "Gamma Cassiopeiae",
                magnitude = 2.47,
                nameIsPrimary = false,
            )
        val scene = CatalogLayers.StarsMapping.toScene(30, listOf(gammaCas), emptyList())

        assertThat(scene.points).hasSize(1)
        assertThat(scene.labels).isEmpty()
    }

    // ---- deep-sky mapping ------------------------------------------------------------

    @Test
    fun `deep-sky objects map to screen-space icons`() {
        val m31 = obj("dso/m31", LayerKind.DEEP_SKY, "galaxy.spiral", "M31", magnitude = 3.4)
        val scene = CatalogLayers.DeepSkyMapping.toScene(20, listOf(m31), emptyList())

        val appearance = scene.points.single().appearance as PointAppearance.Icon
        assertThat(appearance.image).isEqualTo(CatalogLayers.iconFor(TypeCode("galaxy.spiral")))
        assertThat(scene.labels.single().text).isEqualTo("M31")
    }

    @Test
    fun `deep-sky labels get a magnitude bonus so faint DSOs still label when zoomed in`() {
        // The Ring Nebula: mag 9.0, fainter than the ~8.4 threshold reachable at maximum zoom.
        val ringNebula =
            obj("dso/m57", LayerKind.DEEP_SKY, "nebula.planetary", "Ring Nebula", magnitude = 9.0)
        val dsoLabel =
            CatalogLayers.DeepSkyMapping.toScene(20, listOf(ringNebula), emptyList())
                .labels.single()
        // A star of the same real magnitude keeps its true (un-bonused) filter magnitude.
        val star = obj("star/faint", LayerKind.STARS, "star", "Faint", magnitude = 9.0)
        val starLabel =
            CatalogLayers.StarsMapping.toScene(30, listOf(star), emptyList()).labels.single()

        assertThat(starLabel.magnitudeForThresholding).isEqualTo(9.0)
        // 9.0 brightened by the 2.0 DSO bonus clears the ~8.4 max-zoom threshold.
        assertThat(dsoLabel.magnitudeForThresholding).isEqualTo(7.0)
        assertThat(dsoLabel.magnitudeForThresholding!!)
            .isLessThan(starLabel.magnitudeForThresholding!!)
    }

    @Test
    fun `icon resolution walks up the type hierarchy`() {
        // Exact match.
        assertThat(CatalogLayers.iconFor(TypeCode("cluster.globular")).key)
            .isEqualTo("icon/globular_cluster")
        // Subtype the map has never seen inherits its ancestor's icon.
        assertThat(CatalogLayers.iconFor(TypeCode("galaxy.spiral.barred")))
            .isEqualTo(CatalogLayers.iconFor(TypeCode("galaxy")))
        assertThat(CatalogLayers.iconFor(TypeCode("nebula.emission")).key)
            .isEqualTo("icon/diffuse_nebula")
        // Asterisms and star clouds share the D62 asterism glyph.
        assertThat(CatalogLayers.iconFor(TypeCode("asterism")).key).isEqualTo("icon/asterism")
        assertThat(CatalogLayers.iconFor(TypeCode("star_cloud")).key).isEqualTo("icon/asterism")
        // Unknown root falls back to the generic marker.
        assertThat(CatalogLayers.iconFor(TypeCode("black_hole")).key).isEqualTo("icon/other")
    }

    // ---- constellations mapping --------------------------------------------------------

    @Test
    fun `constellation figures become one line per stroke`() {
        val orion =
            obj("constellation/orion", LayerKind.CONSTELLATIONS, "constellation", "Orion")
        val figure =
            Figure(
                owner = CelestialObjectId("constellation/orion"),
                strokes =
                    listOf(
                        listOf(RaDec(83.0, -0.3), RaDec(84.1, -1.2), RaDec(85.2, -1.9)),
                        listOf(RaDec(88.8, 7.4), RaDec(81.3, 6.3)),
                    ),
            )
        val scene =
            CatalogLayers.ConstellationsMapping.toScene(10, listOf(orion), listOf(figure))

        assertThat(scene.lines).hasSize(2)
        assertThat(scene.lines[0].vertices).hasSize(3)
        assertThat(scene.lines[1].vertices).hasSize(2)
        // Vertices are unit direction vectors.
        assertThat(scene.lines[0].vertices[0].length).isWithin(1e-9).of(1.0)
        assertThat(scene.labels.single().text).isEqualTo("Orion")
        assertThat(scene.points).isEmpty()
    }

    @Test
    fun `constellations request figures, star and deep-sky layers do not`() {
        assertThat(CatalogLayers.ConstellationsMapping.usesFigures).isTrue()
        assertThat(CatalogLayers.StarsMapping.usesFigures).isFalse()
        assertThat(CatalogLayers.DeepSkyMapping.usesFigures).isFalse()
    }

    // ---- label priority ----------------------------------------------------------------

    @Test
    fun `brighter objects outrank fainter ones and magnitude-less objects rank mid-band`() {
        val sirius = CatalogLayers.labelPriority(-1.46)
        val faint = CatalogLayers.labelPriority(9.0)
        val constellation = CatalogLayers.labelPriority(null)

        assertThat(sirius).isGreaterThan(constellation)
        assertThat(constellation).isGreaterThan(faint)
        assertThat(CatalogLayers.labelPriority(-3.0)).isAtMost(100)
        assertThat(CatalogLayers.labelPriority(30.0)).isAtLeast(0)
    }

    // ---- layer set -----------------------------------------------------------------------

    @Test
    fun `create wires the v1 depth table`() {
        val layers =
            CatalogLayers.create(
                catalog = FakeCatalogRepository(),
                locale = kotlinx.coroutines.flow.emptyFlow(),
            ).associateBy { it.id }

        assertThat(layers[CatalogLayers.CONSTELLATIONS_LAYER_ID]!!.depth).isEqualTo(10)
        assertThat(layers[CatalogLayers.DEEP_SKY_LAYER_ID]!!.depth).isEqualTo(20)
        assertThat(layers[CatalogLayers.STARS_LAYER_ID]!!.depth).isEqualTo(30)
    }
}
