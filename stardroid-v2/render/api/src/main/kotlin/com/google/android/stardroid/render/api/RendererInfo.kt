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
 * What the active [SkyRenderer] backend and the GPU under it turned out to be, captured once the
 * drawing surface exists.
 *
 * This is support data, not render state: it exists so a user reporting "the sky is black" or
 * "the lines look wrong" can hand over the GPU and driver they are actually running, which no
 * other diagnostic on the device reveals. Backends publish it through their own construction
 * callback rather than through [SkyRenderer], which stays a pure drawing contract.
 *
 * @property backend which backend produced this — `"gles1"` today, the discriminator once a
 *   second backend exists.
 * @property vendor `GL_VENDOR`, e.g. `"Qualcomm"`.
 * @property renderer `GL_RENDERER`, e.g. `"Adreno (TM) 620"` — the single most useful field.
 * @property version `GL_VERSION`, e.g. `"OpenGL ES 3.2 V@0502.0"`. Note this reports what the
 *   driver *offers*, which may exceed the context we actually asked for; [backend] says what we
 *   are using.
 * @property maxTextureSizePx `GL_MAX_TEXTURE_SIZE` — the cap the label atlas already clamps to.
 * @property lineWidthRange `GL_ALIASED_LINE_WIDTH_RANGE`. GL ES only guarantees a maximum of
 *   1.0, and `glLineWidth` **silently clamps** out-of-range values with no error, so a device
 *   reporting `1.0..1.0` is drawing every line one pixel wide whatever width the layer asked
 *   for. There is no way to detect that from inside the app other than reading this.
 * @property pointSizeRange `GL_SMOOTH_POINT_SIZE_RANGE`, which may likewise be `1.0..1.0` and
 *   collapse every anti-aliased star to a single pixel.
 */
data class RendererInfo(
    val backend: String,
    val vendor: String,
    val renderer: String,
    val version: String,
    val maxTextureSizePx: Int,
    val lineWidthRange: Range?,
    val pointSizeRange: Range?,
) {
    /** An inclusive GL capability range, as reported by `glGetFloatv`. */
    data class Range(val min: Float, val max: Float) {
        /** True when the range admits nothing wider than a single pixel. */
        val isSinglePixelOnly: Boolean get() = max <= 1f
    }
}
