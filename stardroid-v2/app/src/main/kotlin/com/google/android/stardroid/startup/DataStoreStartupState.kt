/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** [StartupState] on Preferences DataStore, sharing the app's single settings file. */
class DataStoreStartupState(
    private val dataStore: DataStore<Preferences>,
) : StartupState {
    override val eulaAcceptedVersion: Flow<Int> =
        safeData()
            .map { it[EULA_ACCEPTED_VERSION] ?: -1 }
            .distinctUntilChanged()

    override suspend fun setEulaAcceptedVersion(version: Int) {
        dataStore.edit { it[EULA_ACCEPTED_VERSION] = version }
    }

    override val warmWelcomeSeenVersion: Flow<Long> =
        safeData()
            .map { it[WARM_WELCOME_SEEN_VERSION] ?: -1L }
            .distinctUntilChanged()

    override val whatsNewSeenVersion: Flow<Long> =
        safeData()
            .map { it[WHATS_NEW_SEEN_VERSION] ?: -1L }
            .distinctUntilChanged()

    override suspend fun setWhatsNewSeenVersion(version: Long) {
        dataStore.edit { it[WHATS_NEW_SEEN_VERSION] = version }
    }

    override suspend fun markWarmWelcomeSeen(version: Long) {
        // Only called for a *true* fresh install (StartupRouter.markWarmWelcomeSeen's
        // !hasPriorHistory branch) — someone with no prior version to compare against, so
        // "what's new" is meaningless for them. All three writes land in one edit, as v1's
        // single SharedPreferences commit did:
        dataStore.edit {
            // The tour itself just completed.
            it[WARM_WELCOME_SEEN_VERSION] = version
            // Pre-emptively marks What's New seen too, so it never pops up afterward — a
            // fresh install has no "last version" to have a changelog against, and the tour
            // already covered what's in the app. (This is why this method must never be
            // called for anyone with real prior history: it would silently suppress a
            // changelog they should actually see — see markWarmWelcomeSeenOnUpgrade.)
            it[WHATS_NEW_SEEN_VERSION] = version
            // The tour's sensor-status slide already told them if a sensor is missing, so
            // the map screen's separate missing-sensor warning is redundant from here on.
            it[SUPPRESS_MISSING_SENSOR_WARNING] = true
        }
    }

    override suspend fun markWarmWelcomeSeenOnUpgrade(version: Long) {
        // Called instead of markWarmWelcomeSeen whenever the tour is re-shown to someone with
        // real prior-version history (an existing v2 tester past the reset floor, or a v1
        // upgrader) — deliberately leaves WHATS_NEW_SEEN_VERSION untouched, since they do have
        // something real to see a changelog against. The sensor opt-out still applies: the
        // tour's sensor slide plays the same way regardless of which path wrote this state.
        dataStore.edit {
            it[WARM_WELCOME_SEEN_VERSION] = version
            it[SUPPRESS_MISSING_SENSOR_WARNING] = true
        }
    }

    override val suppressMissingSensorWarning: Flow<Boolean> =
        safeData()
            .map { it[SUPPRESS_MISSING_SENSOR_WARNING] ?: false }
            .distinctUntilChanged()

    /** The preferences stream with the standard unreadable-file fallback. */
    private fun safeData(): Flow<Preferences> =
        dataStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }

    companion object {
        private val EULA_ACCEPTED_VERSION = intPreferencesKey("eula_accepted_version")

        private val WARM_WELCOME_SEEN_VERSION = longPreferencesKey("warm_welcome_seen_version")

        private val WHATS_NEW_SEEN_VERSION = longPreferencesKey("whats_new_seen_version")

        private val SUPPRESS_MISSING_SENSOR_WARNING =
            booleanPreferencesKey("suppress_missing_sensor_warning")
    }
}
