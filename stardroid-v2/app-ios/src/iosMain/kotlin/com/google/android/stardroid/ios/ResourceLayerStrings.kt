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
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.earth
import com.google.android.stardroid.ui.resources.east
import com.google.android.stardroid.ui.resources.ecliptic
import com.google.android.stardroid.ui.resources.jupiter
import com.google.android.stardroid.ui.resources.mars
import com.google.android.stardroid.ui.resources.mercury
import com.google.android.stardroid.ui.resources.moon
import com.google.android.stardroid.ui.resources.nadir
import com.google.android.stardroid.ui.resources.neptune
import com.google.android.stardroid.ui.resources.north
import com.google.android.stardroid.ui.resources.north_pole
import com.google.android.stardroid.ui.resources.pluto
import com.google.android.stardroid.ui.resources.saturn
import com.google.android.stardroid.ui.resources.south
import com.google.android.stardroid.ui.resources.south_pole
import com.google.android.stardroid.ui.resources.sun
import com.google.android.stardroid.ui.resources.uranus
import com.google.android.stardroid.ui.resources.venus
import com.google.android.stardroid.ui.resources.west
import com.google.android.stardroid.ui.resources.zenith
import org.jetbrains.compose.resources.getString

/**
 * The layers' labels, read once from `:shared:ui`'s string resources in the app's language.
 * Compose resource lookup is suspending, so [load] resolves every label up front. iOS restarts
 * an app whose language changes, so one snapshot per launch is enough.
 */
class ResourceLayerStrings private constructor(
    override val northPole: String,
    override val southPole: String,
    override val zenith: String,
    override val nadir: String,
    override val north: String,
    override val south: String,
    override val east: String,
    override val west: String,
    override val ecliptic: String,
    private val bodies: Map<SolarSystemBody, String>,
) : LayerStrings {
    override fun bodyName(body: SolarSystemBody): String = bodies.getValue(body)

    companion object {
        suspend fun load(): ResourceLayerStrings =
            ResourceLayerStrings(
                northPole = getString(Res.string.north_pole),
                southPole = getString(Res.string.south_pole),
                zenith = getString(Res.string.zenith),
                nadir = getString(Res.string.nadir),
                north = getString(Res.string.north),
                south = getString(Res.string.south),
                east = getString(Res.string.east),
                west = getString(Res.string.west),
                ecliptic = getString(Res.string.ecliptic),
                bodies =
                    SolarSystemBody.entries.associateWith {
                        getString(
                            when (it) {
                                SolarSystemBody.SUN -> Res.string.sun
                                SolarSystemBody.MOON -> Res.string.moon
                                SolarSystemBody.MERCURY -> Res.string.mercury
                                SolarSystemBody.VENUS -> Res.string.venus
                                SolarSystemBody.EARTH -> Res.string.earth
                                SolarSystemBody.MARS -> Res.string.mars
                                SolarSystemBody.JUPITER -> Res.string.jupiter
                                SolarSystemBody.SATURN -> Res.string.saturn
                                SolarSystemBody.URANUS -> Res.string.uranus
                                SolarSystemBody.NEPTUNE -> Res.string.neptune
                                SolarSystemBody.PLUTO -> Res.string.pluto
                            },
                        )
                    },
            )
    }
}
