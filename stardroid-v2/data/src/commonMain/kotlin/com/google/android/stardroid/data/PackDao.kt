/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.google.android.stardroid.catalog.NameNormalizer

/** Everything one pack contributes; the unit of transactional catalog change. */
data class PackContents(
    val objects: List<CelestialObjectEntity> = emptyList(),
    val showers: List<MeteorShowerEntity> = emptyList(),
    val names: List<ObjectNameEntity> = emptyList(),
    val links: List<ObjectLinkEntity> = emptyList(),
    val infoCards: List<InfoCardEntity> = emptyList(),
    val figures: List<FigureEntity> = emptyList(),
    val figureVertices: List<FigureVertexEntity> = emptyList(),
    val types: List<ObjectTypeEntity> = emptyList(),
    val typeNames: List<TypeNameEntity> = emptyList(),
)

/**
 * Transactional pack application and removal (catalog-and-schema.md): a failed or partial
 * apply leaves prior catalog state intact, and replacing pack `X` never touches pack `Y`'s
 * rows. Incoming `object_link`/`parent_object_id` references *from other packs* to a removed
 * pack's objects are left in place — read queries drop them via JOINs, and they come back to
 * life if the pack is reinstalled.
 */
@Dao
abstract class PackDao {
    /**
     * Atomically replaces [pack]'s rows with [contents]. `name_normalized` is (re)computed here
     * so the normalization invariant cannot drift from the inserted data. Type rows are shared
     * vocabulary: upserted, never removed with a pack.
     */
    @Transaction
    open suspend fun applyPack(
        pack: PackEntity,
        contents: PackContents,
    ) {
        deletePackRows(pack.id)
        insertPack(pack)
        insertTypes(contents.types)
        insertTypeNames(contents.typeNames)
        insertObjects(contents.objects)
        insertShowers(contents.showers)
        insertNames(
            contents.names.map { it.copy(nameNormalized = NameNormalizer.normalize(it.name)) },
        )
        insertLinks(contents.links)
        insertInfoCards(contents.infoCards)
        insertFigures(contents.figures)
        insertFigureVertices(contents.figureVertices)
    }

    @Transaction
    open suspend fun removePack(packId: String) {
        deletePackRows(packId)
        deletePack(packId)
    }

    @Query("SELECT * FROM pack ORDER BY id")
    abstract suspend fun packs(): List<PackEntity>

    private suspend fun deletePackRows(packId: String) {
        // Dependents first, keyed through the pack's own objects/figures. Deleting object_name
        // rows keeps the FTS index in sync via Room's external-content triggers.
        deleteNamesOfPack(packId)
        deleteShowersOfPack(packId)
        deleteInfoCardsOfPack(packId)
        deleteOutgoingLinksOfPack(packId)
        deleteFigureVerticesOfPack(packId)
        deleteFiguresOfPack(packId)
        deleteObjectsOfPack(packId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPack(pack: PackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTypes(types: List<ObjectTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTypeNames(typeNames: List<TypeNameEntity>)

    @Insert
    protected abstract suspend fun insertObjects(objects: List<CelestialObjectEntity>)

    @Insert
    protected abstract suspend fun insertShowers(showers: List<MeteorShowerEntity>)

    @Insert
    protected abstract suspend fun insertNames(names: List<ObjectNameEntity>)

    @Insert
    protected abstract suspend fun insertLinks(links: List<ObjectLinkEntity>)

    @Insert
    protected abstract suspend fun insertInfoCards(cards: List<InfoCardEntity>)

    @Insert
    protected abstract suspend fun insertFigures(figures: List<FigureEntity>)

    @Insert
    protected abstract suspend fun insertFigureVertices(vertices: List<FigureVertexEntity>)

    @Query(
        """
        DELETE FROM object_name
        WHERE object_id IN (SELECT id FROM celestial_object WHERE pack_id = :packId)
        """,
    )
    protected abstract suspend fun deleteNamesOfPack(packId: String)

    @Query(
        """
        DELETE FROM meteor_shower
        WHERE object_id IN (SELECT id FROM celestial_object WHERE pack_id = :packId)
        """,
    )
    protected abstract suspend fun deleteShowersOfPack(packId: String)

    @Query(
        """
        DELETE FROM info_card
        WHERE object_id IN (SELECT id FROM celestial_object WHERE pack_id = :packId)
        """,
    )
    protected abstract suspend fun deleteInfoCardsOfPack(packId: String)

    @Query(
        """
        DELETE FROM object_link
        WHERE object_id IN (SELECT id FROM celestial_object WHERE pack_id = :packId)
        """,
    )
    protected abstract suspend fun deleteOutgoingLinksOfPack(packId: String)

    @Query(
        """
        DELETE FROM figure_vertex
        WHERE figure_id IN (SELECT id FROM figure WHERE pack_id = :packId)
        """,
    )
    protected abstract suspend fun deleteFigureVerticesOfPack(packId: String)

    @Query("DELETE FROM figure WHERE pack_id = :packId")
    protected abstract suspend fun deleteFiguresOfPack(packId: String)

    @Query("DELETE FROM celestial_object WHERE pack_id = :packId")
    protected abstract suspend fun deleteObjectsOfPack(packId: String)

    @Query("DELETE FROM pack WHERE id = :packId")
    protected abstract suspend fun deletePack(packId: String)
}
