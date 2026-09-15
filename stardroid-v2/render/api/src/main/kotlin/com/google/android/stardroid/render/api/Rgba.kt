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
 * An abstract, backend-independent color with straight-alpha `Float` channels in `[0, 1]`.
 *
 * Producers emit `Rgba`; each rendering backend owns the mapping to its own color space and is the
 * sole place the night-mode red transform is applied (see [RenderState.nightMode]). v1 carried
 * colors as packed ARGB `Int`s threaded through the GL buffers; [fromArgb] bridges that legacy
 * representation for the port.
 */
data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    companion object {
        val WHITE = Rgba(1f, 1f, 1f)
        val BLACK = Rgba(0f, 0f, 0f)
        val TRANSPARENT = Rgba(0f, 0f, 0f, 0f)

        /** Unpacks a v1-style packed `0xAARRGGBB` color into straight-alpha `[0, 1]` channels. */
        fun fromArgb(argb: Int): Rgba =
            Rgba(
                r = ((argb shr 16) and 0xFF) / 255f,
                g = ((argb shr 8) and 0xFF) / 255f,
                b = (argb and 0xFF) / 255f,
                a = ((argb shr 24) and 0xFF) / 255f,
            )
    }
}
