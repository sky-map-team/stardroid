/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.ui.map.FrameTicker
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.QuartzCore.CADisplayLink
import kotlin.coroutines.resume

/**
 * The map's animation heartbeat on iOS (D93): the display's own [CADisplayLink], as Android's is
 * its `Choreographer`, so a fling or zoom animation advances once per drawn frame at the panel's
 * real rate. The link runs only while an animation is waiting for a frame. Main thread only, where
 * the ViewModel's animations run.
 */
@OptIn(ExperimentalForeignApi::class)
class DisplayLinkFrameTicker : FrameTicker {
    private val waiting = mutableListOf<CancellableContinuation<Long>>()
    private val link = PausedDisplayLink(::tick)

    override suspend fun awaitFrame(): Long =
        suspendCancellableCoroutine { continuation ->
            waiting += continuation
            link.paused = false
            continuation.invokeOnCancellation { waiting.remove(continuation) }
        }

    private fun tick(displayLink: CADisplayLink) {
        val nanos = (displayLink.targetTimestamp * 1e9).toLong()
        val ready = waiting.toList()
        waiting.clear()
        ready.forEach { it.resume(nanos) }
        // Pause until the next awaitFrame, so an idle map costs no wakeups.
        link.paused = waiting.isEmpty()
    }
}
