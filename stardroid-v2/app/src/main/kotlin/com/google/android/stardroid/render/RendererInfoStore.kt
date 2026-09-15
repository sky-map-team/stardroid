/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render

import com.google.android.stardroid.render.api.RendererInfo

/**
 * Carries [RendererInfo] from the GL thread, which is the only place it can be read, to the
 * diagnostics screen, which is the only place it is shown.
 *
 * Deliberately a plain volatile box rather than a flow: the value is written at most once per
 * surface creation and read by a screen that already polls on a timer, so there is nothing to
 * observe and no lifecycle to manage. It stays null until the map has drawn once, which in
 * practice is always true by the time diagnostics can be opened.
 */
class RendererInfoStore {
    @Volatile
    private var info: RendererInfo? = null

    /** Called on the GL thread from the backend's surface-created callback. */
    fun set(info: RendererInfo) {
        this.info = info
    }

    /** Called from whatever thread the diagnostics snapshot happens to be built on. */
    fun get(): RendererInfo? = info
}
