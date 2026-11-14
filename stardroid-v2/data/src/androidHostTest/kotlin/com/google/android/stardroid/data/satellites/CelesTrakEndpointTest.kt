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
