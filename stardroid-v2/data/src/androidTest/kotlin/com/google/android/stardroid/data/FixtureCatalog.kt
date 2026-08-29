/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

/**
 * The miniature fixture pack of catalog-and-schema.md's test plan: just enough objects to
 * exercise locale fallback, search ranking, position resolution, figures, and pack
 * replacement. `name_normalized` is left blank — [PackDao.applyPack] computes it, which is
 * itself part of what these fixtures test.
 */
object FixtureCatalog {
    const val CORE = "core"
    const val EXTRA = "extra"

    val SIRIUS_POS = 101.287 to -16.716
    val M31_POS = 10.685 to 41.269
    val JUPITER_POS = 200.0 to -10.0
    val VEGA_POS = 279.235 to 38.784
    val PERSEIDS_POS = 48.0 to 58.0

    fun corePack(version: Int = 1) =
        PackEntity(
            id = CORE,
            version = version,
            license = "CC0",
            sourceUrl = null,
            installedAt = 0L,
            builtin = true,
        )

    fun extraPack(version: Int = 1) =
        PackEntity(
            id = EXTRA,
            version = version,
            license = "CC0",
            sourceUrl = "https://example.com/extra",
            installedAt = 0L,
            builtin = false,
        )

    fun coreContents() =
        PackContents(
            types =
                listOf(
                    ObjectTypeEntity("star", null),
                    ObjectTypeEntity("galaxy", null),
                    ObjectTypeEntity("galaxy.spiral", "galaxy"),
                    ObjectTypeEntity("constellation", null),
                    ObjectTypeEntity("planet", null),
                    ObjectTypeEntity("moon", null),
                    ObjectTypeEntity("concept", null),
                    ObjectTypeEntity("meteor_shower", null),
                ),
            typeNames =
                listOf(
                    TypeNameEntity("galaxy.spiral", "en", "Spiral galaxy"),
                ),
            objects =
                listOf(
                    obj(
                        id = "star/sirius",
                        layerKind = "stars",
                        type = "star",
                        pos = SIRIUS_POS,
                        magnitude = -1.46,
                        colorIndex = 0.0,
                        imageRef = "sirius",
                    ),
                    obj("star/rigel", "stars", "star", 78.634 to -8.202, magnitude = 0.13),
                    obj("star/little-sirius", "stars", "star", 90.0 to 10.0, magnitude = 5.0),
                    obj("star/altair", "stars", "star", 297.696 to 8.868, magnitude = 0.76),
                    obj("star/alderamin", "stars", "star", 319.645 to 62.585, magnitude = 2.51),
                    obj(
                        id = "dso/m31",
                        layerKind = "deep_sky",
                        type = "galaxy.spiral",
                        pos = M31_POS,
                        magnitude = 3.44,
                        searchFov = 5.0,
                        imageRef = "m31",
                    ),
                    obj("constellation/orion", "constellations", "constellation", 83.0 to 5.0),
                    obj(
                        id = "planet/jupiter",
                        layerKind = null,
                        type = "planet",
                        pos = JUPITER_POS,
                        searchFov = 2.0,
                        imageRef = "jupiter",
                    ),
                    // No own position: search and cards resolve to Jupiter's position.
                    obj("moon/io", null, "moon", null, parent = "planet/jupiter"),
                    // No position, no parent: a card-only entry.
                    obj("concept/celestial-equator", null, "concept", null),
                    obj(
                        id = "shower/perseids",
                        layerKind = "meteor_showers",
                        type = "meteor_shower",
                        pos = PERSEIDS_POS,
                        searchFov = 45.0,
                    ),
                ),
            showers =
                listOf(
                    MeteorShowerEntity(
                        objectId = "shower/perseids",
                        activeFrom = "07-17",
                        peak = "08-13",
                        activeTo = "08-24",
                        peakZhr = 100,
                    ),
                ),
            names =
                listOf(
                    name("star/sirius", "en", "Sirius"),
                    // Non-primary name in the same locale: display must still prefer "Sirius".
                    name("star/sirius", "en", "Dog Star", isPrimary = false),
                    name("star/sirius", "", "HD 48915", isPrimary = false),
                    name("star/rigel", "en", "Rigel"),
                    name("star/little-sirius", "en", "Little Sirius"),
                    name("star/altair", "en", "Altair"),
                    name("star/alderamin", "en", "Alderamin"),
                    name("dso/m31", "en", "Andromeda Galaxy"),
                    name("dso/m31", "es", "Galaxia de Andrómeda"),
                    name("dso/m31", "", "M31", isPrimary = false),
                    name("dso/m31", "", "NGC 224", isPrimary = false),
                    name("constellation/orion", "en", "Orion"),
                    name("constellation/orion", "es", "Orión"),
                    name("planet/jupiter", "en", "Jupiter"),
                    name("shower/perseids", "en", "Perseids"),
                    name("moon/io", "en", "Io"),
                    name("concept/celestial-equator", "en", "Celestial Equator"),
                ),
            links =
                listOf(
                    ObjectLinkEntity("planet/jupiter", "moon/io", seq = 0),
                    // Cross-pack link into the 'extra' pack: alive only while it is installed.
                    ObjectLinkEntity("planet/jupiter", "star/vega", seq = 1),
                    // Dangling link (target never installed): must be dropped silently.
                    ObjectLinkEntity("planet/jupiter", "moon/europa", seq = 2),
                ),
            infoCards =
                listOf(
                    InfoCardEntity(
                        objectId = "dso/m31",
                        locale = "en",
                        description = "The nearest large spiral galaxy.",
                        funFact = "It will merge with the Milky Way.",
                        distance = "2.5 million light years",
                        size = null,
                        mass = null,
                        spectralClass = null,
                        imageCredit = "NASA",
                        searchSubtext = "Galaxy in Andromeda",
                    ),
                    // Deliberately sparse: locale fallback is whole-row, so an es request gets
                    // this card's null description, not the en card's text.
                    InfoCardEntity(
                        objectId = "dso/m31",
                        locale = "es",
                        description = null,
                        funFact = null,
                        distance = null,
                        size = null,
                        mass = null,
                        spectralClass = null,
                        imageCredit = null,
                        searchSubtext = "Galaxia en Andrómeda",
                    ),
                    InfoCardEntity(
                        objectId = "moon/io",
                        locale = "en",
                        description = "The most volcanically active body in the solar system.",
                        funFact = null,
                        distance = null,
                        size = null,
                        mass = null,
                        spectralClass = null,
                        imageCredit = null,
                        searchSubtext = "Orbits Jupiter",
                    ),
                ),
            figures =
                listOf(
                    FigureEntity(
                        id = "figure/orion-iau",
                        ownerObjectId = "constellation/orion",
                        layerKind = "constellations",
                        kind = "lines",
                        culture = "iau",
                        packId = CORE,
                    ),
                    FigureEntity(
                        id = "figure/orion-alt",
                        ownerObjectId = "constellation/orion",
                        layerKind = "constellations",
                        kind = "lines",
                        culture = "other",
                        packId = CORE,
                    ),
                ),
            figureVertices =
                listOf(
                    vertex("figure/orion-iau", stroke = 0, seq = 0, ra = 83.0, dec = 5.0),
                    vertex("figure/orion-iau", stroke = 0, seq = 1, ra = 84.0, dec = 6.0),
                    vertex("figure/orion-iau", stroke = 0, seq = 2, ra = 85.0, dec = 7.0),
                    vertex("figure/orion-iau", stroke = 1, seq = 0, ra = 80.0, dec = 0.0),
                    vertex("figure/orion-iau", stroke = 1, seq = 1, ra = 81.0, dec = 1.0),
                    vertex("figure/orion-alt", stroke = 0, seq = 0, ra = 70.0, dec = 2.0),
                    vertex("figure/orion-alt", stroke = 0, seq = 1, ra = 71.0, dec = 3.0),
                ),
        )

