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
 * **Unlike every other tappable object, a satellite has no catalog row.** Stars, deep-sky objects
 * and even the position-less planets all ship an info card in the bundled database; a satellite
 * cannot, because it arrives from the network and may not exist next year. So its card is
 * synthesized here from the element set plus a short static description.
 *
 * The consequence is deliberate and worth knowing: satellite cards have no imagery, no "see also"
 * links and no localized prose beyond the two strings below. Giving them the full treatment would
 * mean adding rows to `source-data/` and regenerating the bundled DB — reasonable, and a bigger
 * change than this one; see D101.
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
