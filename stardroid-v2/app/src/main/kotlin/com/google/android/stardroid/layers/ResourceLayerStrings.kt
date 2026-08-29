/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import android.content.res.Resources
import com.google.android.stardroid.R
import com.google.android.stardroid.astronomy.SolarSystemBody

/**
 * [LayerStrings] backed by Android string resources — the layers' locale edge (D37). Strings are
 * looked up dynamically to respect system locale changes at runtime.
 */
class ResourceLayerStrings(
    private val resources: Resources,
) : LayerStrings {
    override val northPole: String get() = resources.getString(R.string.north_pole)
    override val southPole: String get() = resources.getString(R.string.south_pole)
    override val zenith: String get() = resources.getString(R.string.zenith)
    override val nadir: String get() = resources.getString(R.string.nadir)
    override val north: String get() = resources.getString(R.string.north)
    override val south: String get() = resources.getString(R.string.south)
    override val east: String get() = resources.getString(R.string.east)
    override val west: String get() = resources.getString(R.string.west)
    override val ecliptic: String get() = resources.getString(R.string.ecliptic)

    override fun bodyName(body: SolarSystemBody): String {
        val resId =
            when (body) {
                SolarSystemBody.SUN -> R.string.sun
                SolarSystemBody.MOON -> R.string.moon
                SolarSystemBody.MERCURY -> R.string.mercury
                SolarSystemBody.VENUS -> R.string.venus
                SolarSystemBody.EARTH -> R.string.earth
                SolarSystemBody.MARS -> R.string.mars
                SolarSystemBody.JUPITER -> R.string.jupiter
                SolarSystemBody.SATURN -> R.string.saturn
                SolarSystemBody.URANUS -> R.string.uranus
                SolarSystemBody.NEPTUNE -> R.string.neptune
                SolarSystemBody.PLUTO -> R.string.pluto
            }
        return resources.getString(resId)
    }
}
