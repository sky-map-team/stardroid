/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

/**
 * Identifies a submittable layer (stars, constellations, solar system, grid, …).
 *
 * A producer owns one [LayerId] and replaces that layer's whole content on each
 * [SkyRenderer.submit]; the [LayerScene.depth] field — not this id — determines draw order.
 */
@JvmInline
value class LayerId(val id: String)
