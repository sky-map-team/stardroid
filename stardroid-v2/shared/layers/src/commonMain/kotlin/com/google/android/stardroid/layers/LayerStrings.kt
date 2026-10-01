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

/**
 * The localized label text the computed layers emit (layers-and-app.md): producers localize,
 * the renderer only draws. Layers take a `Flow<LayerStrings>` and re-emit their scene when the
 * locale changes, exactly as [CatalogLayer] does with `Flow<LocaleSpec>`.
 *
 * The Android implementation reads `R.string` resources; tests use a fixed fake.
 */
interface LayerStrings {
    val northPole: String
    val southPole: String
    val zenith: String
    val nadir: String
    val north: String
    val south: String
    val east: String
    val west: String
    val ecliptic: String

    fun bodyName(body: SolarSystemBody): String
}