    fun extraContents() =
        PackContents(
            objects =
                listOf(
                    obj("star/vega", "stars", "star", VEGA_POS, magnitude = 0.03, packId = EXTRA),
                ),
            names =
                listOf(
                    name("star/vega", "en", "Vega"),
                ),
        )

    private fun obj(
        id: String,
        layerKind: String?,
        type: String,
        pos: Pair<Double, Double>?,
        magnitude: Double? = null,
        colorIndex: Double? = null,
        searchFov: Double? = null,
        parent: String? = null,
        imageRef: String? = null,
        packId: String = CORE,
    ) = CelestialObjectEntity(
        id = id,
        packId = packId,
        layerKind = layerKind,
        type = type,
        ra = pos?.first,
        dec = pos?.second,
        magnitude = magnitude,
        colorIndex = colorIndex,
        searchFov = searchFov,
        parentObjectId = parent,
        imageRef = imageRef,
    )

    private fun name(
        objectId: String,
        locale: String,
        name: String,
        isPrimary: Boolean = true,
    ) = ObjectNameEntity(
        objectId = objectId,
        locale = locale,
        name = name,
        nameNormalized = "",
        isPrimary = isPrimary,
    )

    private fun vertex(
        figureId: String,
        stroke: Int,
        seq: Int,
        ra: Double,
        dec: Double,
    ) = FigureVertexEntity(figureId = figureId, stroke = stroke, seq = seq, ra = ra, dec = dec)
}
