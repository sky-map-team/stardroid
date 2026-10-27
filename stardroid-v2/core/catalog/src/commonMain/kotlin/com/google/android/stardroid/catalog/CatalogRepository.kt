/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import com.google.android.stardroid.math.RaDec
import kotlinx.coroutines.flow.Flow

/**
 * One search result / "see also" chip: everything the search UI and arrow need.
 *
 * [position] is the *resolved* sky position under the schema's independent-capabilities rule:
 * the object's own position if it has one, else its parent's (a moon shown at its planet), else
 * `null` — a card-only hit whose experience is the info card alone (catalog-and-schema.md).
 */
data class SearchHit(
    val id: CelestialObjectId,
    /** Display name for the requested locale. */
    val name: String,
    /** Disambiguation line under the name ("Orbits Jupiter", "Galaxy in Andromeda"). */
    val subtext: String?,
    val position: RaDec?,
    /** Target FOV in degrees when navigating to this hit. */
    val searchFovDeg: Double?,
)

/**
 * The localized info-card content for one object. Display strings ([distance], [size], [mass])
 * are pre-localized text, as in v1's `object_info.json`. [links] are the "see also" chips in
 * display order — [SearchHit]s, because tapping a chip re-aims the search arrow exactly like a
 * search (catalog-and-schema.md).
 */
data class ObjectInfo(
    val id: CelestialObjectId,
    val name: String,
    /**
     * The object's other searchable names in the locale chain — every alias except the [name]
     * shown as the card title, in display order. Populates the card's "also known as" section so
     * a user who searched a designation (say "M31") isn't confused when the card is titled with a
     * different primary name (catalog-and-schema.md's primary/secondary name split).
     */
    val otherNames: List<String> = emptyList(),
    val type: TypeCode,
    /** The layer that renders this object, `null` if none does (card-only rows). */
    val layerKind: LayerKind? = null,
    /** Resolved like [SearchHit.position]; `null` for card-only objects. */
    val position: RaDec?,
    /** Hierarchical parent (Jupiter for Io) — the info card's breadcrumb; usually `null`. */
    val parent: CelestialObjectId?,
    val magnitude: Double?,
    val description: String?,
    val funFact: String?,
    val distance: String?,
    val size: String?,
    val mass: String?,
    val spectralClass: String?,
    /**
     * Info-card photo key (the schema's `image_ref`), an opaque `String` here: mapping to the
     * renderer's `ImageRef` is app-side so `:core:catalog` stays independent of `:render:api`.
     */
    val imageRef: String?,
    val imageCredit: String?,
    val searchSubtext: String?,
    /** Resolved like [SearchHit.searchFovDeg] — lets the card re-aim exactly like a search. */
    val searchFovDeg: Double? = null,
    val links: List<SearchHit> = emptyList(),
)

/**
 * One gallery thumbnail: an object with a photo, under its best locale-chain name. The grid
 * needs nothing else — tapping a tile opens the full [ObjectInfo] card by [id].
 */
data class GalleryItem(
    val id: CelestialObjectId,
    val name: String,
    /** Photo key, same namespace as [ObjectInfo.imageRef] — never `null` here by definition. */
    val imageRef: String,
)

/**
 * The pure interface over the catalog store (`:data` implements it with Room). All methods are
 * locale-aware via [LocaleSpec]'s fallback chain; downloaded packs surface through the same
 * queries the moment their rows land (catalog-and-schema.md, data-layer.md).
 */
interface CatalogRepository {
    /** Render-ready snapshot per layer; re-emits on locale or catalog change. */
    fun layerObjects(
        kind: LayerKind,
        locale: LocaleSpec,
    ): Flow<List<CatalogObject>>

    /** The figures a layer draws (constellation stick figures for the given culture). */
    fun figures(
        kind: LayerKind,
        culture: String = "iau",
    ): Flow<List<Figure>>

    /**
     * Every shower with a radiant position and an activity-window sidecar row, for the
     * meteor-shower layer; re-emits on locale or catalog change like [layerObjects].
     */
    fun meteorShowers(locale: LocaleSpec): Flow<List<MeteorShower>>

    /**
     * Word-prefix name search ("gal" matches "Andromeda Galaxy"), ranked whole-name-prefix
     * first, then word-prefix, weighted by primary-name status and magnitude, filtered by the
     * locale chain.
     */
    suspend fun searchByPrefix(
        prefix: String,
        locale: LocaleSpec,
        limit: Int,
    ): List<SearchHit>

    /**
     * The info card for [id], or `null` if the object is unknown. An object with no stored card
     * content still yields an [ObjectInfo] (name, type, resolved position, breadcrumb) with
     * `null` card fields — the search-to-card flow works for every catalog entry.
     */
    suspend fun objectInfo(
        id: CelestialObjectId,
        locale: LocaleSpec,
    ): ObjectInfo?

    /**
     * Every id with curated info-card content in any locale — the tap-to-identify candidate
     * set (v1's `ObjectInfoRegistry.supportedObjectIds`): a sky tap resolves against curated
     * objects only, never the thousands of card-less catalog rows.
     */
    suspend fun infoCardObjectIds(): Set<CelestialObjectId>

    /**
     * Every object with a photo, sorted by display name — the image-gallery grid (v1's
     * `ObjectInfoRegistry.getAllWithImages`).
     */
    suspend fun galleryItems(locale: LocaleSpec): List<GalleryItem>
}
