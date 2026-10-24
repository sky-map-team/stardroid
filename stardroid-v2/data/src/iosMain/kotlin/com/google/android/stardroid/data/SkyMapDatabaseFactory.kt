/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.room.Room
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.stringByDeletingLastPathComponent

/**
 * Builds the on-device catalog database from the bundled `skymap.db` — the iOS counterpart of
 * the Android factory of the same name. Room's prepackaged-database support (`createFromAsset`)
 * is Android-only, so the copy happens here, by the rule Room follows there: copy when there is
 * no on-device copy, or when the copy's schema version is not this build's. The catalog holds no
 * user state, so replacing it loses nothing.
 *
 * As on Android, an app update that changes catalog content but not the schema keeps the old
 * copy. Refreshing it is the core-pack refresh of data-packs.md, for both platforms at once.
 *
 * The database opens with Room's bundled SQLite, not the system one (catalog-and-schema.md).
 */
@OptIn(ExperimentalForeignApi::class)
object SkyMapDatabaseFactory {
    const val DATABASE_NAME = "skymap.db"

    /**
     * Opens the catalog at [databasePath] (in Application Support, say), first copying
     * [bundledPath] (the app bundle's `skymap.db`) there if needed. Blocking file I/O.
     */
    fun create(
        bundledPath: String,
        databasePath: String,
    ): SkyMapDatabase {
        if (schemaVersion(databasePath) != CATALOG_SCHEMA_VERSION) {
            copyBundled(bundledPath, databasePath)
        }
        return Room.databaseBuilder<SkyMapDatabase>(name = databasePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    /**
     * [create] plus the D24/G11 failure recovery, as on Android: Room opens lazily, so this probes
     * with a trivial query; if the on-device copy is unusable it is deleted and re-copied from
     * the bundle once. A failure of the *retry* is a real bug or a full disk — that one
     * propagates. Main-safe.
     */
    suspend fun createWithRecovery(
        bundledPath: String,
        databasePath: String,
    ): SkyMapDatabase =
        withContext(Dispatchers.IO) {
            val db = create(bundledPath, databasePath)
            try {
                db.packDao().packs()
                db
            } catch (e: RuntimeException) {
                log("Catalog DB failed to open; deleting and re-copying from the bundle: $e")
                try {
                    db.close()
                } catch (closeEx: RuntimeException) {
                    // A corrupt DB may fail to close too; recovery must still delete + re-copy.
                    log("Failed to close corrupt catalog DB before deletion: $closeEx")
                }
                deleteDatabase(databasePath)
                create(bundledPath, databasePath).also { it.packDao().packs() }
            }
        }

    /** The copy's `user_version`, or null if there is no copy or it is not a readable database. */
    private fun schemaVersion(databasePath: String): Int? {
        if (!NSFileManager.defaultManager.fileExistsAtPath(databasePath)) return null
        return try {
            BundledSQLiteDriver().open(databasePath, SQLITE_OPEN_READONLY).use { connection ->
                connection.prepare("PRAGMA user_version").use { statement ->
                    if (statement.step()) statement.getLong(0).toInt() else null
                }
            }
        } catch (e: SQLiteException) {
            null
        }
    }

    /**
     * Replaces whatever is at [databasePath] with a copy of [bundledPath]. The copy is written
     * beside it and moved into place, so an interrupted copy never leaves a half-written catalog
     * where the next launch would open it.
     */
    private fun copyBundled(
        bundledPath: String,
        databasePath: String,
    ) {
        val files = NSFileManager.defaultManager
        deleteDatabase(databasePath)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val directory = (databasePath as NSString).stringByDeletingLastPathComponent
        check(files.createDirectoryAtPath(directory, true, null, null)) {
            "Could not create $directory"
        }
        val partial = "$databasePath.partial"
        files.removeItemAtPath(partial, null)
        check(files.copyItemAtPath(bundledPath, partial, null)) {
            "Could not copy the bundled catalog from $bundledPath"
        }
        check(files.moveItemAtPath(partial, databasePath, null)) {
            "Could not move the catalog copy into place at $databasePath"
        }
    }

    /**
     * The message goes to NSLog as the format string, `%` escaped, with no arguments: a Kotlin
     * String passed through NSLog's C varargs for a `%@` crashes (SIGSEGV).
     */
    private fun log(message: String) {
        NSLog("[SkyMapDatabaseFactory] " + message.replace("%", "%%"))
    }

    /** Deletes the database and SQLite's files beside it. Missing files are not an error. */
    private fun deleteDatabase(databasePath: String) {
        listOf("", "-wal", "-shm", "-journal").forEach {
            NSFileManager.defaultManager.removeItemAtPath(databasePath + it, null)
        }
    }
}
