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
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/**
 * Maps one [LayerKind]'s catalog snapshot to render primitives. Stars, deep-sky objects, and
 * constellations differ **only** in this policy — stellar points vs. icon points vs. figure
 * lines, plus their label styling (catalog-and-schema.md). Styling lives here, not in the DB:
 * the store carries semantic data only (D12).
 */
interface PrimitiveMapping {
    /** Whether the layer draws [Figure]s; `false` skips the figures query entirely. */
    val usesFigures: Boolean
        get() = false

    fun toScene(
        depth: Int,
        objects: List<CatalogObject>,
        figures: List<Figure>,
    ): LayerScene
}

/**
 * The one catalog-backed [SkyLayer] (layers-and-app.md): three instances — stars, deep sky,
 * constellations — each parameterized by [kind] and a [PrimitiveMapping]. Re-emits on catalog or
 * locale change; ignores the clock.
 */
class CatalogLayer(
    override val id: LayerId,
    override val depth: Int,
    private val kind: LayerKind,
    private val catalog: CatalogRepository,
    private val locale: Flow<LocaleSpec>,
    private val mapping: PrimitiveMapping,
    /** Where [PrimitiveMapping.toScene] runs — trig over thousands of objects, so not Main. */
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scenes(): Flow<LayerScene> =
        locale
            .distinctUntilChanged()
            .flatMapLatest { localeSpec ->
                val figures =
                    if (mapping.usesFigures) catalog.figures(kind) else flowOf(emptyList())
                combine(catalog.layerObjects(kind, localeSpec), figures) { objects, figs ->
                    mapping.toScene(depth, objects, figs)
                }
            }
            .flowOn(mapContext)
}
