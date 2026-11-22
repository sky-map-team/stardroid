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
import kotlin.coroutines.resume

/**
 * Runs [source]'s fetches one at a time. Firebase answers a fetch made during another with
 * success and the old values, which would pass for a fresh kill-switch read. Whoever asks during
 * a fetch shares the next one, forced if any of them forced it. No timeout: moving on from a
 * fetch still running would be that same overlap. [scope] must be single-threaded.
 */
class SerializedFlagSource(
    private val source: FlagSource,
    private val scope: CoroutineScope,
) : FlagSource by source {
    private class Caller(
        val ignoreThrottle: Boolean,
        val onDone: (updated: Boolean, error: String?) -> Unit,
    )

    private var waiting = mutableListOf<Caller>()
    private var fetching = false

    override fun fetch(
        ignoreThrottle: Boolean,
        onDone: (updated: Boolean, error: String?) -> Unit,
    ) {
        scope.launch {
            waiting += Caller(ignoreThrottle, onDone)
            if (fetching) return@launch
            fetching = true
            try {
                while (waiting.isNotEmpty()) {
                    val callers = waiting
                    waiting = mutableListOf()
                    val (updated, error) = fetchOne(callers.any { it.ignoreThrottle })
                    callers.forEach { it.onDone(updated, error) }
                }
            } finally {
                // iOS's exception hook logs and carries on, so a throw mustn't end fetching.
                fetching = false
            }
        }
    }

    private suspend fun fetchOne(ignoreThrottle: Boolean): Pair<Boolean, String?> =
        suspendCancellableCoroutine { continuation ->
            source.fetch(ignoreThrottle) { updated, error ->
                if (continuation.isActive) continuation.resume(updated to error)
            }
        }
}
