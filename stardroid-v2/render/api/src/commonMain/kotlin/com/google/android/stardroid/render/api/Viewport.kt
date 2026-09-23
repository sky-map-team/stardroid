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
 * The drawing surface's size in pixels plus its display density. The surface owns its own size —
 * there is no `setViewport` on [SkyRenderer]; the backend reads it from `onSurfaceChanged` (px) and
 * the display metrics ([density]), and pairs it with a [SkyCamera] to form a [SkyProjection].
 */
data class Viewport(val widthPx: Int, val heightPx: Int, val density: Float) {
    init {
        require(widthPx > 0 && heightPx > 0) {
            "viewport dimensions must be positive, got ${widthPx}x$heightPx"
        }
        require(density > 0f) { "density must be positive, got $density" }
    }

    /** Width / height. Combined with the camera's vertical FOV this fixes the horizontal FOV. */
    val aspect: Float get() = widthPx.toFloat() / heightPx
}
