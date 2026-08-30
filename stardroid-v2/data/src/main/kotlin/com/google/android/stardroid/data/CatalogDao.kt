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
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Read queries over the catalog. Locale-aware queries take the request's full fallback chain
 * ([com.google.android.stardroid.catalog.LocaleSpec.fallbackChain]) and return one row per
 * *candidate* name/card; picking the best candidate per object is Kotlin-side in
 * [RoomCatalogRepository] — at catalog scale (a few thousand objects) that is simpler and more
 * testable than per-object argmin SQL.
 */
@Dao
interface CatalogDao {
    /**
     * Renderable objects of a layer with their candidate names in the locale chain. `Flow`
     * re-emission on any `celestial_object`/`object_name` change is Room invalidation — this is
     * how pack installs surface in running layers.
     */
    @Query(
        """
        SELECT o.id, o.type, o.ra, o.dec, o.magnitude, o.color_index AS colorIndex,
               o.search_fov AS searchFov,
               n.locale AS nameLocale, n.name, n.is_primary AS isPrimary
        FROM celestial_object o
        LEFT JOIN object_name n ON n.object_id = o.id AND n.locale IN (:locales)
        WHERE o.layer_kind = :layerKind AND o.ra IS NOT NULL AND o.dec IS NOT NULL
        """,
    )
    fun layerObjectRows(
        layerKind: String,
        locales: List<String>,
    ): Flow<List<LayerObjectRow>>

    /** Stick-figure vertices for a layer and culture, pre-ordered for stroke grouping. */
    @Query(
        """
        SELECT f.id AS figureId, f.owner_object_id AS ownerObjectId,
               v.stroke, v.seq, v.ra, v.dec
        FROM figure f
        JOIN figure_vertex v ON v.figure_id = f.id
        WHERE f.layer_kind = :layerKind AND f.culture = :culture AND f.kind = 'lines'
        ORDER BY f.id, v.stroke, v.seq
        """,
    )
    fun figureVertexRows(
        layerKind: String,
        culture: String,
    ): Flow<List<FigureVertexRow>>

    /**
     * Every meteor shower with its activity window and candidate names in the locale chain.
     * The JOIN drops sidecar rows whose object is missing or position-less (cross-pack dangling
     * is allowed at runtime; the bundled pack is generator-validated).
     */
    @Query(
        """
        SELECT o.id, o.ra, o.dec,
               s.active_from AS activeFrom, s.peak, s.active_to AS activeTo,
               s.peak_zhr AS peakZhr,
               n.locale AS nameLocale, n.name, n.is_primary AS isPrimary
        FROM meteor_shower s
        JOIN celestial_object o ON o.id = s.object_id
        LEFT JOIN object_name n ON n.object_id = o.id AND n.locale IN (:locales)
        WHERE o.ra IS NOT NULL AND o.dec IS NOT NULL
        """,
    )
    fun meteorShowerRows(locales: List<String>): Flow<List<MeteorShowerRow>>

    /**
     * Name rows matching an FTS word-prefix query, restricted to the locale chain, with the
     * owning object's search-relevant fields and its parent's position (the LEFT JOIN implements
     * "own position → parent's position → none").
     *
     * Bounded by [candidateLimit], because a one-character query becomes the prefix `a*` and
     * matches a large fraction of every name row in the catalog — all of which used to be
     * materialized, grouped and sorted in Kotlin only to be thrown away by the display limit
     * (audit-2026-08 M3). The ORDER BY is the head of `RoomCatalogRepository.searchByPrefix`'s
     * own ranking — whole-name prefix, then primary name, then brightness — so truncation drops
     * the rows that ranking would have dropped anyway. `substr` rather than `LIKE` because the
     * prefix is user text and must not be read as a wildcard pattern.
     */
    @Query(
        """
        SELECT n.object_id AS objectId, n.locale, n.name, n.name_normalized AS nameNormalized,
               n.is_primary AS isPrimary,
               o.magnitude, o.ra, o.dec, o.search_fov AS searchFov,
               p.ra AS parentRa, p.dec AS parentDec, p.search_fov AS parentSearchFov
        FROM object_name n
        JOIN celestial_object o ON o.id = n.object_id
        LEFT JOIN celestial_object p ON p.id = o.parent_object_id
        WHERE n.locale IN (:locales)
          AND n.id IN (SELECT docid FROM object_name_fts WHERE object_name_fts MATCH :ftsQuery)
        ORDER BY substr(n.name_normalized, 1, length(:normalizedPrefix)) = :normalizedPrefix DESC,
                 n.is_primary DESC,
                 o.magnitude IS NULL, o.magnitude
        LIMIT :candidateLimit
        """,
    )
    suspend fun searchNameRows(
        ftsQuery: String,
        locales: List<String>,
        normalizedPrefix: String,
        candidateLimit: Int,
    ): List<SearchNameRow>

