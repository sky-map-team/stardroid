/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

import platform.Foundation.NSRecursiveLock

/** Foundation's recursive lock: reentrant, as the JVM monitor is. */
internal actual class ReentrantLock actual constructor() {
    @PublishedApi
    internal val lock = NSRecursiveLock()

    actual inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
