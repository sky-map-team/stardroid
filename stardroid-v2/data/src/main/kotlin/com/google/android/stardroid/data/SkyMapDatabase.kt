/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The catalog store (catalog-and-schema.md). Read-mostly and replaceable: user state never
 * lives here, so the bundled pack can be swapped wholesale on app update (D24/G11 recovery is
 * "delete and re-copy from the bundled asset"). Schema JSON is exported to `data/schemas/` —
 * the 4c build-time generator must match it, identity hash included, for `createFromAsset`.
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
    version = 1,
    exportSchema = true,
)
abstract class SkyMapDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun packDao(): PackDao
}
