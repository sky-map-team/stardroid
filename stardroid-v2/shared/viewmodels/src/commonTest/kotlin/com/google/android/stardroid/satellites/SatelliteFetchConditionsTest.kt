/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.settings.FakeSettings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.FakeStartupState
import com.google.android.stardroid.startup.StartupRouter
import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SatelliteFetchConditionsTest {
    private val now = Instant.fromEpochMilliseconds(1_794_000_000_000)

    private val allOpen =
        SatelliteFetchConditions(
            featureEnabled = true,
            dataConsented = true,
            layerEnabled = true,
            termsAccepted = true,
            lastForegroundAt = now - 1.hours,
            backgroundUse = false,
        )

    @Test
    fun allowsAFetchWhenEveryGateIsOpen() {
        assertThat(allOpen.allowFetch(now)).isTrue()
    }

    @Test
    fun theFlagOffBlocks() {
        assertThat(allOpen.copy(featureEnabled = false).allowFetch(now)).isFalse()
    }

    @Test
    fun withdrawnConsentBlocks() {
        assertThat(allOpen.copy(dataConsented = false).allowFetch(now)).isFalse()
    }

    @Test
    fun theLayerOffBlocks() {
        assertThat(allOpen.copy(layerEnabled = false).allowFetch(now)).isFalse()
    }

    @Test
    fun termsNotAcceptedBlocks() {
        assertThat(allOpen.copy(termsAccepted = false).allowFetch(now)).isFalse()
    }

    @Test
    fun backgroundUseDoesNotOverrideAnyOtherGate() {
        val withWidget = allOpen.copy(backgroundUse = true, lastForegroundAt = null)
        assertThat(withWidget.copy(featureEnabled = false).allowFetch(now)).isFalse()
        assertThat(withWidget.copy(dataConsented = false).allowFetch(now)).isFalse()
        assertThat(withWidget.copy(layerEnabled = false).allowFetch(now)).isFalse()
        assertThat(withWidget.copy(termsAccepted = false).allowFetch(now)).isFalse()
    }

    @Test
    fun neverOpenedInTheForegroundBlocks() {
        assertThat(allOpen.copy(lastForegroundAt = null).allowFetch(now)).isFalse()
    }

    @Test
    fun foregroundUseCountsForFourteenDays() {
        val window = SatelliteFetchConditions.ACTIVE_USE_WINDOW
        assertThat(window).isEqualTo(14.days)
        assertThat(allOpen.copy(lastForegroundAt = now - window).allowFetch(now)).isTrue()
        assertThat(
            allOpen.copy(lastForegroundAt = now - window - 1.hours).allowFetch(now),
        ).isFalse()
    }

    @Test
    fun aWidgetOrPassAlertKeepsDownloadsGoingWithoutForegroundUse() {
        val lapsed = allOpen.copy(lastForegroundAt = now - 60.days)
        assertThat(lapsed.allowFetch(now)).isFalse()
        assertThat(lapsed.copy(backgroundUse = true).allowFetch(now)).isTrue()
        assertThat(allOpen.copy(lastForegroundAt = null, backgroundUse = true).allowFetch(now))
            .isTrue()
    }

    @Test
    fun switchedOnIgnoresUseButNotTheOtherGates() {
        val lapsed = allOpen.copy(lastForegroundAt = null)
        assertThat(lapsed.switchedOn).isTrue()
        assertThat(lapsed.copy(featureEnabled = false).switchedOn).isFalse()
        assertThat(lapsed.copy(dataConsented = false).switchedOn).isFalse()
        assertThat(lapsed.copy(layerEnabled = false).switchedOn).isFalse()
        assertThat(lapsed.copy(termsAccepted = false).switchedOn).isFalse()
    }

    @Test
    fun aStampWrittenByAClockFarAheadDoesNotCount() {
        assertThat(allOpen.copy(lastForegroundAt = now + 1.hours).allowFetch(now)).isTrue()
        assertThat(allOpen.copy(lastForegroundAt = now + 30.days).allowFetch(now)).isFalse()
    }

    @Test
    fun theFlowReadsEachGateFromItsStore() =
        runTest {
            val settings = FakeSettings()
            val startupState = FakeStartupState()
            val router = StartupRouter(startupState, appVersion = 1L)
            val backgroundUse = MutableStateFlow(false)
            var flagOn = true
            val conditions =
                satelliteFetchConditions(
                    experiments = ExperimentConfig { it == Experiment.SATELLITES && flagOn },
                    settings = settings,
                    startupRouter = router,
                    startupState = startupState,
                    backgroundUse = backgroundUse,
                )

            // A fresh install: the layer starts off, the terms are unread, nothing recorded.
            val fresh = conditions.first()
            assertThat(fresh.layerEnabled).isFalse()
            assertThat(fresh.termsAccepted).isFalse()
            assertThat(fresh.lastForegroundAt).isNull()
            assertThat(fresh.allowFetch(now)).isFalse()

            settings.setSatelliteDataEnabled(true)
            settings.setLayerEnabled(SatelliteLayer.LAYER_ID, true)
            router.markEulaAccepted()
            startupState.setLastForegroundAt(now)
            val ready = conditions.first()
            assertThat(ready).isEqualTo(
                SatelliteFetchConditions(
                    featureEnabled = true,
                    dataConsented = true,
                    layerEnabled = true,
                    termsAccepted = true,
                    lastForegroundAt = now,
                    backgroundUse = false,
                ),
            )
            assertThat(ready.allowFetch(now)).isTrue()

            backgroundUse.value = true
            assertThat(conditions.first().backgroundUse).isTrue()

            flagOn = false
            assertThat(conditions.first().featureEnabled).isFalse()
        }
}
