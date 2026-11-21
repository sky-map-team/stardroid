/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Puts the bundled catalog where the app opens it. Plain files and streams, so the rule is
 * testable on the JVM; [SkyMapDatabaseFactory] supplies the APK asset.
 */
internal object CatalogInstaller {
    /**
     * Replaces [target] with the bundled catalog unless its stamp ([CatalogStamp]) already
     * matches. The copy is written beside [target] and moved into place, so an interrupted copy
     * leaves the previous file (or none), never a half-written catalog that would be opened.
     * Returns whether it copied.
     */
    fun installIfStale(
        target: File,
        openBundled: () -> InputStream,
    ): Boolean {
        val bundled =
            checkNotNull(CatalogStamp.parse(openBundled().use(::readHeader))) {
                "The bundled catalog is not a SQLite database"
            }
        val onDevice = CatalogStamp.parse(target.takeIf { it.exists() }?.let(::readHeaderOrNull))
        if (!CatalogStamp.needsRefresh(onDevice, bundled)) return false

        target.parentFile?.mkdirs()
        val partial = File(target.path + ".partial")
        try {
            openBundled().use {
                Files.copy(
                    it,
                    partial.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            // The old copy's side files belong to it, not to the replacement.
            listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.delete()
        }
        return true
    }

    private fun readHeader(input: InputStream): ByteArray =
        ByteArray(SQLITE_HEADER_SIZE).also { DataInputStream(input).readFully(it) }

    private fun readHeaderOrNull(file: File): ByteArray? =
        try {
            FileInputStream(file).use(::readHeader)
        } catch (e: EOFException) {
            null
        } catch (e: IOException) {
            null
        }
}
