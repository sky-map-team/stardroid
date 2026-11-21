/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

/** How many bytes of a SQLite file [CatalogStamp.parse] needs: the database header. */
internal const val SQLITE_HEADER_SIZE = 100

/**
 * What identifies a catalog file without opening it with SQLite: the schema version and the
 * content version, both read straight from the 100-byte SQLite header.
 *
 * The content version is a hash of everything the generator wrote, stamped by `:data:generator`
 * into the header's `application_id` (offset 68). It lives there rather than in a table so that
 * it costs no Room schema change and can be read from the bundled asset without copying it out.
 * It changes whenever `source-data/` or the generator output does, with no human bump.
 */
internal data class CatalogStamp(
    val schemaVersion: Int,
    val contentVersion: Int,
) {
    companion object {
        private const val USER_VERSION_OFFSET = 60
        private const val APPLICATION_ID_OFFSET = 68
        private val MAGIC = "SQLite format 3\u0000".encodeToByteArray()

        /** The stamp in [header] (a file's first bytes), or null if it is not a SQLite file. */
        fun parse(header: ByteArray?): CatalogStamp? {
            if (header == null || header.size < SQLITE_HEADER_SIZE) return null
            if (MAGIC.indices.any { header[it] != MAGIC[it] }) return null
            return CatalogStamp(
                schemaVersion = header.bigEndianInt(USER_VERSION_OFFSET),
                contentVersion = header.bigEndianInt(APPLICATION_ID_OFFSET),
            )
        }

        /**
         * True when the on-device copy must be replaced by the bundled one: there is no readable
         * copy, or its schema or content differs from the bundled one's. Any difference counts,
         * so a downgrade restores the older bundled catalog too.
         */
        fun needsRefresh(
            onDevice: CatalogStamp?,
            bundled: CatalogStamp,
        ): Boolean = onDevice == null || onDevice != bundled
    }
}

private fun ByteArray.bigEndianInt(offset: Int): Int =
    (0 until 4).fold(0) { acc, i -> (acc shl 8) or (this[offset + i].toInt() and 0xFF) }
