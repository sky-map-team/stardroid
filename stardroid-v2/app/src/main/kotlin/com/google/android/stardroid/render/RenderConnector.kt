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

/**
 * Bridges a rendering backend to a [GLSurfaceView] running in RENDERMODE_WHEN_DIRTY (D23).
 *
 * Every [submit] / [setCamera] / [setRenderState] delegates to the underlying renderer and then
 * calls [GLSurfaceView.requestRender] so the GL thread wakes and draws the updated state. When
 * the device is still (no camera, state, or scene changes), nothing redraws — battery-friendly.
 *
 * [renderer] is typed to the [SkyRenderer] contract rather than to a concrete backend, which is
 * the whole of what makes the backend swappable: a backend also plays a second role, as the
 * surface's [GLSurfaceView.Renderer], and both are settled at the one construction site in
 * `MainActivity`. Everything downstream of here — `RenderBinder`, the ViewModels, the layers —
 * never learns which backend it is talking to.
 *
 * This class is the only callsite that owns the `requestRender()` trigger.
 */
class RenderConnector(
    val renderer: SkyRenderer,
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
