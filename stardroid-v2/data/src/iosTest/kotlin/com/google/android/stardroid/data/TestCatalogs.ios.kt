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

private val copyDirectory = NSTemporaryDirectory() + "skymap-catalog-test"

/**
 * Copies the generated catalog (its path comes from the Gradle test task) and opens the copy,
 * as the iOS app will open the copy it makes from its bundle. The build
 * output itself is never opened: SQLite would write its journal files beside it.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun openBundledCatalog(): SkyMapDatabase {
    deleteBundledCatalog()
    val bundled =
        checkNotNull(environmentVariable("SKYMAP_CATALOG_DB")) {
            "SKYMAP_CATALOG_DB is unset — run this through the Gradle test task."
        }
    val files = NSFileManager.defaultManager
    check(files.createDirectoryAtPath(copyDirectory, true, null, null))
    val copy = "$copyDirectory/skymap.db"
    check(files.copyItemAtPath(bundled, copy, null)) { "Could not copy $bundled" }
    return Room.databaseBuilder<SkyMapDatabase>(name = copy)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteBundledCatalog() {
    NSFileManager.defaultManager.removeItemAtPath(copyDirectory, null)
}
