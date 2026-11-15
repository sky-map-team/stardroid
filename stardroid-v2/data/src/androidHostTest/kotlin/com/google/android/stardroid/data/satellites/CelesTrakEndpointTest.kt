/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CelesTrakEndpointTest {
    @Test
    fun `the ISS and the CSS are separate OMM CSV requests by catalog number`() {
        assertThat(CelesTrakEndpoint.gpUrl(CelesTrakEndpoint.GP_URL, SatelliteTarget.ISS))
            .isEqualTo("https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=CSV")
        assertThat(CelesTrakEndpoint.gpUrl(CelesTrakEndpoint.GP_URL, SatelliteTarget.CSS))
            .isEqualTo("https://celestrak.org/NORAD/elements/gp.php?CATNR=48274&FORMAT=CSV")
    }

    @Test
    fun `the user agent carries the version, the platform and the contact URL`() {
        assertThat(CelesTrakEndpoint.userAgent("2.4.0", "Android"))
            .isEqualTo("SkyMap/2.4.0 (Android; +https://github.com/sky-map-team/stardroid)")
    }

    @Test
    fun `the release name is not sent`() {
        assertThat(CelesTrakEndpoint.userAgent("2.3.2:Cassini", "iOS"))
            .isEqualTo("SkyMap/2.3.2 (iOS; +https://github.com/sky-map-team/stardroid)")
    }

    @Test
    fun `an unreadable version still gives a valid user agent`() {
        assertThat(CelesTrakEndpoint.userAgent("", "iOS")).startsWith("SkyMap/unknown (iOS;")
    }
}
