/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.location

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class StaticMapUrlTest {
    @Test
    fun `coordinates are rounded to three decimals`() {
        val url = staticMapUrl(latitudeDeg = 51.5072111, longitudeDeg = -0.1276555, apiKey = "k")
        assertContains(url, "center=lonlat:-0.128,51.507")
        assertContains(url, "marker=lonlat:-0.128,51.507;color:red")
    }

    @Test
    fun `gps jitter maps to the same url so the disk cache is hit`() {
        val a = staticMapUrl(latitudeDeg = 51.50702, longitudeDeg = -0.12758, apiKey = "k")
        val b = staticMapUrl(latitudeDeg = 51.50698, longitudeDeg = -0.12762, apiKey = "k")
        assertEquals(a, b)
    }

    // The URL no longer goes through the platform's locale at all (formatAndroidStyle's
    // defaults are ASCII digits and a '.' separator), so a German or Arabic device can't
    // corrupt the query; the formatter's own tests cover locales.
    @Test
    fun `small and negative coordinates keep their sign and leading zero`() {
        val url = staticMapUrl(latitudeDeg = -33.86882, longitudeDeg = 0.05, apiKey = "k")
        assertContains(url, "center=lonlat:0.050,-33.869")
    }
}
