/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities for the catalog schema in docs/design/catalog-and-schema.md.
 *
 * No Room `@ForeignKey`s are declared, deliberately: the schema allows references *across*
 * packs (an `object_link` from one pack's object to another's, a `parent_object_id` into a
 * not-yet-installed pack), which must dangle gracefully rather than cascade-delete or abort a
 * pack swap. Referential cleanup within a pack is the transactional pack writer's job
 * ([PackDao]); read queries drop dangling references via their JOINs.
 */

/** Provenance of a set of catalog rows; the shipped catalog is pack `"core"`. */
@Entity(tableName = "pack")
data class PackEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val license: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    /** Epoch millis. */
    @ColumnInfo(name = "installed_at") val installedAt: Long,
    val builtin: Boolean,
)

/**
 * One node of the hierarchical type-code tree ("galaxy.spiral" → parent "galaxy"). Types are
 * data, not an enum: packs may introduce subtypes the shipped app has never seen; they are
 * shared vocabulary across packs and survive pack removal.
 */
@Entity(tableName = "object_type")
data class ObjectTypeEntity(
    @PrimaryKey val code: String,
    @ColumnInfo(name = "parent_code") val parentCode: String?,
)

/** Localized display name for a type code ("Spiral galaxy"); same fallback chain as names. */
@Entity(tableName = "type_name", primaryKeys = ["code", "locale"])
data class TypeNameEntity(
    val code: String,
    val locale: String,
    val name: String,
)

/**
 * One catalog entry. Searchability, having an info card, a position, and renderability are
 * independent capabilities: [layerKind] `null` means no layer renders it, [ra]/[dec] `null`
 * means no own position (search resolves to the parent's, else card-only).
 */
@Entity(
    tableName = "celestial_object",
    indices = [
        Index("layer_kind"),
        Index("parent_object_id"),
        Index("pack_id"),
    ],
)
data class CelestialObjectEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    /** `null` = not rendered by any layer. */
    @ColumnInfo(name = "layer_kind") val layerKind: String?,
    val type: String,
    /** J2000 degrees; `null` = no own position. */
    val ra: Double?,
    val dec: Double?,
    val magnitude: Double?,
    @ColumnInfo(name = "color_index") val colorIndex: Double?,
    @ColumnInfo(name = "search_fov") val searchFov: Double?,
    @ColumnInfo(name = "parent_object_id") val parentObjectId: String?,
    /** Info-card photo key; mapping to a renderer `ImageRef` is app-side. */
    @ColumnInfo(name = "image_ref") val imageRef: String?,
)

/** Non-hierarchical "see also" relation, rendered as info-card chips in [seq] order. */
@Entity(
    tableName = "object_link",
    primaryKeys = ["object_id", "linked_id"],
    indices = [Index("object_id")],
)
data class ObjectLinkEntity(
    @ColumnInfo(name = "object_id") val objectId: String,
    @ColumnInfo(name = "linked_id") val linkedId: String,
    val seq: Int,
)

/**
 * One localized name row. Locale `""` is the universal locale for designations ("M31",
 * "NGC 224") that match in every request locale. [nameNormalized] (lowercased,
 * diacritic-stripped via [com.google.android.stardroid.catalog.NameNormalizer]) exists for whole-name-prefix ranking; word-prefix
 * *matching* goes through [ObjectNameFtsEntity].
 */
@Entity(
    tableName = "object_name",
    indices = [
        Index("name_normalized"),
        Index("object_id", "locale"),
    ],
)
data class ObjectNameEntity(
    /** Rowid alias; the external-content FTS table tracks rows by it. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "object_id") val objectId: String,
    val locale: String,
    val name: String,
    @ColumnInfo(name = "name_normalized") val nameNormalized: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
)

/**
 * External-content FTS4 index over [ObjectNameEntity.name]: word-prefix matching ("gal" →
 * "Andromeda **Gal**axy") with unicode61 folding case and diacritics on both document and
 * query terms. Room keeps it in sync with `object_name` via generated triggers, so downloaded
 * packs are searchable the moment their rows land — no startup index build.
 */
@Fts4(
    contentEntity = ObjectNameEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    // 2 = strip diacritics even from characters whose base form is multi-byte; needs
    // SQLite >= 3.27, satisfied by minSdk 29 (SQLite 3.28 — D9).
    tokenizerArgs = ["remove_diacritics=2"],
)
@Entity(tableName = "object_name_fts")
data class ObjectNameFtsEntity(
    val name: String,
)

/**
 * Meteor-shower activity sidecar (layers-and-app.md): the annual window and peak rate for a
 * `celestial_object` whose `layer_kind` is `meteor_showers`. Dates are year-agnostic `"MM-DD"`
 * text ([com.google.android.stardroid.catalog.MonthDay.parse]); windows never cross a year
 * boundary. Cleanup on pack replacement keys through the owning object ([PackDao]).
 */
@Entity(tableName = "meteor_shower")
data class MeteorShowerEntity(
    @PrimaryKey @ColumnInfo(name = "object_id") val objectId: String,
    @ColumnInfo(name = "active_from") val activeFrom: String,
    val peak: String,
    @ColumnInfo(name = "active_to") val activeTo: String,
    @ColumnInfo(name = "peak_zhr") val peakZhr: Int,
)

/** Localized info-card content; display strings are pre-localized text, as in v1. */
@Entity(tableName = "info_card", primaryKeys = ["object_id", "locale"])
data class InfoCardEntity(
    @ColumnInfo(name = "object_id") val objectId: String,
    val locale: String,
    val description: String?,
    @ColumnInfo(name = "fun_fact") val funFact: String?,
    val distance: String?,
    val size: String?,
    val mass: String?,
    @ColumnInfo(name = "spectral_class") val spectralClass: String?,
    @ColumnInfo(name = "image_credit") val imageCredit: String?,
    @ColumnInfo(name = "search_subtext") val searchSubtext: String?,
)

/**
 * One drawable figure (constellation stick figure), owned by the searchable catalog object it
 * depicts. [kind] distinguishes stick figures (`"lines"`) from future boundaries; [culture]
 * supports alternative constellation cultures as data, not schema change.
 */
@Entity(
    tableName = "figure",
    indices = [
        Index("layer_kind"),
        Index("owner_object_id"),
        Index("pack_id"),
    ],
)
data class FigureEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_object_id") val ownerObjectId: String,
    @ColumnInfo(name = "layer_kind") val layerKind: String,
    val kind: String = "lines",
    val culture: String = "iau",
    @ColumnInfo(name = "pack_id") val packId: String,
)

/** One vertex of a figure polyline: stroke [stroke], position [seq] within it, J2000 degrees. */
@Entity(tableName = "figure_vertex", primaryKeys = ["figure_id", "stroke", "seq"])
data class FigureVertexEntity(
    @ColumnInfo(name = "figure_id") val figureId: String,
    val stroke: Int,
    val seq: Int,
    val ra: Double,
    val dec: Double,
)
