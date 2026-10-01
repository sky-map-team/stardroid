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
 * The contract every rendering backend implements. Producers push retained, whole-layer scenes and
 * per-frame camera/state; the backend draws them (D13).
 *
 * **Threading:** every method may be called from any thread. Each publishes an immutable reference
 * by atomic swap (no locks held during a draw) and *latest value wins*; the backend's render thread
 * reads the most recently published camera, state, and scene set each time it wakes. The API itself
 * is Flow-free — an `:app` adapter collects each producer's `Flow<LayerScene>` into [submit] — so
 * backends and tests need no coroutine machinery.
 *
 * The implementation lands with the GL backend (slice 3b); a recording fake validates the contract
 * for layer/ViewModel tests in the meantime.
 */
interface SkyRenderer {
    /**
     * Replaces the named layer's content; a `null` [scene] removes the layer. Thread-safe, cheap.
     */
    fun submit(
        layerId: LayerId,
        scene: LayerScene?,
    )

    /** Publishes the per-frame camera. Thread-safe; latest value wins. */
    fun setCamera(camera: SkyCamera)

    /** Publishes the global render state. Thread-safe; latest value wins. */
    fun setRenderState(state: RenderState)
}
