/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.FlagSource
import com.google.android.stardroid.startup.FlagSourceExperimentConfig

// A flag is on by default only if its iOS surface exists.
private val IosDefaults: ExperimentConfig =
    ExperimentConfig { ExperimentConfig.Static.isEnabled(it) }

// The flags the server may change on iOS: those with an iOS surface to switch. Add a flag here
// with the feature that gives it one.
private val ServerControlled: Set<Experiment> =
    setOf(Experiment.SATELLITES, Experiment.NOTIFICATIONS)

internal fun iosExperiments(source: FlagSource): ExperimentConfig =
    FlagSourceExperimentConfig(source, IosDefaults, ServerControlled)

/** No server: the shipped defaults, for a build without a Firebase configuration. */
internal object NoFlagSource : FlagSource {
    override val canFetch: Boolean = false

    override fun serverValue(key: String): Boolean? = null

    override val lastFetchTimeMillis: Long? = null

    override fun fetch(
        ignoreThrottle: Boolean,
        onDone: (updated: Boolean, error: String?) -> Unit,
    ) = onDone(false, "No Remote Config")
}
