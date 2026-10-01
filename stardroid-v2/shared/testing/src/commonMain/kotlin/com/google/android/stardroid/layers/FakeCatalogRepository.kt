/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [CatalogRepository] for layer tests: hot per-kind state flows the test mutates to
 * simulate catalog change, and a call log for the locale each query was made with.
 */
class FakeCatalogRepository : CatalogRepository {
    val objectsByKind =
        LayerKind.entries.associateWith { MutableStateFlow<List<CatalogObject>>(emptyList()) }
    val showers = MutableStateFlow<List<MeteorShower>>(emptyList())
    val figuresByKind =
        LayerKind.entries.associateWith { MutableStateFlow<List<Figure>>(emptyList()) }
    val requestedLocales = mutableListOf<LocaleSpec>()
    var figuresRequests = 0
        private set

    override fun layerObjects(
        kind: LayerKind,
        locale: LocaleSpec,
    ): Flow<List<CatalogObject>> {
        requestedLocales += locale
        return objectsByKind.getValue(kind)
    }

    override fun figures(
        kind: LayerKind,
        culture: String,
    ): Flow<List<Figure>> {
        figuresRequests++
        return figuresByKind.getValue(kind)
    }

    override fun meteorShowers(locale: LocaleSpec): Flow<List<MeteorShower>> {
        requestedLocales += locale
        return showers
    }

    override suspend fun searchByPrefix(
        prefix: String,
        locale: LocaleSpec,
        limit: Int,
    ): List<SearchHit> = emptyList()

    override suspend fun objectInfo(
        id: CelestialObjectId,
        locale: LocaleSpec,
    ): ObjectInfo? = null

    override suspend fun infoCardObjectIds(): Set<CelestialObjectId> = emptySet()

    override suspend fun galleryItems(locale: LocaleSpec): List<GalleryItem> = emptyList()
}
