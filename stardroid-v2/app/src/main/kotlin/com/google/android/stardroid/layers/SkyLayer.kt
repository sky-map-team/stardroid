/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import kotlinx.coroutines.flow.Flow

/**
 * One producer of sky content (layers-and-app.md): a cold [scenes] flow of complete
 * [LayerScene]s, collected into `SkyRenderer.submit(id, scene)` while the layer is enabled.
 * Layers declare their dependencies via their constructors — there is no shared context bundle.
 *
 * The toggle-UI display name (`nameRes` in the design) is deferred to the slice that builds the
 * layer-toggle UI; adding it now would be API without a consumer.
 */
interface SkyLayer {
    val id: LayerId

    /**
     * User-settable choices this layer offers, rendered as an inline expander under its row in
     * the Layers sheet (D87). Empty for layers that have nothing to configure, which is all of
     * them but the solar system so far.
     *
     * A layer reads its own parameter from [Settings][com.google.android.stardroid.settings
     * .Settings] and rebuilds its scene when it changes, rather than the value riding on
     * `RenderState`. D87 proposed the latter, by analogy with `magnitudeLimit`; the analogy does
     * not hold. `magnitudeLimit` re-filters a hundred thousand catalog points, where avoiding a
     * resubmission is the whole point, while this changes ten images on a user action that
     * happens once in a session — and routing it through `RenderState` would teach the renderer
     * what a "disc size" is.
     */
    val parameters: List<LayerParameter>
        get() = emptyList()

    /** Back-to-front ordering; v1's depth table carries over (grid 0 … horizon 90). */
    val depth: Int

    fun scenes(): Flow<LayerScene>
}
