/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.SolarSystemBody

/** Deterministic English strings for layer tests; [suffix] distinguishes locale changes. */
data class FakeLayerStrings(private val suffix: String = "") : LayerStrings {
    override val northPole = "NP$suffix"
    override val southPole = "SP$suffix"
    override val zenith = "ZENITH$suffix"
    override val nadir = "NADIR$suffix"
    override val north = "NORTH$suffix"
    override val south = "SOUTH$suffix"
    override val east = "EAST$suffix"
    override val west = "WEST$suffix"
    override val ecliptic = "Ecliptic$suffix"

    override fun bodyName(body: SolarSystemBody): String = "${body.name}$suffix"
}
