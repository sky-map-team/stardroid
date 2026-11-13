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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SatelliteKillSwitchTest {
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

    private suspend fun state(
        config: ExperimentConfig,
        timeout: Duration = 10.seconds,
    ) = satelliteFlagState(config, timeout)

    @Test
    fun `a flag switched off on the server reads as off`() =
        runTest {
            assertThat(state(FakeConfig(value = true, fetchedValue = false)))
                .isEqualTo(SatelliteFlagState.OFF)
        }

    @Test
    fun `an enabled fresh flag reads as on`() =
        runTest {
            assertThat(state(FakeConfig(value = true))).isEqualTo(SatelliteFlagState.ON)
        }

    @Test
    fun `a failed or throttled fetch is unknown even if the stale value is on`() =
        runTest {
            assertThat(state(FakeConfig(value = true, fresh = false)))
                .isEqualTo(SatelliteFlagState.UNKNOWN)
        }

    @Test
    fun `a fetch that never completes is unknown after the timeout`() =
        runTest {
            assertThat(state(FakeConfig(value = true, hang = true), 1.seconds))
                .isEqualTo(SatelliteFlagState.UNKNOWN)
        }

    @Test
    fun `only an unknown flag asks WorkManager to retry, and only on is allowed to download`() {
        assertThat(satelliteFlagWorkAction(SatelliteFlagState.UNKNOWN))
            .isEqualTo(SatelliteWorkAction.RETRY)
        assertThat(satelliteFlagWorkAction(SatelliteFlagState.OFF))
            .isEqualTo(SatelliteWorkAction.SUCCEED)
        assertThat(satelliteFlagWorkAction(SatelliteFlagState.ON)).isNull()
    }

    @Test
    fun `a server-backed config is fresh only if the fetch succeeded`() =
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
    fun `a config with no server is always current`() =
        runTest {
            assertThat(ExperimentConfig { true }.awaitFresh()).isTrue()
            assertThat(state(ExperimentConfig { true })).isEqualTo(SatelliteFlagState.ON)
        }
}
