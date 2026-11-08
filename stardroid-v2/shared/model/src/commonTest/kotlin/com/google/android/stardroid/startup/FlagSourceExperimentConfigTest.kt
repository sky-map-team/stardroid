/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FlagSourceExperimentConfigTest {
    private class FakeSource(
        private val server: MutableMap<String, Boolean> = mutableMapOf(),
        override var lastFetchTimeMillis: Long? = null,
        private val outcome: Pair<Boolean, String?> = false to null,
        override val canFetch: Boolean = true,
        private val callbackTwice: Boolean = false,
    ) : FlagSource {
        var ignoredThrottle: Boolean? = null

        override fun serverValue(key: String): Boolean? = server[key]

        override fun fetch(
            ignoreThrottle: Boolean,
            onDone: (Boolean, String?) -> Unit,
        ) {
            ignoredThrottle = ignoreThrottle
            onDone(outcome.first, outcome.second)
            if (callbackTwice) onDone(outcome.first, outcome.second)
        }
    }

    private val defaults = ExperimentConfig { it == Experiment.SATELLITES }

    private fun config(
        source: FlagSource,
        controlled: Set<Experiment> = setOf(Experiment.SATELLITES, Experiment.GLES3_RENDERER),
    ) = FlagSourceExperimentConfig(source, defaults, controlled)

    @Test
    fun noServerValueMeansTheDefault() {
        val config = config(FakeSource())

        assertThat(config.isEnabled(Experiment.SATELLITES)).isTrue()
        assertThat(config.isEnabled(Experiment.GLES3_RENDERER)).isFalse()
    }

    @Test
    fun theServerOverridesADefaultInEitherDirection() {
        val source =
            FakeSource(
                mutableMapOf(
                    Experiment.SATELLITES.key to false,
                    Experiment.GLES3_RENDERER.key to true,
                ),
            )
        val config = config(source)

        assertThat(config.isEnabled(Experiment.SATELLITES)).isFalse()
        assertThat(config.isEnabled(Experiment.GLES3_RENDERER)).isTrue()
    }

    @Test
    fun aFlagWithNoSurfaceHereIgnoresTheServer() {
        val source = FakeSource(mutableMapOf(Experiment.CAMERA_AR.key to true))

        assertThat(config(source).isEnabled(Experiment.CAMERA_AR)).isFalse()
    }

    @Test
    fun valuesAreFrozenAtConstruction() {
        val server = mutableMapOf(Experiment.SATELLITES.key to true)
        val config = config(FakeSource(server))

        server[Experiment.SATELLITES.key] = false

        assertThat(config.isEnabled(Experiment.SATELLITES)).isTrue()
    }

    @Test
    fun fetchNowIgnoresTheThrottleAndReportsTheOutcome() =
        runTest {
            val source = FakeSource(outcome = true to null)

            val result = config(source).fetchNow()

            assertThat(source.ignoredThrottle).isEqualTo(true)
            assertThat(result).isEqualTo(FetchResult.Success(updated = true))
        }

    @Test
    fun aFailedFetchIsReported() =
        runTest {
            val result = config(FakeSource(outcome = false to "offline")).fetchNow()

            assertThat(result).isEqualTo(FetchResult.Failure("offline"))
        }

    @Test
    fun itCanFetchAndReportsTheLastFetchTime() {
        val config = config(FakeSource(lastFetchTimeMillis = 1234L))

        assertThat(config.canFetch).isTrue()
        assertThat(config.lastFetchTimeMillis).isEqualTo(1234L)
    }

    @Test
    fun canFetchFollowsTheSource() {
        assertThat(config(FakeSource(canFetch = false)).canFetch).isFalse()
    }

    @Test
    fun aSourceThatCallsBackTwiceIsTolerated() =
        runTest {
            val result = config(FakeSource(outcome = true to null, callbackTwice = true)).fetchNow()

            assertThat(result).isEqualTo(FetchResult.Success(updated = true))
        }
}
