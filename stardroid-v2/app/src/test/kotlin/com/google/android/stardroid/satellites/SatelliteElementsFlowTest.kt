/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.data.satellites.CelesTrakClient
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import com.google.android.stardroid.data.satellites.SatelliteGroup
import com.google.android.stardroid.data.satellites.TleStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.hours

/**
 * The map layer and the Layers badge must pick up a completed fetch without waiting out the
 * poll; a one-hour poll here makes any pass attributable to the fetch announcement alone.
 */
class SatelliteElementsFlowTest {
    @TempDir
    lateinit var tempDir: File

    private val issElements =
        """
        ISS (ZARYA)
        1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993
        2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882
        """.trimIndent()

    private val client =
        object : CelesTrakClient {
            override fun fetch(
                group: SatelliteGroup,
                ifModifiedSince: String?,
            ): FetchOutcome = FetchOutcome.Success(issElements, null)
        }

    private fun repository() =
        SatelliteElementsRepository(TleStore(tempDir), client, SatelliteGroup.STATIONS)

    @Test
    fun `the status flow reports a completed fetch, not waiting on the poll`() =
        runBlocking {
            val repository = repository()
            val seen = CopyOnWriteArrayList<ElementFreshness>()
            val job =
                launch {
                    satelliteUiStatusFlow(repository, pollInterval = 1.hours)
                        .collect { seen += it.freshness }
                }

            // Let the collector read the empty cache first. With a one-hour poll, only the fetch
            // announcement can produce the second emission.
            withTimeout(5_000) { while (seen.isEmpty()) delay(20) }
            assertThat(seen.first()).isEqualTo(ElementFreshness.ABSENT)

            repository.refresh()

            withTimeout(5_000) { while (ElementFreshness.FRESH !in seen) delay(20) }
            job.cancel()
            assertThat(seen.last()).isEqualTo(ElementFreshness.FRESH)
        }

    @Test
    fun `the elements flow reports parsed elements after a completed fetch`() =
        runBlocking {
            val repository = repository()
            val seen = CopyOnWriteArrayList<List<Int>>()
            val job =
                launch {
                    satelliteElementsFlow(repository, pollInterval = 1.hours)
                        .collect { seen += it.tles.map { tle -> tle.noradId } }
                }

            withTimeout(5_000) { while (seen.isEmpty()) delay(20) }
            assertThat(seen.first()).isEmpty()

            repository.refresh()

            withTimeout(5_000) { while (listOf(25544) !in seen) delay(20) }
            job.cancel()
            assertThat(seen.last()).containsExactly(25544)
        }
}
