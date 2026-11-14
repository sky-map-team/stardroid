/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val SATELLITE_FLAG_TIMEOUT: Duration = 10.seconds

/** The kill switch as seen after waiting for a fresh value. */
enum class SatelliteFlagState {
    ON,
    OFF,

    /** The fresh fetch failed, was throttled or timed out, so the flag can't be trusted. */
    UNKNOWN,
}

/**
 * Waits up to [timeout] for a fresh flag, then reads it at call time. Never answers
 * [SatelliteFlagState.ON] from a stale value: the flag exists to stop CelesTrak traffic. Call it
 * last, once the cheap local gates have passed, so only an install about to download pays for
 * the fetch.
 */
suspend fun satelliteFlagState(
    config: ExperimentConfig,
    timeout: Duration = SATELLITE_FLAG_TIMEOUT,
): SatelliteFlagState =
    when {
        withTimeoutOrNull(timeout) { config.awaitFresh() } != true -> SatelliteFlagState.UNKNOWN
        config.isEnabledNow(Experiment.SATELLITES) -> SatelliteFlagState.ON
        else -> SatelliteFlagState.OFF
    }
