/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import kotlinx.coroutines.flow.Flow

/**
 * The persisted first-run bookkeeping behind [StartupRouter]
 * (screens-and-startup.md): v1's `read_tos_version` / `read_warm_welcome_version` /
 * `read_whats_new_version1` SharedPreferences ints and longs as a small DataStore state.
 * Keys are new — v1 prefs aren't migrated (D1), so existing upgraders see What's New once.
 */
interface StartupState {
    /** The EULA *version int* the user accepted; -1 until any acceptance (v1 semantics). */
    val eulaAcceptedVersion: Flow<Int>

    suspend fun setEulaAcceptedVersion(version: Int)

    /**
     * The app version when the warm welcome was completed; <= 0 means never — the gate is
     * "ever seen", not a version comparison (v1 semantics).
     */
    val warmWelcomeSeenVersion: Flow<Long>

    /** The app version whose What's New the user has seen; -1 until any. */
    val whatsNewSeenVersion: Flow<Long>

    suspend fun setWhatsNewSeenVersion(version: Long)

    /**
     * Completing the warm welcome writes three facts in one edit (v1's 2026 fix, commit
     * 14e83daf): the welcome itself, What's New for [version] (fresh installs must never see
     * the What's New dialog, since they have no prior version to compare against), and the
     * missing-sensor warning opt-out (the welcome's sensor slide already showed the status).
     */
    suspend fun markWarmWelcomeSeen(version: Long)

    /**
     * Completing a *re-shown* warm welcome (`StartupRouter.WARM_WELCOME_RESET_VERSION_CODE`):
     * an existing tester, not a fresh install, so unlike [markWarmWelcomeSeen] this leaves
     * [whatsNewSeenVersion] untouched — they have real upgrade history and must still see
     * What's New. The sensor opt-out still applies; the tour's sensor slide played either way.
     */
    suspend fun markWarmWelcomeSeenOnUpgrade(version: Long)

    /**
     * Suppress the map's missing-sensor warning (v1's `no warn about missing sensors`),
     * set by warm-welcome completion.
     */
    val suppressMissingSensorWarning: Flow<Boolean>
}
