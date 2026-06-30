/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.activities

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.StardroidApplication
import com.google.android.stardroid.util.Experiment
import com.google.android.stardroid.util.ExperimentConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupRouter @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val app: StardroidApplication,
    private val experimentConfig: ExperimentConfig
) {
    fun needsEula() =
        sharedPreferences.getInt(ApplicationConstants.READ_TOS_PREF_VERSION, -1) != EULA_VERSION_CODE

    fun markEulaAccepted() {
        sharedPreferences.edit { putInt(ApplicationConstants.READ_TOS_PREF_VERSION, EULA_VERSION_CODE) }
    }

    fun needsWarmWelcome() =
        experimentConfig.isEnabled(Experiment.WARM_WELCOME) &&
            sharedPreferences.getLong(ApplicationConstants.READ_WARM_WELCOME_PREF_VERSION, -1) <= 0

    fun markWarmWelcomeSeen() {
        sharedPreferences.edit {
            putLong(ApplicationConstants.READ_WARM_WELCOME_PREF_VERSION, app.version)
            putBoolean(ApplicationConstants.NO_WARN_ABOUT_MISSING_SENSORS, true)
            // Mark what's new seen so fresh installs skip the dialog; upgrades still see it
            // because this method is only called after the warm welcome (fresh install path).
            putLong(ApplicationConstants.READ_WHATS_NEW_PREF_VERSION, app.version)
        }
    }

    fun needsWhatsNew() =
        sharedPreferences.getLong(ApplicationConstants.READ_WHATS_NEW_PREF_VERSION, -1) != app.version

    fun markWhatsNewSeen() {
        sharedPreferences.edit { putLong(ApplicationConstants.READ_WHATS_NEW_PREF_VERSION, app.version) }
    }

    companion object {
        private const val EULA_VERSION_CODE = 1
    }
}
