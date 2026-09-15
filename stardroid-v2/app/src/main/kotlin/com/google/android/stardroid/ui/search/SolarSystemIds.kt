/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.search

import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.catalog.CelestialObjectId

/**
 * The `planet/<body>` join between catalog ids and [SolarSystemBody] (source-data/README.md):
 * the ephemeris positions every body the catalog stores position-less, including `planet/sun`
 * and `planet/moon`. Earth never appears — the observer stands on it.
 */
object SolarSystemIds {
    /** The catalog id namespace the ephemeris can resolve. */
    private const val PLANET_ID_PREFIX = "planet/"

    fun idFor(body: SolarSystemBody): CelestialObjectId =
        CelestialObjectId(PLANET_ID_PREFIX + body.name.lowercase())

    fun bodyFor(id: CelestialObjectId): SolarSystemBody? {
        if (!id.value.startsWith(PLANET_ID_PREFIX)) return null
        val name = id.value.removePrefix(PLANET_ID_PREFIX)
        return SolarSystemBody.entries.firstOrNull {
            it != SolarSystemBody.EARTH && it.name.equals(name, ignoreCase = true)
        }
    }
}
