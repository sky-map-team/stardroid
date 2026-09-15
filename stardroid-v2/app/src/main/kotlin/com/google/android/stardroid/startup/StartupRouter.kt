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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * v1 `StartupRouter`, ported verbatim over [StartupState] flows — it encodes hard-won
 * gating behavior (screens-and-startup.md):
 *
 * 1. EULA: gated on an accepted-EULA *version int*, so a future EULA change re-prompts.
 * 2. Warm welcome: normally fresh installs only (never-seen, not a version comparison). It
 *    shipped behind an experiment flag, which was retired once the feature launched — the
 *    pager is now unconditional for fresh installs. [WARM_WELCOME_RESET_VERSION_CODE] adds a
 *    one-off version floor on top of that: anyone whose seen-version predates it gets the tour
 *    again on upgrade too, which is how 2.0.0 re-introduces itself to existing v2 testers
 *    rather than only to fresh installs.
 * 3. What's New: stored seen-version != current app version — upgrades only, because
 *    completing a *true fresh-install* warm welcome also marks What's New as seen (v1 fix,
 *    commit 14e83daf). Anyone with real prior-version history — a v1 upgrader ([isV1Upgrade])
 *    or an existing v2 tester past the reset floor — skips that shortcut and must still see
 *    what changed. The UI always shows What's New first whenever both are pending; the only
 *    open question is *whether* both are pending, since a true fresh install can look
 *    identical to that state for one frame before it's ever touched anything.
 *    [needsWhatsNewDuringWarmWelcome] answers that: true only for real prior-version history,
 *    telling the UI it's safe to overlay What's New on the still-visible tour underneath.
 */
class StartupRouter(
    private val state: StartupState,
    val appVersion: Long,
    private val warmWelcomeResetVersionCode: Long = WARM_WELCOME_RESET_VERSION_CODE,
    val isV1Upgrade: Boolean = false,
) {
    val needsEula: Flow<Boolean> =
        state.eulaAcceptedVersion.map { it != EULA_VERSION_CODE }

    suspend fun markEulaAccepted() {
        state.setEulaAcceptedVersion(EULA_VERSION_CODE)
    }

    val needsWarmWelcome: Flow<Boolean> =
        state.warmWelcomeSeenVersion.map { it < warmWelcomeResetVersionCode }

    val needsWhatsNew: Flow<Boolean> =
        state.whatsNewSeenVersion.map { it != appVersion }

    /**
     * True only when the still-pending warm welcome belongs to someone with real
     * prior-version history (a v1 upgrader or an existing v2 tester past the reset floor) —
     * never for a true fresh install, even though its raw gates look identical for one frame.
     * Tells the UI it's safe to show What's New now, overlaid on the tour underneath, rather
     * than waiting for the tour to finish.
     */
    val needsWhatsNewDuringWarmWelcome: Flow<Boolean> =
        combine(state.warmWelcomeSeenVersion, needsWarmWelcome, needsWhatsNew) {
                seenVersion,
                warmWelcome,
                whatsNew,
            ->
            hasPriorHistory(seenVersion) && warmWelcome && whatsNew
        }

    suspend fun markWarmWelcomeSeen() {
        // A true fresh install — never seen in v2 before, and not a v1 upgrader either — is
        // the only cohort with no real prior-version history, so it's the only one where
        // completing the tour can also silently mark What's New seen. Everyone else (a
        // re-shown tour past the reset floor, or a v1 upgrader) has something real to show.
        if (hasPriorHistory(state.warmWelcomeSeenVersion.first())) {
            state.markWarmWelcomeSeenOnUpgrade(appVersion)
        } else {
            state.markWarmWelcomeSeen(appVersion)
        }
    }

    private fun hasPriorHistory(warmWelcomeSeenVersion: Long) =
        isV1Upgrade || warmWelcomeSeenVersion > 0

    suspend fun markWhatsNewSeen() {
        state.setWhatsNewSeenVersion(appVersion)
    }

    companion object {
        /** Bump to re-prompt every user with the new terms (v1 `EULA_VERSION_CODE`). */
        const val EULA_VERSION_CODE = 1

        /**
         * The versionCode of the 2.0.0-beta01:Hannah internal-track build. A one-off floor,
         * not a running mechanism: bumping it re-shows the warm welcome to everyone whose
         * stored seen-version falls below it, existing v2 testers included. Left where it is
         * unless a future release is similarly major — most releases should not touch this.
         */
        const val WARM_WELCOME_RESET_VERSION_CODE = 1713L
    }
}