    @Query("SELECT * FROM celestial_object WHERE id = :id")
    suspend fun objectById(id: String): CelestialObjectEntity?

    /** All candidate names in the chain for a batch of objects (search hits, info-card links). */
    @Query("SELECT * FROM object_name WHERE object_id IN (:objectIds) AND locale IN (:locales)")
    suspend fun namesFor(
        objectIds: List<String>,
        locales: List<String>,
    ): List<ObjectNameEntity>

    @Query("SELECT * FROM info_card WHERE object_id IN (:objectIds) AND locale IN (:locales)")
    suspend fun infoCardsFor(
        objectIds: List<String>,
        locales: List<String>,
    ): List<InfoCardEntity>

    /**
     * "See also" targets of an object in chip order. The inner JOIN silently drops links whose
     * target is not installed (cross-pack links are allowed to dangle — see CatalogEntities.kt).
     */
    @Query(
        """
        SELECT o.id, o.ra, o.dec, o.search_fov AS searchFov,
               p.ra AS parentRa, p.dec AS parentDec, p.search_fov AS parentSearchFov
        FROM object_link l
        JOIN celestial_object o ON o.id = l.linked_id
        LEFT JOIN celestial_object p ON p.id = o.parent_object_id
        WHERE l.object_id = :objectId
        ORDER BY l.seq
        """,
    )
    suspend fun linkRows(objectId: String): List<LinkRow>

    /** Ids with card content in any locale — the tap-to-identify candidate set. */
    @Query("SELECT DISTINCT object_id FROM info_card")
    suspend fun infoCardObjectIds(): List<String>

    /** Objects with a photo and their candidate names in the chain — the gallery grid. */
    @Query(
        """
        SELECT o.id, o.image_ref AS imageRef,
               n.locale AS nameLocale, n.name, n.is_primary AS isPrimary
        FROM celestial_object o
        LEFT JOIN object_name n ON n.object_id = o.id AND n.locale IN (:locales)
        WHERE o.image_ref IS NOT NULL
        """,
    )
    suspend fun galleryRows(locales: List<String>): List<GalleryRow>
}

/**
 * A named row where [name] may be `null` for locales in the chain without a candidate —
 * [RoomCatalogRepository]'s locale-fallback name-picking idiom is shared across the row types
 * that implement this.
 */
internal interface NameCandidate {
    val name: String?
    val nameLocale: String?
    val isPrimary: Boolean?
}

/** One (object, candidate-name) pair for a layer; name fields `null` when no name in chain. */
data class LayerObjectRow(
    val id: String,
    val type: String,
    val ra: Double,
    val dec: Double,
    val magnitude: Double?,
    val colorIndex: Double?,
    val searchFov: Double?,
    override val nameLocale: String?,
    override val name: String?,
    override val isPrimary: Boolean?,
) : NameCandidate

data class FigureVertexRow(
    val figureId: String,
    val ownerObjectId: String,
    val stroke: Int,
    val seq: Int,
    val ra: Double,
    val dec: Double,
)

/** One (shower, candidate-name) pair; name fields `null` when no name in chain. */
data class MeteorShowerRow(
    val id: String,
    val ra: Double,
    val dec: Double,
    val activeFrom: String,
    val peak: String,
    val activeTo: String,
    val peakZhr: Int,
    override val nameLocale: String?,
    override val name: String?,
    override val isPrimary: Boolean?,
) : NameCandidate

/** One matched name with its object's search fields; parent columns feed position fallback. */
data class SearchNameRow(
    val objectId: String,
    val locale: String,
    val name: String,
    val nameNormalized: String,
    val isPrimary: Boolean,
    val magnitude: Double?,
    val ra: Double?,
    val dec: Double?,
    val searchFov: Double?,
    val parentRa: Double?,
    val parentDec: Double?,
    val parentSearchFov: Double?,
)

/** One (object, candidate-name) pair for the gallery; name fields `null` when no name. */
data class GalleryRow(
    val id: String,
    val imageRef: String,
    override val nameLocale: String?,
    override val name: String?,
    override val isPrimary: Boolean?,
) : NameCandidate

/** One link target's position fields, in chip order. */
data class LinkRow(
    val id: String,
    val ra: Double?,
    val dec: Double?,
    val searchFov: Double?,
    val parentRa: Double?,
    val parentDec: Double?,
    val parentSearchFov: Double?,
)
