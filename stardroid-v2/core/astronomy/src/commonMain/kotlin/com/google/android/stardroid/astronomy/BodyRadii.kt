/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

/** Kilometres in an astronomical unit (IAU 2012 definition, exact). */
const val KM_PER_AU = 149_597_870.7

/**
 * Mean equatorial radius in kilometres — IAU/NASA fact-sheet values, the same ones the planetary
 * fact sheets and JPL quote.
 *
 * Equatorial rather than mean-volumetric because these size a disc drawn on the sky, and a
 * flattened planet presents its equatorial diameter across. The difference matters for exactly
 * two bodies here: Jupiter is 6.5% flattened and Saturn 9.8%, so a volumetric radius would draw
 * both a few percent small.
 *
 * Public because the map draws bodies by physical size as well as apparent: the "Glyphs" mode
 * (D87) ranks them by how big they actually are, not how big they look from here.
 */
fun equatorialRadiusKm(body: SolarSystemBody): Double =
    when (body) {
        SolarSystemBody.SUN -> 696_000.0
        SolarSystemBody.MERCURY -> 2_439.7
        SolarSystemBody.VENUS -> 6_051.8
        SolarSystemBody.EARTH -> 6_378.1
        SolarSystemBody.MOON -> 1_737.4
        SolarSystemBody.MARS -> 3_396.2
        SolarSystemBody.JUPITER -> 71_492.0
        SolarSystemBody.SATURN -> 60_268.0
        SolarSystemBody.URANUS -> 25_559.0
        SolarSystemBody.NEPTUNE -> 24_764.0
        SolarSystemBody.PLUTO -> 1_188.3
    }
