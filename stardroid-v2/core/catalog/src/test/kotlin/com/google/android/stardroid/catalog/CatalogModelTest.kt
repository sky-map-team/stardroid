/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CatalogModelTest {
    @Test
    fun `MonthDay rejects days that do not exist in the month`() {
        assertThrows(IllegalArgumentException::class.java) { MonthDay(2, 30) }
        assertThrows(IllegalArgumentException::class.java) { MonthDay(4, 31) }
        assertThrows(IllegalArgumentException::class.java) { MonthDay(1, 32) }
        assertThrows(IllegalArgumentException::class.java) { MonthDay(13, 1) }
    }

    @Test
    fun `MonthDay accepts month-end boundaries including leap day`() {
        assertThat(MonthDay(2, 29)).isEqualTo(MonthDay.parse("02-29"))
        assertThat(MonthDay(12, 31).day).isEqualTo(31)
        assertThat(MonthDay(4, 30).day).isEqualTo(30)
    }

    @Test
    fun `isA walks up the type hierarchy`() {
        val barred = TypeCode("galaxy.spiral.barred")
        assertThat(barred.isA(TypeCode("galaxy"))).isTrue()
        assertThat(barred.isA(TypeCode("galaxy.spiral"))).isTrue()
        assertThat(barred.isA(barred)).isTrue()
    }

    @Test
    fun `isA rejects non-ancestors and segment-prefix confusions`() {
        assertThat(TypeCode("galaxy.spiral").isA(TypeCode("nebula"))).isFalse()
        // An ancestor must match whole segments: "galaxy" is not "gal".
        assertThat(TypeCode("galaxy.spiral").isA(TypeCode("gal"))).isFalse()
        // A parent is not a kind of its own subtype.
        assertThat(TypeCode("galaxy").isA(TypeCode("galaxy.spiral"))).isFalse()
    }
}
