/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * The catalog's Room schema version. iOS checks an on-device copy against it, since Room's
 * prepackaged-database support (which does that check on Android) is Android-only.
 */
internal const val CATALOG_SCHEMA_VERSION = 2

/**
 * The catalog store (catalog-and-schema.md). Read-mostly and replaceable: user state never
 * lives here, so the bundled pack can be swapped wholesale on app update (D24/G11 recovery is
 * "delete and re-copy from the bundled asset"). Schema JSON is exported to `data/schemas/` —
 * the 4c build-time generator must match it, identity hash included, for `createFromAsset`.
 *
 * Declared in common code so iOS opens the same bundled catalog (phase 1 of the iOS port): Room
 * generates each target's implementation, and [SkyMapDatabaseConstructor] is how a non-Android
 * target finds it without reflection.
 */
@Database(
    entities = [
        PackEntity::class,
        ObjectTypeEntity::class,
        TypeNameEntity::class,
        CelestialObjectEntity::class,
        ObjectLinkEntity::class,
        ObjectNameEntity::class,
        ObjectNameFtsEntity::class,
        MeteorShowerEntity::class,
        InfoCardEntity::class,
        FigureEntity::class,
        FigureVertexEntity::class,
    ],
    version = CATALOG_SCHEMA_VERSION,
    exportSchema = true,
)
@ConstructedBy(SkyMapDatabaseConstructor::class)
abstract class SkyMapDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun packDao(): PackDao
}

/** Room generates the `actual` for every target; nothing is written by hand. */
@Suppress("KotlinNoActualForExpect")
expect object SkyMapDatabaseConstructor : RoomDatabaseConstructor<SkyMapDatabase> {
    override fun initialize(): SkyMapDatabase
}
