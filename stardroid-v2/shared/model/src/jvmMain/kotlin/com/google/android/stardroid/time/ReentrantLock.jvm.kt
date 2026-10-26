/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

/** The JVM monitor, exactly as `@Synchronized` used it. */
internal actual class ReentrantLock actual constructor() {
    actual inline fun <T> withLock(block: () -> T): T = synchronized(this, block)
}
