/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.location

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class StaticMapUrlTest {
    @Test
    fun `coordinates are rounded to three decimals`() {
        val url = staticMapUrl(latitudeDeg = 51.5072111, longitudeDeg = -0.1276555, apiKey = "k")
        assertThat(url).contains("center=lonlat:-0.128,51.507")
        assertThat(url).contains("marker=lonlat:-0.128,51.507;color:red")
    }

    @Test
    fun `gps jitter maps to the same url so the disk cache is hit`() {
        val a = staticMapUrl(latitudeDeg = 51.50702, longitudeDeg = -0.12758, apiKey = "k")
        val b = staticMapUrl(latitudeDeg = 51.50698, longitudeDeg = -0.12762, apiKey = "k")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `formatting is locale-independent`() {
        val original = Locale.getDefault()
        try {
            // Germany formats decimals with a comma, which would corrupt the query string.
            Locale.setDefault(Locale.GERMANY)
            val url = staticMapUrl(latitudeDeg = 51.5072, longitudeDeg = -0.1277, apiKey = "k")
            assertThat(url).contains("center=lonlat:-0.128,51.507")
        } finally {
            Locale.setDefault(original)
        }
    }
}
