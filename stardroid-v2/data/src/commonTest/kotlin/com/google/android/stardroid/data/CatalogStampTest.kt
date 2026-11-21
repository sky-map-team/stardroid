/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class CatalogStampTest {
    /** A SQLite header carrying [schema] at offset 60 and [content] at offset 68. */
    private fun header(
        schema: Int,
        content: Int,
    ): ByteArray =
        ByteArray(SQLITE_HEADER_SIZE).also {
            "SQLite format 3\u0000".encodeToByteArray().copyInto(it)
            for (i in 0 until 4) {
                it[60 + i] = (schema shr (24 - 8 * i)).toByte()
                it[68 + i] = (content shr (24 - 8 * i)).toByte()
            }
        }

    @Test
    fun readsTheSchemaAndContentVersionsFromTheHeader() {
        assertThat(CatalogStamp.parse(header(3, -0x21524111)))
            .isEqualTo(CatalogStamp(3, -0x21524111))
    }

    @Test
    fun aFileThatIsNotSqliteHasNoStamp() {
        assertThat(CatalogStamp.parse(null)).isNull()
        assertThat(CatalogStamp.parse(ByteArray(10))).isNull()
        assertThat(CatalogStamp.parse(ByteArray(SQLITE_HEADER_SIZE))).isNull()
    }

    @Test
    fun refreshesWhenThereIsNoCopyOrEitherVersionDiffers() {
        val bundled = CatalogStamp(3, 100)
        assertThat(CatalogStamp.needsRefresh(null, bundled)).isTrue()
        assertThat(CatalogStamp.needsRefresh(CatalogStamp(2, 100), bundled)).isTrue()
        assertThat(CatalogStamp.needsRefresh(CatalogStamp(3, 99), bundled)).isTrue()
        assertThat(CatalogStamp.needsRefresh(CatalogStamp(3, 100), bundled)).isFalse()
    }
}
