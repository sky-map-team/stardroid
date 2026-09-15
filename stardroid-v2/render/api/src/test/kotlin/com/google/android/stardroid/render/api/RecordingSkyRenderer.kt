/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory [SkyRenderer] that records the latest submission per [LayerId] plus the latest
 * camera and render state, with no GL. Lets future layer/ViewModel tests assert things like "the
 * stars layer submitted N points with magnitude data".
 *
 * Lives in this module's test source set for now; when `:data`/`:app` tests need it, promote it to
 * a `java-test-fixtures` source set so they can consume it (deferred until there is a consumer).
 */
class RecordingSkyRenderer : SkyRenderer {
    // Thread-safe to honor SkyRenderer's "callable from any thread" contract, so async/coroutine
    // producer tests can drive it without races.
    private val _scenes = ConcurrentHashMap<LayerId, LayerScene>()

    /** Latest scene submitted per layer; removed layers are absent. */
    val scenes: Map<LayerId, LayerScene> get() = _scenes

    @Volatile
    var lastCamera: SkyCamera? = null
        private set

    @Volatile
    var lastRenderState: RenderState? = null
        private set

    override fun submit(
        layerId: LayerId,
        scene: LayerScene?,
    ) {
        if (scene == null) _scenes.remove(layerId) else _scenes[layerId] = scene
    }

    override fun setCamera(camera: SkyCamera) {
        lastCamera = camera
    }

    override fun setRenderState(state: RenderState) {
        lastRenderState = state
    }
}
