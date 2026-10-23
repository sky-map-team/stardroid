/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.satellites.SatelliteIds
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/** Instrumented because [highlightLine] formats its text from real resources. */
@RunWith(AndroidJUnit4::class)
class TonightFormatsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun a_satellite_pass_row_opens_that_satellites_card() {
        val start = Instant.parse("2026-10-10T19:30:00Z")
        val pass =
            SatellitePass(
                satelliteName = "ISS (ZARYA)",
                noradId = 25544,
                start = start,
                culmination = start + 3.minutes,
                end = start + 6.minutes,
                maxAltitudeDeg = 60.0,
                startAzimuthDeg = 250.0,
                endAzimuthDeg = 60.0,
                peakMagnitude = -3.1,
                shadowEntry = null,
            )

        val line = highlightLine(SkyEvent.SatellitePassTonight(pass, quality = 0.5), context)

        // Not the layer id: the card lookup resolves nothing for that (#1105).
        assertThat(line.targetId).isEqualTo(SatelliteIds.idFor(25544).value)
    }
}
