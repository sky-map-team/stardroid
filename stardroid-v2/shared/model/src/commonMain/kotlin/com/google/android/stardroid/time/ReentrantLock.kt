/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

/**
 * A reentrant mutual-exclusion lock: what `@Synchronized` gave the clocks on the JVM, which
 * Kotlin/Native lacks. The clocks' few fields are written from the UI thread and read on the
 * render and background threads, and a read can write them too ([TimeTravelClock] re-anchors as
 * it answers), so each locked method still has to run whole. Reentrant because they call one
 * another ([TimeTravelClock.setTimeTravelDate] pauses).
 */
internal expect class ReentrantLock() {
    inline fun <T> withLock(block: () -> T): T
}
