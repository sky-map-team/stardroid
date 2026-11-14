/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.FetchResult
import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The fresh-flag gate both platforms call before downloading (#1275, #1285). */
class SatelliteFlagStateTest {
    private class FakeConfig(
        private var value: Boolean,
        private val fetchedValue: Boolean = value,
        private val fresh: Boolean = true,
        private val hang: Boolean = false,
    ) : ExperimentConfig {
        override fun isEnabled(experiment: Experiment) = value

        override suspend fun awaitFresh(): Boolean {
            if (hang) awaitCancellation()
            if (fresh) value = fetchedValue
            return fresh
        }
    }

    /** iOS's shape: [isEnabled] keeps the launch value, [isEnabledNow] sees the fetch. */
    private class FrozenConfig(
        private val atLaunch: Boolean,
        private val fetched: Boolean,
    ) : ExperimentConfig {
        private var now = atLaunch

        override fun isEnabled(experiment: Experiment) = atLaunch

        override fun isEnabledNow(experiment: Experiment) = now

        override suspend fun awaitFresh(): Boolean {
            now = fetched
            return true
        }
    }

    private suspend fun state(
        config: ExperimentConfig,
        timeout: Duration = 10.seconds,
    ) = satelliteFlagState(config, timeout)

    @Test
    fun aFlagSwitchedOffOnTheServerReadsAsOff() =
        runTest {
            assertThat(state(FakeConfig(value = true, fetchedValue = false)))
                .isEqualTo(SatelliteFlagState.OFF)
        }

    @Test
    fun anEnabledFreshFlagReadsAsOn() =
        runTest {
            assertThat(state(FakeConfig(value = true))).isEqualTo(SatelliteFlagState.ON)
        }

    @Test
    fun aFailedOrThrottledFetchIsUnknownEvenIfTheStaleValueIsOn() =
        runTest {
            assertThat(state(FakeConfig(value = true, fresh = false)))
                .isEqualTo(SatelliteFlagState.UNKNOWN)
        }

    @Test
    fun aFetchThatNeverCompletesIsUnknownAfterTheTimeout() =
        runTest {
            assertThat(state(FakeConfig(value = true, hang = true), 1.seconds))
                .isEqualTo(SatelliteFlagState.UNKNOWN)
        }

    @Test
    fun theFlagIsReadAtCallTimeNotFromTheLaunchValue() =
        runTest {
            assertThat(state(FrozenConfig(atLaunch = true, fetched = false)))
                .isEqualTo(SatelliteFlagState.OFF)
            assertThat(state(FrozenConfig(atLaunch = false, fetched = true)))
                .isEqualTo(SatelliteFlagState.ON)
        }

    @Test
    fun aServerBackedConfigIsFreshOnlyIfTheFetchSucceeded() =
        runTest {
            val ok =
                object : ExperimentConfig {
                    override fun isEnabled(experiment: Experiment) = true

                    override val canFetch = true

                    override suspend fun fetchNow() = FetchResult.Success(updated = false)
                }
            val failed =
                object : ExperimentConfig {
                    override fun isEnabled(experiment: Experiment) = true

                    override val canFetch = true

                    override suspend fun fetchNow() = FetchResult.Failure("offline")
                }
            assertThat(ok.awaitFresh()).isTrue()
            assertThat(failed.awaitFresh()).isFalse()
        }

    @Test
    fun aConfigWithNoServerIsAlwaysCurrent() =
        runTest {
            assertThat(ExperimentConfig { true }.awaitFresh()).isTrue()
            assertThat(state(ExperimentConfig { true })).isEqualTo(SatelliteFlagState.ON)
        }
}
