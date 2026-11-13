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

    @Test
    fun `a flag switched off on the server stops the run`() =
        runTest {
            assertThat(satellitesAllowed(FakeConfig(value = true, fetchedValue = false))).isFalse()
        }

    @Test
    fun `an enabled fresh flag allows the run`() =
        runTest { assertThat(satellitesAllowed(FakeConfig(value = true))).isTrue() }

    @Test
    fun `a failed fetch skips the run even if the stale value is on`() =
        runTest {
            assertThat(satellitesAllowed(FakeConfig(value = true, fresh = false))).isFalse()
        }

    @Test
    fun `a fetch that never completes is abandoned after the timeout`() =
        runTest {
            assertThat(satellitesAllowed(FakeConfig(value = true, hang = true), 1.seconds))
                .isFalse()
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
            assertThat(satellitesAllowed(ExperimentConfig { true })).isTrue()
        }
}
