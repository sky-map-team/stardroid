/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.satellites

import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.testing.assertThat
import kotlinx.datetime.Instant
import kotlin.test.Test

class TrackedSatellitesTest {
    private val iss =
        Tle.parse(
            line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
            line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
            name = "ISS (ZARYA)",
        )
    private val at = Instant.parse("2026-08-16T04:00:00Z")
    private val london = LatLong(51.5, -0.1)

    private fun elements(
        freshness: ElementFreshness,
        vararg tles: Tle,
    ) = SatelliteElements(tles.toList(), freshness, fetchedAt = null, ageDays = null)

    @Test
    fun `a cached station becomes a target with its name and position`() {
        val result =
            trackedSatellites(elements(ElementFreshness.FRESH, iss), london, null, at)

        assertThat(result.map { it.info.name }).containsExactly("ISS (ZARYA)")
        assertThat(result.single().position).isNotNull()
    }

    @Test
    fun `nothing cached gives nothing`() {
        val result = trackedSatellites(elements(ElementFreshness.ABSENT), london, null, at)

        assertThat(result).isEmpty()
    }
}
