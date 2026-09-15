/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.render.api.PointAppearance
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

/**
 * The satellite map layer.
 *
 * The astronomy underneath is already pinned against Skyfield in `:core:astronomy` (D93/D94), so
 * this suite is about the *layer's* decisions: what it draws, what it declines to draw, and the
 * gating that keeps it absent when the feature is off.
 */
class SatelliteLayerTest {
    private val issTle =
        Tle.parse(
            line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
            line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
            name = "ISS (ZARYA)",
        )

    private val london = LatLong(51.5074, -0.1278)
    private val time = Instant.parse("2026-08-15T06:57:40Z")

    private fun layer() = SatelliteLayer(flowOf(), flowOf(), flowOf())

    private fun elements(
        tles: List<Tle>,
        freshness: ElementFreshness,
    ) = SatelliteElements(tles, freshness, time, 1.0)

    @Test
    fun `draws a marker, a trail and a label for each satellite`() {
        val scene =
            layer().buildScene(elements(listOf(issTle), ElementFreshness.FRESH), time, london)

        // One head plus the trail behind it.
        assertThat(scene.points).hasSize(5)
        assertThat(scene.labels).hasSize(1)
        assertThat(scene.labels.single().text).isEqualTo("ISS (ZARYA)")
        assertThat(scene.labels.single().style.color).isEqualTo(SkyColors.SKY_LABEL)
    }

    @Test
    fun `the trail fades toward the tail so the direction of travel reads`() {
        val scene =
            layer().buildScene(elements(listOf(issTle), ElementFreshness.FRESH), time, london)
        val alphas =
            scene.points.map { (it.appearance as PointAppearance.Fixed).color.a }

        // Emitted oldest-first, so alpha increases along the list and the head is fully opaque.
        assertThat(alphas).isInOrder()
        assertThat(alphas.last()).isEqualTo(1f)
        assertThat(alphas.first()).isLessThan(alphas.last())
    }

    @Test
    fun `the marker is star-white rather than a third hue`() {
        // Lens Blue already means deep-sky and Planet Red already means planets and showers, so a
        // new hue would dilute an existing meaning to say what the trail says better. If someone
        // "brands" satellites later, this is the decision they are overriding.
        val scene =
            layer().buildScene(elements(listOf(issTle), ElementFreshness.FRESH), time, london)
        val head = scene.points.last().appearance as PointAppearance.Fixed
        assertThat(head.color.r).isEqualTo(1f)
        assertThat(head.color.g).isEqualTo(1f)
        assertThat(head.color.b).isEqualTo(1f)
    }

    @Test
    fun `no cached elements draws nothing at all`() {
        val scene = layer().buildScene(elements(emptyList(), ElementFreshness.ABSENT), time, london)
        assertThat(scene.points).isEmpty()
        assertThat(scene.labels).isEmpty()
    }

    @Test
    fun `stale elements still draw, because roughly right is still worth showing`() {
        // Past ten days the app suppresses precise pass *times* — a confidently wrong "21:47" is
        // worse than nothing — but the map layer keeps drawing, because a satellite in roughly the
        // right place is still pleasant and honest at map scale.
        val scene =
            layer().buildScene(elements(listOf(issTle), ElementFreshness.STALE), time, london)
        assertThat(scene.points).isNotEmpty()
    }

    @Test
    fun `an unnamed element set falls back to its catalog number`() {
        val bare =
            Tle.parse(
                line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
                line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
            )
        val scene = layer().buildScene(elements(listOf(bare), ElementFreshness.FRESH), time, london)
        assertThat(scene.labels.single().text).isEqualTo("#25544")
    }

    @Test
    fun `the layer sits in front of the solar system and behind the horizon`() {
        // D18 sorts by descending distance, so a satellite - four orders of magnitude closer than
        // anything else drawn - belongs nearest the viewer.
        // 55: in front of the solar system's 60, behind the horizon.
        assertThat(layer().depth).isEqualTo(55)
    }

    @Test
    fun `the toggle is absent, not merely off, when the experiment is disabled`() {
        // Matching how the CAMERA_AR-gated surfaces behave. A toggle for a feature that cannot do
        // anything invites a user to turn it on and conclude the app is broken.
        assertThat(LayerRegistry.toggleableIds(satellitesEnabled = false))
            .doesNotContain(SatelliteLayer.LAYER_ID)
        assertThat(LayerRegistry.toggleableIds(satellitesEnabled = true))
            .contains(SatelliteLayer.LAYER_ID)
    }

    @Test
    fun `co-located station modules and debris are not drawn`() {
        // Regression for something only a live fetch found. GROUP=stations sounds like "the space
        // stations" and is actually 21 objects: seven of them - the ISS modules plus the docked
        // Dragon, Progress and Cygnus - carry *identical* orbital elements, because they are
        // bolted together. Drawing the feed as-is stacked seven markers and seven labels on one
        // pixel, then five more at Tiangong, plus upper-stage debris and unviewable cubesats.
        //
        // These lines are verbatim from a real CelesTrak response.
        val feed =
            Tle.parseCatalog(
                """
                ISS (ZARYA)
                1 25544U 98067A   26228.56710022  .00005115  00000+0  99348-4 0  9991
                2 25544  51.6334   1.2594 0007609  53.1141 307.0544 15.49461657581119
                POISK
                1 36086U 09060A   26228.56710022  .00005115  00000+0  99348-4 0  9999
                2 36086  51.6334   1.2594 0007609  53.1141 307.0544 15.49461657580953
                ISS (NAUKA)
                1 49044U 21066A   26228.56710022  .00005115  00000+0  99348-4 0  9997
                2 49044  51.6334   1.2594 0007609  53.1141 307.0544 15.49461657580995
                FREGAT DEB
                1 49271U 11037PF  26228.29031530  .00012011  00000+0  18802-1 0  9992
                2 49271  51.6391 181.0493 0918758  54.6279 313.6967 12.43486252232523
                """.trimIndent(),
            )
        assertThat(feed).hasSize(4)

        val scene = layer().buildScene(elements(feed, ElementFreshness.FRESH), time, london)
        // Only the ISS itself, so exactly one marker and one label rather than four of each.
        assertThat(scene.labels.map { it.text }).containsExactly("ISS (ZARYA)")
    }
}
