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
 * One layer's dot points (stars, fixed markers), interleaved as
 * `(x, y, z, r, g, b, a, sizeDp, magnitude)` per vertex — the portable `build` half of a
 * programmable backend's point drawer (render-gles3.md §6.5, D128).
 *
 * Colour tint, magnitude shade and size depend only on the scene, so they are baked. Alpha's
 * dependence on [RenderState.magnitudeLimit], and the night-mode transform, are *not*: the shader
 * applies them from uniforms, so neither invalidates this data. Icon points are excluded — they
 * are textured quads, not dots.
 */
class PointVertices private constructor(
    val data: FloatArray,
    val vertexCount: Int,
) {
    companion object {
        /** Floats per vertex: position 3, colour 4, size 1, magnitude 1. */
        const val FLOATS_PER_VERTEX = 9

        /**
         * The magnitude written for points whose brightness is fixed rather than catalogued. No
         * real limit is ever this bright, so the shader's magnitude fade leaves them alone.
         */
        const val NO_MAGNITUDE = -1e9f

        /** The uniform value standing in for a `null` limit: nothing is fainter than this. */
        const val NO_MAGNITUDE_LIMIT = 1e9f

        fun build(allPoints: List<PointPrimitive>): PointVertices {
            val points = allPoints.filterNot { it.appearance is PointAppearance.Icon }
            val data = FloatArray(points.size * FLOATS_PER_VERTEX)
            var i = 0
            for (point in points) {
                data[i++] = point.pos.x.toFloat()
                data[i++] = point.pos.y.toFloat()
                data[i++] = point.pos.z.toFloat()
                when (val appearance = point.appearance) {
                    is PointAppearance.Stellar -> {
                        val shade = StellarRamps.magnitudeShade(appearance.magnitude)
                        val tint = StellarRamps.colorForIndex(appearance.colorIndex)
                        data[i++] = tint.r * shade
                        data[i++] = tint.g * shade
                        data[i++] = tint.b * shade
                        data[i++] = 1f
                        data[i++] = StellarRamps.sizeDp(appearance.magnitude)
                        data[i++] = appearance.magnitude.toFloat()
                    }
                    is PointAppearance.Fixed -> {
                        data[i++] = appearance.color.r
                        data[i++] = appearance.color.g
                        data[i++] = appearance.color.b
                        data[i++] = appearance.color.a
                        data[i++] = appearance.sizeDp.toFloat()
                        data[i++] = NO_MAGNITUDE
                    }
                    is PointAppearance.Icon -> error("icon points were filtered out above")
                }
            }
            return PointVertices(data, points.size)
        }
    }
}
