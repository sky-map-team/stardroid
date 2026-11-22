/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Where a platform's server-side flag values come from (Firebase Remote Config on iOS). The
 * Kotlin side owns the defaults and the [Experiment] list, so adding a flag needs no change to
 * the platform code that implements this.
 */
interface FlagSource {
    /** False when there is no server to ask (no Firebase configuration). */
    val canFetch: Boolean

    /** The value the server has set for [key], or null if it has set none or it can't be read. */
    fun serverValue(key: String): Boolean?

    /** The text the server has set for [key], for parameters that aren't flags, or null. */
    fun serverText(key: String): String?

    /** Epoch millis of the last successful fetch, or null if there has been none. */
    val lastFetchTimeMillis: Long?

    /**
     * The usual throttle applies unless [ignoreThrottle]. [onDone] gets whether the values
     * changed, or an error message, exactly once, from any thread.
     */
    fun fetch(
        ignoreThrottle: Boolean,
        onDone: (updated: Boolean, error: String?) -> Unit,
    )
}

/**
 * An [ExperimentConfig] over a [FlagSource], with these rules:
 *
 * - **Frozen at construction.** Values are read once, so a screen never changes under the user;
 *   a fetch is stored and applies from the next launch. Only [isEnabledNow], for gates about to
 *   cause network traffic, reads the source at call time.
 * - **Only [serverControlled] flags listen to the server.** A flag whose surface doesn't exist on
 *   this platform stays at its [defaults], whatever the shared server config says. Android's
 *   switching on a feature must not switch it on where it hasn't been built.
 * - **Any failure leaves the [defaults] in force.**
 */
class FlagSourceExperimentConfig(
    private val source: FlagSource,
    private val defaults: ExperimentConfig,
    private val serverControlled: Set<Experiment>,
    private val clock: Clock = Clock.System,
) : ExperimentConfig {
    private val values: Map<Experiment, Boolean> = Experiment.entries.associateWith(::read)

    private fun read(experiment: Experiment): Boolean {
        val controlled = experiment in serverControlled
        val server = if (controlled) source.serverValue(experiment.key) else null
        return server ?: defaults.isEnabled(experiment)
    }

    override fun isEnabled(experiment: Experiment): Boolean = values.getValue(experiment)

    /** Read through to the source, so a fetch activated since launch counts. */
    override fun isEnabledNow(experiment: Experiment): Boolean = read(experiment)

    /**
     * A successful fetch within [FRESH_FETCH_FLOOR] counts as fresh without another, as on gms:
     * it skips the fetch the app just made, and keeps background wakes clear of Firebase's
     * throttle.
     */
    override suspend fun awaitFresh(): Boolean {
        if (!canFetch) return true
        val last = lastFetchTimeMillis
        val sinceLast = last?.let { clock.now().toEpochMilliseconds() - it }
        if (sinceLast != null && sinceLast in 0 until FRESH_FETCH_FLOOR.inWholeMilliseconds) {
            return true
        }
        return fetchNow() is FetchResult.Success
    }

    override val canFetch: Boolean get() = source.canFetch

    override val lastFetchTimeMillis: Long? get() = source.lastFetchTimeMillis

    // A caller that stops waiting leaves the fetch to finish and be stored, so its stamp counts
    // as fresh next time.
    override suspend fun fetchNow(): FetchResult =
        suspendCancellableCoroutine { continuation ->
            source.fetch(ignoreThrottle = true) { updated, error ->
                // A source that breaks the exactly-once contract must not crash the app.
                if (!continuation.isActive) return@fetch
                val result = error?.let { FetchResult.Failure(it) } ?: FetchResult.Success(updated)
                continuation.resume(result)
            }
        }
}

/** How recent a successful fetch must be to count as fresh, on gms and iOS alike. */
val FRESH_FETCH_FLOOR: Duration = 15.minutes
