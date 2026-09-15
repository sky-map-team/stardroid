/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Satellite identity and the synthesized info card.
 *
 * Satellites are the only tappable object with no catalog row — they arrive from the network, so
 * they cannot ship in the bundled database like even the position-less planets do. That makes the
 * id namespace and the synthesized card load-bearing rather than incidental.
 */
class SatelliteIdsTest {
    private val issTle =
        Tle.parse(
            line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
            line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
            name = "ISS (ZARYA)",
        )

    @Test
    fun `ids round-trip through the satellite namespace`() {
        val id = SatelliteIds.idFor(25544)
        assertThat(id.value).isEqualTo("satellite/25544")
        assertThat(SatelliteIds.noradIdFor(id)).isEqualTo(25544)
    }

    @Test
    fun `ids from other namespaces are not mistaken for satellites`() {
        // The card lookup branches on this: a planet id must fall through to the catalog, not be
        // answered with a synthesized satellite card.
        assertThat(SatelliteIds.noradIdFor(CelestialObjectId("planet/mars"))).isNull()
        assertThat(SatelliteIds.noradIdFor(CelestialObjectId("messier/M31"))).isNull()
        assertThat(SatelliteIds.noradIdFor(CelestialObjectId("satellite/not-a-number"))).isNull()
    }

    @Test
    fun `the synthesized card carries the satellite's name and position`() {
        val position = RaDec(123.4, -12.3)
        val card = SatelliteIds.cardFor(issTle, position, description = "A space station")

        assertThat(card.id).isEqualTo(SatelliteIds.idFor(25544))
        assertThat(card.name).isEqualTo("ISS (ZARYA)")
        assertThat(card.position).isEqualTo(position)
        assertThat(card.description).isEqualTo("A space station")
    }

    @Test
    fun `an unnamed element set falls back to its catalog number`() {
        val bare =
            Tle.parse(
                line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
                line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
            )
        assertThat(SatelliteIds.cardFor(bare, null, null).name).isEqualTo("#25544")
    }

    @Test
    fun `the card claims no layer kind`() {
        // LayerKind names catalog-backed layers. A satellite is drawn by a computed layer and has
        // no catalog row at all, so claiming one would be a lie the card UI might act on.
        assertThat(SatelliteIds.cardFor(issTle, null, null).layerKind).isNull()
    }
}
