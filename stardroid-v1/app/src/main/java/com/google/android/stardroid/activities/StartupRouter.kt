package com.google.android.stardroid.activities

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.StardroidApplication
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupRouter @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val app: StardroidApplication
) {
    fun needsEula() =
        sharedPreferences.getInt(ApplicationConstants.READ_TOS_PREF_VERSION, -1) != EULA_VERSION_CODE

    fun markEulaAccepted() {
        sharedPreferences.edit { putInt(ApplicationConstants.READ_TOS_PREF_VERSION, EULA_VERSION_CODE) }
    }

    fun needsWarmWelcome() =
        ApplicationConstants.WARM_WELCOME_ENABLED &&
            sharedPreferences.getLong(ApplicationConstants.READ_WARM_WELCOME_PREF_VERSION, -1) <= 0

    fun markWarmWelcomeSeen() {
        sharedPreferences.edit {
            putLong(ApplicationConstants.READ_WARM_WELCOME_PREF_VERSION, app.version)
            putBoolean(ApplicationConstants.NO_WARN_ABOUT_MISSING_SENSORS, true)
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
