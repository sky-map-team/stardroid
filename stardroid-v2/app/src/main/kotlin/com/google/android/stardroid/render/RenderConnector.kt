/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render

import android.opengl.GLSurfaceView
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyRenderer
import com.google.android.stardroid.render.gles1.GLSkyRenderer

/**
 * Bridges [GLSkyRenderer] to a [GLSurfaceView] running in RENDERMODE_WHEN_DIRTY (D23).
 *
 * Every [submit] / [setCamera] / [setRenderState] delegates to the underlying renderer and then
 * calls [GLSurfaceView.requestRender] so the GL thread wakes and draws the updated state. When the
 * device is still (no camera, state, or scene changes), nothing redraws — battery-friendly.
 *
 * The future `:app` wiring: ViewModels collect each layer's `Flow<LayerScene>` into [submit] via
 * this connector, and [setCamera] is driven by the orientation sensor / sky-model flow. The
 * [GLSkyRenderer] and the [GLSurfaceView] are wired up in the Activity/Fragment; this class is
 * the only callsite that owns the `requestRender()` trigger.
 */
class RenderConnector(
    val renderer: GLSkyRenderer,
    private val surfaceView: GLSurfaceView,
) : SkyRenderer {
    override fun submit(
        layerId: LayerId,
        scene: LayerScene?,
    ) {
        renderer.submit(layerId, scene)
        surfaceView.requestRender()
    }

    override fun setCamera(camera: SkyCamera) {
        renderer.setCamera(camera)
        surfaceView.requestRender()
    }

    override fun setRenderState(state: RenderState) {
        renderer.setRenderState(state)
        surfaceView.requestRender()
    }
}
