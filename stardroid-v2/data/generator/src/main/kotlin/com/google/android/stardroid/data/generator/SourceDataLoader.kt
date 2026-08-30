/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.generator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

/**
 * Loads the checked-in `source-data/` tree (see its README.md for the file formats) into a
 * validated [CatalogData] for the bundled pack.
 */
object SourceDataLoader {
    private const val PACK_ID = "core"
    private const val STARS_LAYER = "stars"
    private const val DEEP_SKY_LAYER = "deep_sky"
    private const val CONSTELLATIONS_LAYER = "constellations"
    private const val METEOR_SHOWERS_LAYER = "meteor_showers"

    /** Universal-locale file name; its rows get locale `''` (designations match everywhere). */
    private const val UNIVERSAL = "universal"

    /**
     * Stars at or above this magnitude are excluded from the bundled pack, matching what v1
     * shipped (v1's `StarAttributeCalculator.MAX_MAGNITUDE = 5.6f`). The full `stars.csv` is
     * retained as seed data for a future expanded-catalog pack.
     */
    const val BUNDLED_STAR_MAX_MAGNITUDE = 5.6

    fun load(sourceDir: Path): CatalogData {
        val objects = linkedMapOf<String, ObjectRow>()
        val figures = mutableListOf<FigureRow>()
        val vertices = mutableListOf<VertexRow>()
        val links = mutableListOf<LinkRow>()
        val showers = mutableListOf<ShowerRow>()

        // Positional catalogs.
        val filteredStarIds = mutableSetOf<String>()
        readCsv(sourceDir.resolve("stars.csv")).forEach { row ->
            val id = row.getValue("id")
            val magnitude = row.getValue("magnitude").toDouble()
            if (magnitude >= BUNDLED_STAR_MAX_MAGNITUDE) {
                filteredStarIds += id
                return@forEach
            }
            objects[id] =
                ObjectRow(
                    id = id,
                    layerKind = STARS_LAYER,
                    type = "star",
                    ra = row.getValue("ra_deg").toDouble(),
                    dec = row.getValue("dec_deg").toDouble(),
                    magnitude = magnitude,
                    colorIndex = null,
                    searchFov = null,
                    parentObjectId = null,
                    imageRef = null,
                )
        }
        readCsv(sourceDir.resolve("dso.csv")).forEach { row ->
            val id = row.getValue("id")
            val type = row.getValue("type")
            objects[id] =
                ObjectRow(
                    id = id,
                    // Black holes are searchable at their own position but deliberately not
                    // rendered (catalog-and-schema.md capability table).
                    layerKind = if (type == "black_hole") null else DEEP_SKY_LAYER,
                    type = type,
                    ra = row.getValue("ra_deg").toDouble(),
                    dec = row.getValue("dec_deg").toDouble(),
                    magnitude = row.getValue("magnitude").toDoubleOrNull(),
                    colorIndex = null,
                    searchFov = null,
                    parentObjectId = null,
                    imageRef = null,
                )
        }
        for (constellation in readJson(sourceDir.resolve("constellations/iau.json"))
            .getValue("constellations").jsonArray) {
            val obj = constellation.jsonObject
            val id = obj.getValue("id").jsonPrimitive.content
            objects[id] =
                ObjectRow(
                    id = id,
                    layerKind = CONSTELLATIONS_LAYER,
                    type = "constellation",
                    ra = obj.getValue("ra").jsonPrimitive.double,
                    dec = obj.getValue("dec").jsonPrimitive.double,
                    magnitude = null,
                    colorIndex = null,
                    searchFov = null,
                    parentObjectId = null,
                    imageRef = null,
                )
            val culture = "iau"
            val figureId = "figure/${id.substringAfterLast('/')}-$culture"
            figures +=
                FigureRow(
                    id = figureId,
                    ownerObjectId = id,
                    layerKind = CONSTELLATIONS_LAYER,
                    kind = "lines",
                    culture = culture,
                )
            obj.getValue("strokes").jsonArray.forEachIndexed { stroke, points ->
                points.jsonArray.forEachIndexed { seq, point ->
                    val (ra, dec) = point.jsonArray.map { it.jsonPrimitive.double }
                    vertices += VertexRow(figureId, stroke, seq, ra, dec)
                }
            }
        }

        // objects.json: objects beyond the positional catalogs — ephemeris bodies and moons
        // (position-less), meteor showers (fixed radiant ra/dec plus an `activity` window that
        // puts them in the meteor-showers layer, D58) — plus metadata overlays (image, parent,
        // links, magnitude) for positional ones.
        for ((id, element) in readJson(sourceDir.resolve("objects.json"))
            .getValue("objects").jsonObject) {
            val meta = element.jsonObject

            fun string(key: String) = meta[key]?.jsonPrimitive?.contentOrNull
            val activity = meta["activity"]?.jsonObject
            if (activity != null) {
                fun date(key: String) = activity.getValue(key).jsonPrimitive.content
                showers +=
                    ShowerRow(
                        objectId = id,
                        activeFrom = date("from"),
                        peak = date("peak"),
                        activeTo = date("to"),
                        peakZhr = activity.getValue("peak_zhr").jsonPrimitive.int,
                    )
            }
            require(id !in filteredStarIds) {
                "objects.json entry '$id' refers to a star excluded from the bundled pack " +
                    "(magnitude >= $BUNDLED_STAR_MAX_MAGNITUDE)"
            }
            val existing = objects[id]
            objects[id] =
                existing?.copy(
                    parentObjectId = string("parent") ?: existing.parentObjectId,
                    imageRef = string("image_ref") ?: existing.imageRef,
                    magnitude = existing.magnitude ?: meta["magnitude"]?.jsonPrimitive?.double,
                    // Position stays the positional catalog's to own, but no catalog sets a
                    // search FOV, so this file is the only place one can be declared.
                    searchFov =
                        meta["search_fov"]?.jsonPrimitive?.doubleOrNull ?: existing.searchFov,
                ) ?: ObjectRow(
                    id = id,
                    layerKind = if (activity != null) METEOR_SHOWERS_LAYER else null,
                    type =
                        requireNotNull(string("type")) {
                            "objects.json entry '$id' is not in a positional catalog, " +
                                "so it must declare a type"
                        },
                    ra = meta["ra"]?.jsonPrimitive?.doubleOrNull,
                    dec = meta["dec"]?.jsonPrimitive?.doubleOrNull,
                    magnitude = meta["magnitude"]?.jsonPrimitive?.doubleOrNull,
                    colorIndex = null,
                    searchFov = meta["search_fov"]?.jsonPrimitive?.doubleOrNull,
                    parentObjectId = string("parent"),
                    imageRef = string("image_ref"),
                )
            meta["see_also"]?.jsonArray?.forEachIndexed { seq, target ->
                links += LinkRow(id, target.jsonPrimitive.content, seq)
            }
        }

        // Localized names; universal.csv carries the '' locale.
        val names = mutableListOf<NameRow>()
        for (file in sourceDir.resolve("names").sortedFiles("csv")) {
            // Lowercased to match LocaleSpec's normalized fallback chain ("zh-hans").
            val tag = file.nameWithoutExtension.lowercase()
            val locale = if (tag == UNIVERSAL) "" else tag
            readCsv(file).forEach { row ->
                names +=
                    NameRow(
                        objectId = row.getValue("object_id"),
                        locale = locale,
                        name = row.getValue("name"),
                        isPrimary = row.getValue("is_primary") == "1",
                    )
            }
        }

        // Names for stars filtered out of the bundled pack travel with the future expanded
        // pack; only ids the magnitude filter actually removed are exempt from strict
        // validation, so a typo'd star id in a names CSV still fails generation.
        names.removeAll { it.objectId in filteredStarIds }

        // Localized info cards.
        val cards = mutableListOf<CardRow>()
        for (file in sourceDir.resolve("info_cards").sortedFiles("json")) {
            val locale = file.nameWithoutExtension.lowercase()
            for ((id, element) in readJson(file).getValue("cards").jsonObject) {
                val card = element.jsonObject

                fun string(key: String) = card[key]?.jsonPrimitive?.contentOrNull
                cards +=
                    CardRow(
                        objectId = id,
                        locale = locale,
                        description = string("description"),
                        funFact = string("fun_fact"),
                        distance = string("distance"),
                        size = string("size"),
                        mass = string("mass"),
                        spectralClass = string("spectral_class"),
                        imageCredit = string("image_credit"),
                        searchSubtext = string("search_subtext"),
                    )
            }
        }

        // Cards for magnitude-filtered stars travel with the future expanded pack, exactly as
        // their names do above: the card text is written once against source-data and stays
        // valid when the bundle's magnitude cutoff rises. Only ids the filter actually removed
        // are exempt, so a typo'd object id in a card file still fails generation.
        cards.removeAll { it.objectId in filteredStarIds }

        // Type vocabulary.
        val types = mutableListOf<TypeRow>()
        val typeNames = mutableListOf<TypeNameRow>()
        for (element in readJson(sourceDir.resolve("types.json")).getValue("types").jsonArray) {
            val type = element.jsonObject
            val code = type.getValue("code").jsonPrimitive.content
            val parent = type["parent"]?.jsonPrimitive?.takeIf { it.isString }?.content
            types += TypeRow(code, parent)
            for ((locale, name) in type.getValue("names").jsonObject) {
                typeNames += TypeNameRow(code, locale, name.jsonPrimitive.content)
            }
        }

        return CatalogData(
            pack =
                PackRow(
                    id = PACK_ID,
                    version = 1,
                    license = "Mixed; see source-data/README.md",
                    sourceUrl = "https://github.com/sky-map-team/stardroid",
                    builtin = true,
                ),
            types = types,
            typeNames = typeNames,
            objects = objects.values.toList(),
            showers = showers,
            names = names,
            links = links,
            cards = cards,
            figures = figures,
            vertices = vertices,
        ).also { it.validate() }
    }

    private fun Path.sortedFiles(ext: String) =
        listDirectoryEntries()
            .filter { it.extension == ext }
            .sortedBy { it.fileName.toString() }

    private fun readJson(path: Path): JsonObject =
        Json.parseToJsonElement(Files.readString(path)).jsonObject

    /** Minimal RFC-4180 reader: quoted fields, doubled-quote escapes, no embedded newlines. */
    private fun readCsv(path: Path): List<Map<String, String>> {
        val lines = Files.readAllLines(path).filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = splitCsvLine(lines.first())
        return lines.drop(1).map { line ->
            val fields = splitCsvLine(line)
            require(fields.size == header.size) { "$path: malformed row: $line" }
            header.zip(fields).toMap()
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        require(!inQuotes) { "unterminated quote in: $line" }
        fields += current.toString()
        return fields
    }
}
