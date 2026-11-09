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
import com.google.android.stardroid.data.satellites.TextFiles
import com.google.android.stardroid.data.satellites.TleStore
import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class SatelliteDiagnosticsTest {
    private class MemoryFiles : TextFiles {
        private val files = mutableMapOf<String, String>()

        override fun read(name: String) = files[name]

        override fun replace(
            name: String,
            text: String,
        ) {
            files[name] = text
        }
    }

    private fun repository(outcome: FetchOutcome) =
        SatelliteElementsRepository(TleStore(MemoryFiles()), CelesTrakClient { _, _ -> outcome })

    @Test
    fun beforeAnyFetchThereIsNothingToReport() {
        val state = repository(FetchOutcome.HttpError(403)).diagnostics()

        assertThat(state.freshness).isEqualTo(ElementFreshness.ABSENT)
        assertThat(state.satelliteCount).isEqualTo(0)
        assertThat(state.lastStatusCode).isNull()
    }

    @Test
    fun aRefusalShowsItsStatusCode() {
        val repository = repository(FetchOutcome.HttpError(403))
        repository.refresh()

        val state = repository.diagnostics()

        assertThat(state.lastStatusCode).isEqualTo(403)
        assertThat(state.consecutiveFailures).isEqualTo(1)
        assertThat(state.circuitOpenUntil).isNotNull()
    }
}
