/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.layers.LayerStrings

/**
 * The layers' labels in English, verbatim from Android's `strings.xml`, until iOS gets
 * localized strings: how strings are shared with iOS is decided with the screens (ios-port-plan
 * §3.4, phase 5). Catalog names are unaffected; they come from the database in every locale.
 */
object EnglishLayerStrings : LayerStrings {
    override val northPole = "NP"
    override val southPole = "SP"
    override val zenith = "ZENITH"
    override val nadir = "NADIR"
    override val north = "NORTH"
    override val south = "SOUTH"
    override val east = "EAST"
    override val west = "WEST"
    override val ecliptic = "Ecliptic"

    override fun bodyName(body: SolarSystemBody): String =
        when (body) {
            SolarSystemBody.SUN -> "Sun"
            SolarSystemBody.MOON -> "Moon"
            SolarSystemBody.MERCURY -> "Mercury"
            SolarSystemBody.VENUS -> "Venus"
            SolarSystemBody.EARTH -> "Earth"
            SolarSystemBody.MARS -> "Mars"
            SolarSystemBody.JUPITER -> "Jupiter"
            SolarSystemBody.SATURN -> "Saturn"
            SolarSystemBody.URANUS -> "Uranus"
            SolarSystemBody.NEPTUNE -> "Neptune"
            SolarSystemBody.PLUTO -> "Pluto"
        }
}
