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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class FlagSourceExperimentConfigTest {
    private class FakeSource(
        private val server: MutableMap<String, Boolean> = mutableMapOf(),
        override var lastFetchTimeMillis: Long? = null,
        private val outcome: Pair<Boolean, String?> = false to null,
        override val canFetch: Boolean = true,
        private val callbackTwice: Boolean = false,
    ) : FlagSource {
        var ignoredThrottle: Boolean? = null
        var fetches = 0

        override fun serverValue(key: String): Boolean? = server[key]

        override fun fetch(
            ignoreThrottle: Boolean,
            onDone: (Boolean, String?) -> Unit,
        ) {
            ignoredThrottle = ignoreThrottle
            fetches++
            onDone(outcome.first, outcome.second)
            if (callbackTwice) onDone(outcome.first, outcome.second)
        }
    }

    private val defaults = ExperimentConfig { it == Experiment.SATELLITES }

    private val now = Instant.parse("2026-11-14T12:00:00Z")

    private val clock =
        object : Clock {
            override fun now() = now
        }

    private fun config(
        source: FlagSource,
        controlled: Set<Experiment> = setOf(Experiment.SATELLITES, Experiment.GLES3_RENDERER),
    ) = FlagSourceExperimentConfig(source, defaults, controlled, clock)

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
    fun isEnabledNowReadsTheSourceAtCallTime() {
        val server = mutableMapOf(Experiment.SATELLITES.key to true)
        val config = config(FakeSource(server))

        server[Experiment.SATELLITES.key] = false

        assertThat(config.isEnabled(Experiment.SATELLITES)).isTrue()
        assertThat(config.isEnabledNow(Experiment.SATELLITES)).isFalse()
    }

    @Test
    fun isEnabledNowStillIgnoresTheServerForAFlagWithNoSurfaceHere() {
        val config = config(FakeSource(mutableMapOf(Experiment.CAMERA_AR.key to true)))

        assertThat(config.isEnabledNow(Experiment.CAMERA_AR)).isFalse()
    }

    @Test
    fun aRecentSuccessfulFetchIsFreshWithoutAnother() =
        runTest {
            val recent = now - FRESH_FETCH_FLOOR + 1.minutes
            val source = FakeSource(lastFetchTimeMillis = recent.toEpochMilliseconds())

            assertThat(config(source).awaitFresh()).isTrue()
            assertThat(source.fetches).isEqualTo(0)
        }

    @Test
    fun anOlderFetchMeansFetchingAgain() =
        runTest {
            val old = now - FRESH_FETCH_FLOOR - 1.minutes
            val ok =
                FakeSource(
                    lastFetchTimeMillis = old.toEpochMilliseconds(),
                    outcome = false to null,
                )
            val failed =
                FakeSource(
                    lastFetchTimeMillis = old.toEpochMilliseconds(),
                    outcome = false to "offline",
                )

            assertThat(config(ok).awaitFresh()).isTrue()
            assertThat(ok.fetches).isEqualTo(1)
            assertThat(config(failed).awaitFresh()).isFalse()
        }

    @Test
    fun neverFetchedOrAFutureStampMeansFetching() =
        runTest {
            val never = FakeSource()
            val future = FakeSource(lastFetchTimeMillis = (now + 1.minutes).toEpochMilliseconds())

            config(never).awaitFresh()
            config(future).awaitFresh()

            assertThat(never.fetches).isEqualTo(1)
            assertThat(future.fetches).isEqualTo(1)
        }

    @Test
    fun aSourceThatCannotFetchIsAlwaysCurrent() =
        runTest {
            val source = FakeSource(canFetch = false)

            assertThat(config(source).awaitFresh()).isTrue()
            assertThat(source.fetches).isEqualTo(0)
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
