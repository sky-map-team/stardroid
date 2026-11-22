/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Runs [source]'s fetches one at a time. Firebase answers a fetch made during another with
 * success and the old values, which would pass for a fresh kill-switch read.
 */
class SerializedFlagSource(
    private val source: FlagSource,
    private val scope: CoroutineScope,
) : FlagSource by source {
    private val mutex = Mutex()

    override fun fetch(
        ignoreThrottle: Boolean,
        onDone: (updated: Boolean, error: String?) -> Unit,
    ) {
        scope.launch {
            val (updated, error) = mutex.withLock { fetchOne(ignoreThrottle) }
            onDone(updated, error)
        }
    }

    private suspend fun fetchOne(ignoreThrottle: Boolean): Pair<Boolean, String?> =
        suspendCancellableCoroutine { continuation ->
            source.fetch(ignoreThrottle) { updated, error ->
                if (continuation.isActive) continuation.resume(updated to error)
            }
        }
}
