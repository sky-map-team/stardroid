/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import android.view.Choreographer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The production ticker: the display's [Choreographer], which is also what drives the GL surface,
 * so an animation stepped here lands one new camera pose per drawn frame at the panel's real
 * refresh rate.
 *
 * Compose's `withFrameNanos` would be the tidier spelling but it throws unless a
 * `MonotonicFrameClock` is in the calling context, and `viewModelScope` has none. Must be awaited
 * from a Looper thread — the main thread, in practice, which is where the view model's
 * animations run.
 */
object ChoreographerFrameTicker : FrameTicker {
    override suspend fun awaitFrame(): Long =
        suspendCancellableCoroutine { continuation ->
            val choreographer = Choreographer.getInstance()
            val callback = Choreographer.FrameCallback { nanos -> continuation.resume(nanos) }
            choreographer.postFrameCallback(callback)
            continuation.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
        }
}
