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
 * What `@Synchronized` gave the clocks on the JVM, which Kotlin/Native lacks. Reentrant because
 * the clocks call one another ([TimeTravelClock.setTimeTravelDate] pauses).
 */
internal expect class ReentrantLock() {
    inline fun <T> withLock(block: () -> T): T
}
