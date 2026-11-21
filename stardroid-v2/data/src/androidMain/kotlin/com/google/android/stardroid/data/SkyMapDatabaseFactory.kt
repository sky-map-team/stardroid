/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the on-device catalog database from the bundled `skymap.db` asset (produced by
 * `:data:generateCatalogDb`). [CatalogInstaller] copies it per [CatalogStamp.needsRefresh], so a
 * content-only change in `source-data/` reaches an in-place upgrade without a
 * [CATALOG_SCHEMA_VERSION] bump. Room then just opens the file: there is deliberately no
 * `createFromAsset` and no destructive migration fallback, because a mismatch Room finds anyway
 * must fail (and be recovered by [createWithRecovery]) rather than silently leave an empty
 * catalog.
 */
object SkyMapDatabaseFactory {
    const val DATABASE_NAME = "skymap.db"
    private const val TAG = "SkyMapDatabaseFactory"

    fun create(context: Context): SkyMapDatabase {
        CatalogInstaller.installIfStale(context.getDatabasePath(DATABASE_NAME)) {
            context.assets.open(DATABASE_NAME)
        }
        return Room.databaseBuilder(context, SkyMapDatabase::class.java, DATABASE_NAME).build()
    }

    /**
     * [create] plus the D24/G11 failure recovery: Room opens lazily, so this probes with a
     * trivial query; if the on-device copy is unusable (disk damage, a Room identity mismatch), it
     * is deleted and re-copied from the bundled asset once. A failure of the *retry* is a real
     * bug or a full disk — that one propagates.
     *
     * Main-safe: blocking I/O runs on [Dispatchers.IO]; the probes dispatch to Room's executor.
     */
    suspend fun createWithRecovery(context: Context): SkyMapDatabase =
        withContext(Dispatchers.IO) {
            val db = create(context)
            try {
                db.packDao().packs()
                db
            } catch (e: RuntimeException) {
                Log.w(TAG, "Catalog DB failed to open; deleting and re-copying from asset", e)
                try {
                    db.close()
                } catch (closeEx: RuntimeException) {
                    // A corrupt DB may fail to close too; recovery must still delete + re-copy.
                    Log.w(TAG, "Failed to close corrupt catalog DB before deletion", closeEx)
                }
                context.deleteDatabase(DATABASE_NAME)
                create(context).also { it.packDao().packs() }
            }
        }
}
