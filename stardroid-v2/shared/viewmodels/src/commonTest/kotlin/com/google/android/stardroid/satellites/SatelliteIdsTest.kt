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
import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

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

    @Test
    fun `catalog content is overlaid on the live card without moving it`() {
        val position = RaDec(123.4, -12.3)
        val live = SatelliteIds.cardFor(issTle, position, description = "generic")
        val catalogRow =
            live.copy(
                name = "",
                position = null,
                description = "The station",
                funFact = "A fact",
                distance = "About 400 km",
                size = "109 m",
                mass = "420 t",
                imageRef = "spacecraft/nasa_iss.webp",
                imageCredit = "NASA",
            )

        val card = SatelliteIds.withCatalogContent(live, catalogRow)

        assertThat(card.name).isEqualTo("ISS (ZARYA)")
        assertThat(card.position).isEqualTo(position)
        assertThat(card.description).isEqualTo("The station")
        assertThat(card.funFact).isEqualTo("A fact")
        assertThat(card.distance).isEqualTo("About 400 km")
        assertThat(card.size).isEqualTo("109 m")
        assertThat(card.mass).isEqualTo("420 t")
        assertThat(card.imageRef).isEqualTo("spacecraft/nasa_iss.webp")
        assertThat(card.imageCredit).isEqualTo("NASA")
    }

    @Test
    fun `a satellite with no catalog row keeps its generic card`() {
        val live = SatelliteIds.cardFor(issTle, RaDec(1.0, 2.0), description = "generic")

        assertThat(SatelliteIds.withCatalogContent(live, null)).isEqualTo(live)
    }

    @Test
    fun `a catalog row with no prose falls back to the live card's`() {
        val live = SatelliteIds.cardFor(issTle, RaDec(1.0, 2.0), description = "generic")
        val imageOnly = live.copy(description = null, funFact = null, imageRef = "a/b.webp")

        val card = SatelliteIds.withCatalogContent(live, imageOnly)

        assertThat(card.description).isEqualTo("generic")
        assertThat(card.imageRef).isEqualTo("a/b.webp")
    }
}
