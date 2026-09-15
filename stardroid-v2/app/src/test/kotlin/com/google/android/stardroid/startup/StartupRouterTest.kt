/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The startup gating cases screens-and-startup.md calls out as the fragile piece: fresh
 * install sees warm welcome not What's New; upgrade sees What's New; EULA re-prompt on
 * version bump.
 */
class StartupRouterTest {
    private val state = FakeStartupState()

    // A floor of 0 keeps these generic gating tests independent of the real
    // WARM_WELCOME_RESET_VERSION_CODE — only never-seen (-1) trips it, same as the old
    // `<= 0` check. Tests of the reset floor itself pass the real constant explicitly.
    private fun router(
        appVersion: Long = CURRENT_VERSION,
        warmWelcomeResetVersionCode: Long = 0L,
        isV1Upgrade: Boolean = false,
    ) = StartupRouter(state, appVersion, warmWelcomeResetVersionCode, isV1Upgrade)

    @Test
    fun `fresh install needs the eula until accepted`() =
        runTest {
            val router = router()
            assertThat(router.needsEula.first()).isTrue()

            router.markEulaAccepted()

            assertThat(router.needsEula.first()).isFalse()
        }

    @Test
    fun `eula re-prompts when its version is newer than the accepted one`() =
        runTest {
            // The user accepted an older EULA revision.
            state.eulaAcceptedVersionState.value = StartupRouter.EULA_VERSION_CODE - 1

            assertThat(router().needsEula.first()).isTrue()
        }

    @Test
    fun `fresh install sees the warm welcome, not What's New`() =
        runTest {
            val router = router()
            assertThat(router.needsWarmWelcome.first()).isTrue()

            router.markWarmWelcomeSeen()

            // The 14e83daf fix: completing the welcome also marks What's New seen and
            // suppresses the missing-sensor warning.
            assertThat(router.needsWarmWelcome.first()).isFalse()
            assertThat(router.needsWhatsNew.first()).isFalse()
            assertThat(state.suppressMissingSensorWarningState.value).isTrue()
        }

    @Test
    fun `the warm welcome is unconditional for fresh installs`() =
        runTest {
            // The experiment flag that used to gate this was retired at launch; a
            // never-seen install gets the pager with no further condition.
            assertThat(router().needsWarmWelcome.first()).isTrue()
        }

    @Test
    fun `a v1 upgrader still sees the warm welcome`() =
        runTest {
            // v2 ships under v1's applicationId, so this is an in-place upgrade — but v1
            // recorded its warm welcome in SharedPreferences under a different key
            // (`read_warm_welcome_version`) and D1 deliberately does not migrate v1's
            // preferences. The v2 key is therefore absent, and everyone upgrading from v1
            // sees the v2 welcome for this first version. Guards against a future
            // migration silently suppressing it.
            assertThat(state.warmWelcomeSeenVersionState.value).isEqualTo(-1L)
            assertThat(router().needsWarmWelcome.first()).isTrue()
        }

    @Test
    fun `a v1 upgrader also sees What's New, ahead of the warm welcome`() =
        runTest {
            // Unlike a true fresh install, a v1 upgrader has real prior-version history —
            // they should see both, What's New first (as an overlay ahead of the tour, per
            // needsWhatsNewDuringWarmWelcome), not have it silently suppressed.
            val router = router(isV1Upgrade = true)
            assertThat(router.needsWarmWelcome.first()).isTrue()
            assertThat(router.needsWhatsNew.first()).isTrue()
            assertThat(router.needsWhatsNewDuringWarmWelcome.first()).isTrue()

            router.markWarmWelcomeSeen()

            assertThat(router.needsWarmWelcome.first()).isFalse()
            assertThat(router.needsWhatsNew.first()).isTrue()
            assertThat(state.suppressMissingSensorWarningState.value).isTrue()
        }

