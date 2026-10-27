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
import com.google.android.stardroid.render.api.ImageRef
import kotlinx.datetime.Instant

/**
 * The default [BodyImageMapper]: every body gets its bundled image (v1's
 * `show_planetary_images=true` behavior). Keys are in the `planet/` namespace, resolved by
 * `AssetImageLoader` against `assets/planets/` — NASA imagery, which is scientific data, not
 * branding.
 *
 * The Moon has one image rather than eight, since D88 made phase procedural: the renderer paints
 * the terminator from [ImagePrimitive.terminator][com.google.android.stardroid.render.api
 * .ImagePrimitive.terminator], so the bitmap is the plain fully-lit disc like every other body's.
 */
class PlanetImages : BodyImageMapper {
    override fun imageFor(
        body: SolarSystemBody,
        time: Instant,
    ): ImageRef? =
        when (body) {
            SolarSystemBody.EARTH -> null
            else -> ImageRef("planet/${body.name.lowercase()}")
        }
}
