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
 * An opaque key naming an image to be drawn (e.g. `"planet/jupiter"`, `"dso/m31"`).
 *
 * Producers name images; each backend owns resolution and texture lifecycle, keeping drawables,
 * assets, and future downloaded imagery out of this pure API (design G7,
 * docs/design/render-api.md). For the initial port a backend resolves keys to bundled
 * drawables/assets; downloaded imagery later flows through the same key behind an unchanged
 * contract.
 */
@JvmInline
value class ImageRef(val key: String)
