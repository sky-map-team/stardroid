/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class CatalogInstallerTest {
    @TempDir
    lateinit var dir: File

    private val target get() = File(dir, "databases/skymap.db")

    /** A fake catalog file: a SQLite header with the given versions, then [body]. */
    private fun catalog(
        schema: Int,
        content: Int,
        body: String,
    ): ByteArray {
        val header = ByteArray(SQLITE_HEADER_SIZE)
        "SQLite format 3\u0000".encodeToByteArray().copyInto(header)
        for (i in 0 until 4) {
            header[60 + i] = (schema shr (24 - 8 * i)).toByte()
            header[68 + i] = (content shr (24 - 8 * i)).toByte()
        }
        return header + body.encodeToByteArray()
    }

    private fun install(bundled: ByteArray) =
        CatalogInstaller.installIfStale(target) { ByteArrayInputStream(bundled) }

    @Test
    fun firstRunCopiesTheBundledCatalog() {
        val bundled = catalog(3, 1, "new")
        assertThat(install(bundled)).isTrue()
        assertThat(target.readBytes()).isEqualTo(bundled)
        assertThat(File(target.path + ".partial").exists()).isFalse()
    }

    @Test
    fun aCopyWithTheSameStampIsKept() {
        install(catalog(3, 1, "old"))
        target.appendText("local")
        assertThat(install(catalog(3, 1, "old"))).isFalse()
        assertThat(target.readBytes().decodeToString().endsWith("local")).isTrue()
    }

    @Test
    fun anUpdateWithNewContentButTheSameSchemaReplacesTheCopy() {
        install(catalog(3, 1, "old"))
        val updated = catalog(3, 2, "new")
        assertThat(install(updated)).isTrue()
        assertThat(target.readBytes()).isEqualTo(updated)
    }

    @Test
    fun aNewSchemaReplacesTheCopyAndItsSideFiles() {
        install(catalog(2, 1, "old"))
        val wal = File(target.path + "-wal").apply { writeText("stale") }
        assertThat(install(catalog(3, 1, "new"))).isTrue()
        assertThat(wal.exists()).isFalse()
    }

    @Test
    fun aCopyThatIsNotADatabaseIsReplaced() {
        target.parentFile!!.mkdirs()
        target.writeText("not a database")
        assertThat(install(catalog(3, 1, "new"))).isTrue()
    }

    @Test
    fun anInterruptedCopyLeavesThePreviousCatalogUntouched() {
        val old = catalog(3, 1, "old")
        install(old)
        val bundled = catalog(3, 2, "new")
        var opens = 0
        val failing = {
            // The first open is the header probe; the second is the copy, which dies partway.
            val dies = ++opens == 2
            object : InputStream() {
                private val inner = ByteArrayInputStream(bundled)
                private var reads = 0

                override fun read(): Int = inner.read()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    if (dies && reads++ == 1) throw IOException("disk full")
                    return inner.read(b, off, minOf(len, 60))
                }
            }
        }
        var failed = false
        try {
            CatalogInstaller.installIfStale(target, failing)
        } catch (e: IOException) {
            failed = true
        }
        assertThat(failed).isTrue()
        assertThat(target.readBytes()).isEqualTo(old)
        assertThat(File(target.path + ".partial").exists()).isFalse()
    }
}