    @Test
    fun `a true fresh install never needs What's New before the warm welcome`() =
        runTest {
            // isV1Upgrade defaults to false: nothing to distinguish this from a brand-new
            // install, so the ordering flag must stay false even while both gates are up.
            val router = router()
            assertThat(router.needsWarmWelcome.first()).isTrue()
            assertThat(router.needsWhatsNewDuringWarmWelcome.first()).isFalse()
        }

    @Test
    fun `an existing v2 tester's re-shown welcome also puts What's New first`() =
        runTest {
            // isV1Upgrade is false, but a device that already completed the welcome once
            // (PREVIOUS_VERSION > 0) has real prior-version history too — What's New leads
            // whenever both gates are up, the same rule as a v1 upgrader.
            state.warmWelcomeSeenVersionState.value = PREVIOUS_VERSION
            state.whatsNewSeenVersionState.value = PREVIOUS_VERSION

            val router = router(warmWelcomeResetVersionCode = CURRENT_VERSION)
            assertThat(router.needsWarmWelcome.first()).isTrue()
            assertThat(router.needsWhatsNew.first()).isTrue()
            assertThat(router.needsWhatsNewDuringWarmWelcome.first()).isTrue()
        }

    @Test
    fun `upgrade sees What's New but not the warm welcome`() =
        runTest {
            // A previous version completed the welcome and saw its What's New.
            state.warmWelcomeSeenVersionState.value = PREVIOUS_VERSION
            state.whatsNewSeenVersionState.value = PREVIOUS_VERSION

            val router = router()
            assertThat(router.needsWarmWelcome.first()).isFalse()
            assertThat(router.needsWhatsNew.first()).isTrue()

            router.markWhatsNewSeen()

            assertThat(router.needsWhatsNew.first()).isFalse()
            assertThat(state.whatsNewSeenVersionState.value).isEqualTo(CURRENT_VERSION)
        }

    @Test
    fun `same version does not re-show What's New`() =
        runTest {
            state.whatsNewSeenVersionState.value = CURRENT_VERSION

            assertThat(router().needsWhatsNew.first()).isFalse()
        }

    @Test
    fun `an existing v2 tester below the reset floor sees the warm welcome again`() =
        runTest {
            // A 2.0.0-alpha-series tester who already completed the welcome under an older
            // build. 2.0.0 is treated as a major-enough update to re-introduce itself.
            state.warmWelcomeSeenVersionState.value =
                StartupRouter.WARM_WELCOME_RESET_VERSION_CODE - 1

            val router =
                router(warmWelcomeResetVersionCode = StartupRouter.WARM_WELCOME_RESET_VERSION_CODE)
            assertThat(router.needsWarmWelcome.first()).isTrue()
        }

    @Test
    fun `a tester at or above the reset floor does not see the warm welcome again`() =
        runTest {
            state.warmWelcomeSeenVersionState.value = StartupRouter.WARM_WELCOME_RESET_VERSION_CODE

            val router =
                router(warmWelcomeResetVersionCode = StartupRouter.WARM_WELCOME_RESET_VERSION_CODE)
            assertThat(router.needsWarmWelcome.first()).isFalse()
        }

    @Test
    fun `completing a re-shown warm welcome does not silently mark What's New seen`() =
        runTest {
            // Unlike a true fresh install, this tester has real upgrade history — the
            // 14e83daf fix (marking What's New seen alongside the welcome) must not apply
            // here, or they'd never see what actually changed in this release.
            state.warmWelcomeSeenVersionState.value = PREVIOUS_VERSION
            state.whatsNewSeenVersionState.value = PREVIOUS_VERSION

            val router =
                router(warmWelcomeResetVersionCode = CURRENT_VERSION)
            assertThat(router.needsWarmWelcome.first()).isTrue()

            router.markWarmWelcomeSeen()

            assertThat(router.needsWarmWelcome.first()).isFalse()
            assertThat(router.needsWhatsNew.first()).isTrue()
            assertThat(state.suppressMissingSensorWarningState.value).isTrue()
        }

    private companion object {
        const val PREVIOUS_VERSION = 41L
        const val CURRENT_VERSION = 42L
    }
}
