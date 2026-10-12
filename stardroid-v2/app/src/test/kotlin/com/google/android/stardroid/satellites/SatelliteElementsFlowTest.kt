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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
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
    fun `the status flow re-emits when a fetch completes, not on the poll`() =
        runBlocking {
            val repository = repository()
            val flow = satelliteUiStatusFlow(repository, pollInterval = 1.hours)
            val seen = async { withTimeout(5_000) { flow.take(2).toList() } }
            // The first emission is the poll's immediate read of the empty cache.
            while (repository.freshness() == ElementFreshness.ABSENT && !seen.isCompleted) {
                delay(50)
                repository.refresh()
            }

            assertThat(seen.await().map { it.freshness })
                .containsExactly(ElementFreshness.ABSENT, ElementFreshness.FRESH)
                .inOrder()
        }

    @Test
    fun `the elements flow re-emits parsed elements when a fetch completes`() =
        runBlocking {
            val repository = repository()
            val flow = satelliteElementsFlow(repository, pollInterval = 1.hours)
            val seen = async { withTimeout(5_000) { flow.take(2).toList() } }
            while (repository.freshness() == ElementFreshness.ABSENT && !seen.isCompleted) {
                delay(50)
                repository.refresh()
            }

            val elements = seen.await()
            assertThat(elements.first().tles).isEmpty()
            assertThat(elements.last().tles.map { it.noradId }).containsExactly(25544)
        }
}
