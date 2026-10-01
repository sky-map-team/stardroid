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
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.android.stardroid.testing.environmentVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

/** Where the iOS tests keep their on-device copies of the catalog. */
internal val testCatalogDirectory = NSTemporaryDirectory() + "skymap-catalog-test"

internal val testCatalogPath = "$testCatalogDirectory/${SkyMapDatabaseFactory.DATABASE_NAME}"

/**
 * The generated catalog, standing in for the app bundle's copy. Its path comes from the Gradle
 * test task; the tests never open it in place, since SQLite would write its journal beside it.
 */
internal val bundledCatalogPath: String
    get() =
        checkNotNull(environmentVariable("SKYMAP_CATALOG_DB")) {
            "SKYMAP_CATALOG_DB is unset — run this through the Gradle test task."
        }

/** Through the app's own factory, so the copy is made as it is in production. */
internal actual fun openBundledCatalog(): SkyMapDatabase {
    deleteBundledCatalog()
    return SkyMapDatabaseFactory.create(bundledCatalogPath, testCatalogPath)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteBundledCatalog() {
    NSFileManager.defaultManager.removeItemAtPath(testCatalogDirectory, null)
}

internal actual fun inMemoryCatalog(): SkyMapDatabase =
    Room.inMemoryDatabaseBuilder<SkyMapDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
