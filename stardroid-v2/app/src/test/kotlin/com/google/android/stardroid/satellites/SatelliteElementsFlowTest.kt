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
import com.google.android.stardroid.data.satellites.DirectoryTextFiles
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import com.google.android.stardroid.data.satellites.SatelliteTarget
import com.google.android.stardroid.data.satellites.TleStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    private val client =
        CelesTrakClient { target, _ ->
            val row =
                when (target) {
                    SatelliteTarget.ISS -> ISS_ROW
                    SatelliteTarget.CSS -> CSS_ROW
                }
            FetchOutcome.Success("$HEADER\n$row\n", null)
        }

    private fun repository() =
        SatelliteElementsRepository(
            TleStore(DirectoryTextFiles(tempDir)),
            client,
        )

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

            try {
                // Let the collector read the empty cache first. With a one-hour poll, only the fetch
                // announcement can produce the second emission.
                withTimeout(5_000) { while (seen.isEmpty()) delay(20) }
                assertThat(seen.first()).isEqualTo(ElementFreshness.ABSENT)

                repository.refresh()

                withTimeout(5_000) { while (ElementFreshness.FRESH !in seen) delay(20) }
                assertThat(seen.last()).isEqualTo(ElementFreshness.FRESH)
            } finally {
                job.cancel()
            }
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

            try {
                withTimeout(5_000) { while (seen.isEmpty()) delay(20) }
                assertThat(seen.first()).isEmpty()

                repository.refresh()

                withTimeout(5_000) { while (seen.none { it.size == 2 }) delay(20) }
                assertThat(seen.last()).containsExactly(25544, 48274)
            } finally {
                job.cancel()
            }
        }

    private companion object {
        const val HEADER =
            "OBJECT_NAME,OBJECT_ID,EPOCH,MEAN_MOTION,ECCENTRICITY," +
                "INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY," +
                "EPHEMERIS_TYPE,CLASSIFICATION_TYPE,NORAD_CAT_ID," +
                "ELEMENT_SET_NO,REV_AT_EPOCH,BSTAR,MEAN_MOTION_DOT," +
                "MEAN_MOTION_DDOT"
        const val ISS_ROW =
            "ISS (ZARYA),1998-067A,2026-11-14T07:16:30.203040,15.49368089," +
                ".000738,51.6324,277.2306,24.2289,335.9045,0,U,25544,999,59502," +
                ".17615E-3,.9396E-4,0"
        const val CSS_ROW =
            "CSS (TIANHE),2021-035A,2026-11-13T12:59:15.891360,15.61773849," +
                ".00043868,41.4674,134.3818,302.2376,57.8037,0,U,48274,999," +
                "31649,.20630815E-3,.18078E-3,0"
    }
}
