/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface

/**
 * A current GL ES 3.0 context on a tiny pbuffer, for instrumented tests that need to talk to a
 * real driver but have nothing to show.
 *
 * No activity, no surface view, no frame: an offscreen context is the whole apparatus a shader
 * compile needs, and it makes the gate fast enough to run on every build.
 */
class OffscreenGlContext private constructor(
    private val display: EGLDisplay,
    private val context: EGLContext,
    private val surface: EGLSurface,
) {
    fun release() {
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    companion object {
        /** Creates and makes current a GL ES 3.0 context, or returns null if unsupported. */
        fun createOrNull(): OffscreenGlContext? {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val configAttribs =
                intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE,
                )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            val chosen =
                EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, configCount, 0)
            if (!chosen || configCount[0] == 0) {
                EGL14.eglTerminate(display)
                return null
            }

            val context =
                EGL14.eglCreateContext(
                    display,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                    0,
                )
            if (context == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglTerminate(display)
                return null
            }

            val surface =
                EGL14.eglCreatePbufferSurface(
                    display,
                    configs[0],
                    intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE),
                    0,
                )
            if (surface == EGL14.EGL_NO_SURFACE ||
                !EGL14.eglMakeCurrent(display, surface, surface, context)
            ) {
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                return null
            }
            return OffscreenGlContext(display, context, surface)
        }

        /** `EGL_OPENGL_ES3_BIT_KHR`; EGL14 does not expose a constant for it. */
        private const val EGL_OPENGL_ES3_BIT = 0x0040
    }
}
