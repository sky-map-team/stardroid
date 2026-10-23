/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.math.RaDec

/**
 * The `satellite/<noradId>` id namespace, mirroring `SolarSystemIds`' `planet/<body>`.
 *
 * A satellite's position and name come from its live element set, which arrives from the
 * network and may not exist next year, so its card is synthesized here. Satellites we ship
 * editorial content for (ISS, Tiangong) also have a catalog row carrying the prose, facts and
 * image; [withCatalogContent] overlays it. Any other satellite gets the generic description and
 * no image.
 *
 * Catalog rows have no names, so they never appear in catalog search; satellite search is
 * answered by the live list.
 */
object SatelliteIds {
    private const val SATELLITE_ID_PREFIX = "satellite/"

    /** Matches the `satellite/` id namespace; not one of the bundled catalog's type paths. */
    private val SATELLITE_TYPE = TypeCode("satellite")

    fun idFor(noradId: Int): CelestialObjectId = CelestialObjectId(SATELLITE_ID_PREFIX + noradId)

    fun noradIdFor(id: CelestialObjectId): Int? =
        id.value.removePrefix(SATELLITE_ID_PREFIX).toIntOrNull()?.takeIf {
            id.value.startsWith(SATELLITE_ID_PREFIX)
        }

    /** [live] with the static content of [catalog], its row in the bundled catalog, if any. */
    fun withCatalogContent(
        live: ObjectInfo,
        catalog: ObjectInfo?,
    ): ObjectInfo =
        if (catalog == null) {
            live
        } else {
            live.copy(
                description = catalog.description ?: live.description,
                funFact = catalog.funFact ?: live.funFact,
                distance = catalog.distance ?: live.distance,
                size = catalog.size ?: live.size,
                mass = catalog.mass ?: live.mass,
                imageRef = catalog.imageRef ?: live.imageRef,
                imageCredit = catalog.imageCredit ?: live.imageCredit,
            )
        }

    /**
     * A card for [tle], positioned at [position].
     *
     * [description] and [funFact] come from the caller so the strings stay in `res/values`, where
     * the translation pipeline can reach them.
     */
    fun cardFor(
        tle: Tle,
        position: RaDec?,
        description: String?,
        funFact: String? = null,
    ): ObjectInfo =
        ObjectInfo(
            id = idFor(tle.noradId),
            name = tle.name.ifEmpty { "#${tle.noradId}" },
            type = SATELLITE_TYPE,
            // No LayerKind: those name catalog-backed layers, and this object is not in the
            // catalog at all.
            layerKind = null,
            position = position,
            parent = null,
            magnitude = null,
            description = description,
            funFact = funFact,
            distance = null,
            size = null,
            mass = null,
            spectralClass = null,
            imageRef = null,
            imageCredit = null,
            searchSubtext = null,
            links = emptyList(),
        )
}
