/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.scene

import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SkyDataTest {
    @Test
    fun parseStars_readsPositionMagnitudeAndPrimaryName() {
        val stars =
            SkyData.parseStars(
                sequenceOf(
                    "id,ra_deg,dec_deg,magnitude,names",
                    "star/sirius,101.25,-16.71,-1.43,Sirius|Dog Star",
                    "star/j002624-000300,6.6,-0.05,6.16,",
                ),
            )

        assertThat(stars).hasSize(2)
        assertThat(stars[0].name).isEqualTo("Sirius")
        assertThat(stars[0].magnitude).isEqualTo(-1.43)
        val sirius = RaDec.fromGeocentricVector(stars[0].position)
        assertThat(sirius.raDeg).isWithin(1e-9).of(101.25)
        assertThat(sirius.decDeg).isWithin(1e-9).of(-16.71)
        assertThat(stars[1].name).isNull()
    }

    @Test
    fun parseStars_rejectsRowsWithTheWrongColumnCount() {
        assertThrows<IllegalArgumentException> {
            SkyData.parseStars(sequenceOf("header", "star/x,1.0,2.0,3.0,\"Name, quoted\""))
        }
    }

    @Test
    fun parseConstellations_groupsVerticesByFigureThenStroke() {
        val figures =
            SkyData.parseConstellations(
                sequenceOf(
                    "id,stroke,ra_deg,dec_deg",
                    "constellation/a,0,10.0,20.0",
                    "constellation/a,0,11.0,21.0",
                    "constellation/a,1,12.0,22.0",
                    "constellation/a,1,13.0,23.0",
                    "constellation/b,0,50.0,-5.0",
                    "constellation/b,0,51.0,-6.0",
                ),
            )

        assertThat(
            figures.map { it.id },
        ).containsExactly("constellation/a", "constellation/b").inOrder()
        assertThat(figures[0].strokes.map { it.size }).containsExactly(2, 2).inOrder()
        assertThat(
            RaDec.fromGeocentricVector(figures[0].strokes[1][0]).raDeg,
        ).isWithin(1e-9).of(12.0)
        assertThat(figures[1].strokes).hasSize(1)
    }
}
