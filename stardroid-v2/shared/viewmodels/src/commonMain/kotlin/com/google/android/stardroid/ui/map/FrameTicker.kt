/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

/**
 * Where the camera animations get their heartbeat (D93).
 *
 * v1 pumped its fling and horizon spring from a fixed 20 Hz executor, and the v2 port inherited
 * the rate. On a 120 Hz panel that draws five identical frames for every one that moves, which
 * reads as a hard stall the instant the finger lifts even though the display never misses a
 * frame (issue #959). Animations tick on this instead, so they advance once per drawn frame at
 * whatever rate the device actually runs, and tests can drive them on virtual time.
 */
fun interface FrameTicker {
    /** Suspends until the next frame is about to be drawn; returns its timestamp in nanos. */
    suspend fun awaitFrame(): Long
}
