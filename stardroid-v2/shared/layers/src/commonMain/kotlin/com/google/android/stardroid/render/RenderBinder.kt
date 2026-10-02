/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render

import com.google.android.stardroid.layers.SkyLayer
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The one class that touches both Flows and the renderer (layers-and-app.md): collects enabled
 * layers' scenes into [SkyRenderer.submit], the camera flow into [SkyRenderer.setCamera], and
 * the render-state flow into [SkyRenderer.setRenderState].
 *
 * Disabling a layer cancels its scene collection and submits `null`, which removes the layer
 * from the backend; re-enabling restarts the layer's flow from scratch. Collection lives in
 * the caller's [CoroutineScope] — cancel that scope to unbind everything.
 */
class RenderBinder(
    private val renderer: SkyRenderer,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun bindLayer(
        scope: CoroutineScope,
        layer: SkyLayer,
        enabled: (LayerId) -> Flow<Boolean>,
    ) {
        scope.launch {
            enabled(layer.id)
                .distinctUntilChanged()
                .flatMapLatest { on -> if (on) layer.scenes() else flowOf(null) }
                .collect { renderer.submit(layer.id, it) }
        }
    }

    fun bindCamera(
        scope: CoroutineScope,
        camera: Flow<SkyCamera>,
    ) {
        scope.launch { camera.collect { renderer.setCamera(it) } }
    }

    fun bindRenderState(
        scope: CoroutineScope,
        state: Flow<RenderState>,
    ) {
        scope.launch { state.collect { renderer.setRenderState(it) } }
    }
}
