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
import kotlin.coroutines.resume

/**
 * Where a platform's server-side flag values come from (Firebase Remote Config on iOS). The
 * Kotlin side owns the defaults and the [Experiment] list, so adding a flag needs no change to
 * the platform code that implements this.
 */
interface FlagSource {
    /** The value the server has set for [key], or null if it has set none or it can't be read. */
    fun serverValue(key: String): Boolean?

    /** Epoch millis of the last successful fetch, or null if there has been none. */
    val lastFetchTimeMillis: Long?

    /**
     * Fetches new values and stores them for the next launch. The usual throttle applies unless
     * [ignoreThrottle]. [onDone] gets whether the values changed, or an error message, exactly
     * once, from any thread.
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
 *   a fetch is stored and applies from the next launch.
 * - **Only [serverControlled] flags listen to the server.** A flag whose surface doesn't exist on
 *   this platform stays at its [defaults], whatever the shared server config says. Android's
 *   switching on a feature must not switch it on where it hasn't been built.
 * - **Any failure leaves the [defaults] in force.**
 */
class FlagSourceExperimentConfig(
    private val source: FlagSource,
    defaults: ExperimentConfig,
    serverControlled: Set<Experiment>,
) : ExperimentConfig {
    private val values: Map<Experiment, Boolean> =
        Experiment.entries.associateWith { experiment ->
            val server =
                if (experiment in serverControlled) {
                    source.serverValue(
                        experiment.key,
                    )
                } else {
                    null
                }
            server ?: defaults.isEnabled(experiment)
        }

    override fun isEnabled(experiment: Experiment): Boolean = values.getValue(experiment)

    override val canFetch: Boolean = true

    override val lastFetchTimeMillis: Long? get() = source.lastFetchTimeMillis

    override suspend fun fetchNow(): FetchResult =
        suspendCancellableCoroutine { continuation ->
            source.fetch(ignoreThrottle = true) { updated, error ->
                continuation.resume(
                    if (error == null) FetchResult.Success(updated) else FetchResult.Failure(error),
                )
            }
        }
}
