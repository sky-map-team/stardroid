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
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [StartupState] for JVM tests: every flow is a hot [MutableStateFlow]. */
class FakeStartupState : StartupState {
    val eulaAcceptedVersionState = MutableStateFlow(-1)

    override val eulaAcceptedVersion: Flow<Int> = eulaAcceptedVersionState

    override suspend fun setEulaAcceptedVersion(version: Int) {
        eulaAcceptedVersionState.value = version
    }

    val warmWelcomeSeenVersionState = MutableStateFlow(-1L)

    override val warmWelcomeSeenVersion: Flow<Long> = warmWelcomeSeenVersionState

    val whatsNewSeenVersionState = MutableStateFlow(-1L)

    override val whatsNewSeenVersion: Flow<Long> = whatsNewSeenVersionState

    override suspend fun setWhatsNewSeenVersion(version: Long) {
        whatsNewSeenVersionState.value = version
    }

    val suppressMissingSensorWarningState = MutableStateFlow(false)

    override val suppressMissingSensorWarning: Flow<Boolean> = suppressMissingSensorWarningState

    override suspend fun markWarmWelcomeSeen(version: Long) {
        warmWelcomeSeenVersionState.value = version
        whatsNewSeenVersionState.value = version
        suppressMissingSensorWarningState.value = true
    }

    override suspend fun markWarmWelcomeSeenOnUpgrade(version: Long) {
        warmWelcomeSeenVersionState.value = version
        suppressMissingSensorWarningState.value = true
    }
}
