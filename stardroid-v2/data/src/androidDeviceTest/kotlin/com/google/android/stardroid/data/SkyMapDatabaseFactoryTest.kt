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
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An in-place upgrade on a real device: an older on-device copy against the APK's asset. */
class SkyMapDatabaseFactoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun startAndEndWithoutACopy() {
        context.deleteDatabase(SkyMapDatabaseFactory.DATABASE_NAME)
    }

    private fun packIds(db: SkyMapDatabase) = runBlocking { db.packDao().packs().map { it.id } }

    /** Writes straight into the on-device copy, behind Room's back. */
    private fun alterCopy(sql: String) {
        val path = context.getDatabasePath(SkyMapDatabaseFactory.DATABASE_NAME).path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL(sql)
        }
    }

    private fun emptiedCopyWith(stampSql: String?) {
        val first = SkyMapDatabaseFactory.create(context)
        runBlocking { first.packDao().removePack("core") }
        first.close()
        stampSql?.let(::alterCopy)
    }

    @Test
    fun anExistingCopyIsKept() {
        emptiedCopyWith(null)
        val reopened = SkyMapDatabaseFactory.create(context)
        assertTrue(packIds(reopened).isEmpty())
        reopened.close()
    }

    @Test
    fun aCopyWithOlderContentButTheSameSchemaIsReplaced() {
        // The empty-catalog failure mode: before content versions this copy was kept.
        emptiedCopyWith("PRAGMA application_id = 12345")
        val reopened = SkyMapDatabaseFactory.create(context)
        assertEquals(listOf("core"), packIds(reopened))
        reopened.close()
    }

    @Test
    fun aCopyFromAnotherSchemaVersionIsReplacedNotEmptied() {
        emptiedCopyWith("PRAGMA user_version = 1")
        val reopened = SkyMapDatabaseFactory.create(context)
        assertEquals(listOf("core"), packIds(reopened))
        reopened.close()
    }

    @Test
    fun aCorruptCopyIsRecovered() =
        runBlocking {
            context.getDatabasePath(SkyMapDatabaseFactory.DATABASE_NAME)
                .apply { parentFile!!.mkdirs() }
                .writeText("not a database")
            val db = SkyMapDatabaseFactory.createWithRecovery(context)
            assertEquals(listOf("core"), packIds(db))
            db.close()
        }
}
