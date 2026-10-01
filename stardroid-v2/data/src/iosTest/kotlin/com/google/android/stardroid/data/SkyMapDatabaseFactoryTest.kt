/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.android.stardroid.testing.assertThat
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails

/**
 * The iOS factory's copy rule and D24/G11 recovery: the parts Room does for Android with
 * `createFromAsset`, done by hand on iOS.
 */
@OptIn(ExperimentalForeignApi::class)
class SkyMapDatabaseFactoryTest {
    @BeforeTest
    fun startWithoutACopy() {
        deleteBundledCatalog()
        NSFileManager.defaultManager.createDirectoryAtPath(testCatalogDirectory, true, null, null)
    }

    @AfterTest
    fun cleanup() {
        deleteBundledCatalog()
    }

    private fun create() = SkyMapDatabaseFactory.create(bundledCatalogPath, testCatalogPath)

    private suspend fun createWithRecovery() =
        SkyMapDatabaseFactory.createWithRecovery(bundledCatalogPath, testCatalogPath)

    private suspend fun SkyMapDatabase.packIds() = packDao().packs().map { it.id }

    /** Writes [sql] straight into the on-device copy, behind Room's back. */
    private fun alterCopy(sql: String) {
        BundledSQLiteDriver().open(testCatalogPath).use { it.execSQL(sql) }
    }

    @Test
    fun firstOpenCopiesTheBundledCatalog() =
        runTest {
            val db = create()
            assertThat(db.packIds()).containsExactly("core")
            db.close()
        }

    @Test
    fun anExistingCopyIsKept() =
        runTest {
            create().apply { packDao().removePack("core") }.close()

            val reopened = create()
            assertThat(reopened.packIds()).isEmpty()
            reopened.close()
        }

    @Test
    fun aCopyFromAnotherSchemaVersionIsReplaced() =
        runTest {
            create().apply { packDao().removePack("core") }.close()
            alterCopy("PRAGMA user_version = 1")

            val reopened = create()
            assertThat(reopened.packIds()).containsExactly("core")
            reopened.close()
        }

    @Test
    fun aFileThatIsNotADatabaseIsReplaced() =
        runTest {
            @Suppress("CAST_NEVER_SUCCEEDS")
            ("not a database" as NSString)
                .writeToFile(testCatalogPath, true, NSUTF8StringEncoding, null)

            val db = createWithRecovery()
            assertThat(db.packIds()).containsExactly("core")
            db.close()
        }

    @Test
    fun aCopyRoomCannotValidateIsDeletedAndRecopied() =
        runTest {
            // Right schema version, wrong contents: the version check passes, Room's identity
            // check does not, so only the probe in createWithRecovery catches it.
            BundledSQLiteDriver().open(testCatalogPath).use {
                it.execSQL("CREATE TABLE unrelated(x)")
                it.execSQL("PRAGMA user_version = $CATALOG_SCHEMA_VERSION")
            }
            val broken = create()
            assertFails { broken.packIds() }
            broken.close()

            val db = createWithRecovery()
            assertThat(db.packIds()).containsExactly("core")
            db.close()
        }
}
