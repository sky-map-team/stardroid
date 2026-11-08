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

/**
 * The shipped experiment defaults, minus notifications: iOS delivers none yet, so Settings and
 * Help hide them rather than offer switches that only ask for a permission. Drop the override
 * with the platform surfaces that deliver them (and add them to `permissions_notice_ios`).
 */
internal val IosExperiments: ExperimentConfig =
    ExperimentConfig { it != Experiment.NOTIFICATIONS && ExperimentConfig.Static.isEnabled(it) }
