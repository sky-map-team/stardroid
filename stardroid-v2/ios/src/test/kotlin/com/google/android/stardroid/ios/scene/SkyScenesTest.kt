/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.scene

import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.PointAppearance
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class SkyScenesTest {
    private val time = Instant.parse("2026-03-20T12:00:00Z")
    private val observer = LatLong(51.4769, 0.0)

    @Test
    fun stars_cullsFaintStarsAndLabelsOnlyBrightNamedOnes() {
        val scene =
            SkyScenes.stars(
                listOf(
                    CatalogStar("Sirius", Vector3.UNIT_X, -1.43),
                    CatalogStar("Faintish", Vector3.UNIT_Y, 4.0),
                    CatalogStar(null, Vector3.UNIT_Z, 1.0),
                    CatalogStar("Invisible", Vector3.UNIT_X, 7.5),
                ),
            )

        assertThat(scene.points).hasSize(3)
        assertThat(scene.labels.map { it.text }).containsExactly("Sirius")
        assertThat(scene.points.first().appearance).isEqualTo(PointAppearance.Stellar(-1.43))
    }

    @Test
    fun solarSystem_skipsEarthAndDrawsFarthestFirst() {
        val scene = SkyScenes.solarSystem(time, observer)
        assertThat(scene.points).hasSize(SolarSystemBody.entries.size - 1)
        assertThat(scene.labels.map { it.text }).doesNotContain("Earth")
        // The Moon is always nearest, so it paints last.
        assertThat(scene.labels.last().text).isEqualTo("Moon")
        val sun = MeeusEphemeris.topocentricPosition(SolarSystemBody.SUN, time, observer)
        val sunLabel = scene.labels.single { it.text == "Sun" }
        assertThat(sunLabel.pos.distanceTo(sun.toGeocentricVector())).isWithin(1e-12).of(0.0)
    }

    @Test
    fun horizon_liesInTheHorizontalPlaneWithFourCardinals() {
        val frame = SkyModel.localFrame(time, observer)
        val scene = SkyScenes.horizon(frame)
        val circle = scene.lines.single().vertices
        assertThat(circle.maxOf { kotlin.math.abs(it dot frame.up) }).isLessThan(1e-9)
        assertThat(circle.first().distanceTo(circle.last())).isLessThan(1e-9) // closed
        assertThat(scene.labels.map { it.text }).containsExactly("N", "E", "S", "W")
    }
}
