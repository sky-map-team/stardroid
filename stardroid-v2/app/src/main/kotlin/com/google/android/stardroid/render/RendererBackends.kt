/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render

import android.app.ActivityManager
import android.content.Context
import android.content.res.AssetManager
import android.opengl.GLSurfaceView
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.RendererInfo
import com.google.android.stardroid.render.api.SkyRenderer
import com.google.android.stardroid.render.gles1.GLSkyRenderer
import com.google.android.stardroid.render.gles3.GLES3SkyRenderer
import com.google.android.stardroid.settings.RendererBackend
import com.google.android.stardroid.startup.Experiment

/**
 * A constructed rendering backend: the same object in both of the roles it plays, plus the EGL
 * context version its surface has to be created with.
 *
 * The two roles are why this exists. A backend is a [SkyRenderer] to everything upstream and a
 * [GLSurfaceView.Renderer] to the surface, and the EGL context version must be chosen *before*
 * the surface is created and cannot change afterwards — which is why switching backends needs
 * the activity to be recreated.
 */
class RendererBackendHandle(
    val skyRenderer: SkyRenderer,
    val surfaceRenderer: GLSurfaceView.Renderer,
    val eglContextClientVersion: Int,
)

/**
 * Builds the backend [backend] asks for, falling back to GLES1 when it cannot be honoured.
 *
 * Two independent reasons to fall back, and this is deliberately the *only* place either is
 * applied — a caller cannot end up with a GLES3 context by going round the side:
 *
 * - **[gles3Enabled] is false.** The [Experiment.GLES3_RENDERER] remote kill switch is off, so
 *   a device that previously selected GLES3 quietly returns to GLES1 on its next launch. That
 *   is the point of gating it remotely: a bad interaction with one vendor's driver can be
 *   turned off for everyone without shipping a release.
 * - **The device has no GL ES 3.0.** Real rather than defensive: GLES1 still ships, so such a
 *   device is supported rather than filtered out of the Play listing — the deliberate
 *   difference from `render-gles3.md` §6, which assumed GLES1 would be retired.
 *
 * Either way the fallback is silent by design. The caller records what was *asked for*, not what
 * was built, so a fallback cannot drive a recreate loop; `RendererInfo.backend` reports what
 * actually ran, and the diagnostics screen shows it.
 *
 * @param requestRender wired to the surface's `requestRender`, so the GLES3 backend can ask for
 *   the frames a label fade needs. See [GLES3SkyRenderer.onAnimating].
 */
@Suppress("LongParameterList")
fun createRendererBackend(
    context: Context,
    assets: AssetManager,
    backend: RendererBackend,
    gles3Enabled: Boolean,
    density: Float,
    imageLoader: (ImageRef) -> android.graphics.Bitmap?,
    onRendererInfo: (RendererInfo) -> Unit,
    requestRender: () -> Unit,
): RendererBackendHandle =
    if (backend == RendererBackend.GLES3 && gles3Enabled && supportsGles3(context)) {
        val renderer =
            GLES3SkyRenderer(
                assets = assets,
                density = density,
                imageLoader = imageLoader,
                onRendererInfo = onRendererInfo,
                onAnimating = requestRender,
            )
        RendererBackendHandle(renderer, renderer, eglContextClientVersion = 3)
    } else {
        val renderer = GLSkyRenderer(density, imageLoader, onRendererInfo)
        RendererBackendHandle(renderer, renderer, eglContextClientVersion = 1)
    }

/**
 * Whether this device advertises GL ES 3.0.
 *
 * The API level does not imply it — GL ES 3.0 shipped in Android 4.3, far below our floor, but a
 * device advertises the capability separately — so it has to be asked for. Requesting a version
 * the device does not have yields a context that fails to create, and the symptom is a black
 * screen with nothing in the log, so this check is what keeps the setting from being a trap.
 */
fun supportsGles3(context: Context): Boolean {
    val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return activityManager.deviceConfigurationInfo.reqGlEsVersion >= GLES3_VERSION
}

/** `reqGlEsVersion` packs the major version in the high 16 bits: 3.0 is 0x00030000. */
private const val GLES3_VERSION = 0x00030000
