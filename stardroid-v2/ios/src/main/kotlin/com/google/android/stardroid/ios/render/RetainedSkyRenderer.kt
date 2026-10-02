/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.render

import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyRenderer
import com.google.android.stardroid.render.api.Viewport

/**
 * The [SkyRenderer] contract for the iOS shell: holds the latest scenes, camera and state, and
 * calls [onInvalidate] whenever any of them changes so the view can schedule a redraw.
 *
 * Every setter publishes an immutable snapshot, so producers may call from any thread and a
 * frame never sees a half-applied update (the contract's "latest value wins"). Writes take a
 * lock only to serialise the copy-on-write of the layer map — RoboVM's class library predates
 * `AtomicReference.updateAndGet`, so the lock-free form is not available.
 */
class RetainedSkyRenderer(
    private val onInvalidate: () -> Unit,
) : SkyRenderer {
    private val writeLock = Any()

    @Volatile private var scenes: Map<LayerId, LayerScene> = emptyMap()

    @Volatile private var camera: SkyCamera? = null

    @Volatile private var state: RenderState = RenderState()

    override fun submit(
        layerId: LayerId,
        scene: LayerScene?,
    ) {
        synchronized(writeLock) {
            scenes = if (scene == null) scenes - layerId else scenes + (layerId to scene)
        }
        onInvalidate()
    }

    override fun setCamera(camera: SkyCamera) {
        this.camera = camera
        onInvalidate()
    }

    override fun setRenderState(state: RenderState) {
        this.state = state
        onInvalidate()
    }

    /** The draw list for one frame, or nothing until a camera has been set. */
    fun plan(viewport: Viewport): List<DrawOp> {
        val camera = camera ?: return emptyList()
        return FramePlanner.plan(scenes.values, camera, state, viewport)
    }
}
