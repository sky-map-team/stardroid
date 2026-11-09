/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject

/** A [CADisplayLink] on the main run loop that starts paused. Main thread only. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class PausedDisplayLink(
    private val onTick: (CADisplayLink) -> Unit,
) {
    private val target = Target()
    private val link =
        CADisplayLink.displayLinkWithTarget(target, NSSelectorFromString("tick:")).also {
            it.paused = true
            it.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        }

    var paused: Boolean
        get() = link.paused
        set(value) {
            link.paused = value
        }

    private inner class Target : NSObject() {
        @ObjCAction
        fun tick(displayLink: CADisplayLink) = onTick(displayLink)
    }
}
