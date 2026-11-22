/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import kotlinx.coroutines.flow.first

/**
 * The kill switch and the user's opt-out. The switch turns off at once, but on only at the next
 * launch where flags are frozen, so messages never show before Settings offers the opt-out.
 */
suspend fun announcementsOn(
    experiments: ExperimentConfig,
    settings: Settings,
): Boolean =
    experiments.isEnabled(Experiment.ANNOUNCEMENTS) &&
        experiments.isEnabledNow(Experiment.ANNOUNCEMENTS) &&
        settings.announcementsEnabled.first()
