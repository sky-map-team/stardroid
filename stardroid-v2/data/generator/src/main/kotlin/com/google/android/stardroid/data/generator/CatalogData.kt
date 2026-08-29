/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.generator

import com.google.android.stardroid.catalog.MonthDay

/*
 * In-memory image of the rows the generator writes — one type per table of the
 * catalog schema (docs/design/catalog-and-schema.md), Android-free.
 */

data class PackRow(
    val id: String,
    val version: Int,
    val license: String?,
    val sourceUrl: String?,
    val builtin: Boolean,
)

data class TypeRow(val code: String, val parentCode: String?)

data class TypeNameRow(val code: String, val locale: String, val name: String)

data class ObjectRow(
    val id: String,
    val layerKind: String?,
    val type: String,
    val ra: Double?,
    val dec: Double?,
    val magnitude: Double?,
    val colorIndex: Double?,
    val searchFov: Double?,
    val parentObjectId: String?,
    val imageRef: String?,
)

/** One `meteor_shower` sidecar row; dates are `"MM-DD"` ([MonthDay.parse]-compatible). */
data class ShowerRow(
    val objectId: String,
    val activeFrom: String,
    val peak: String,
    val activeTo: String,
    val peakZhr: Int,
)

data class NameRow(
    val objectId: String,
    val locale: String,
    val name: String,
    val isPrimary: Boolean,
)

data class LinkRow(val objectId: String, val linkedId: String, val seq: Int)

data class CardRow(
    val objectId: String,
    val locale: String,
    val description: String?,
    val funFact: String?,
    val distance: String?,
    val size: String?,
    val mass: String?,
    val spectralClass: String?,
    val imageCredit: String?,
    val searchSubtext: String?,
)

data class FigureRow(
    val id: String,
    val ownerObjectId: String,
    val layerKind: String,
    val kind: String,
    val culture: String,
)

data class VertexRow(
    val figureId: String,
    val stroke: Int,
    val seq: Int,
    val ra: Double,
    val dec: Double,
)

/** Everything one pack contributes, validated and ready to write. */
data class CatalogData(
    val pack: PackRow,
    val types: List<TypeRow>,
    val typeNames: List<TypeNameRow>,
    val objects: List<ObjectRow>,
    val showers: List<ShowerRow> = emptyList(),
    val names: List<NameRow>,
    val links: List<LinkRow>,
    val cards: List<CardRow>,
    val figures: List<FigureRow>,
    val vertices: List<VertexRow>,
) {
    /**
     * The bundled pack must be internally consistent: unlike cross-pack references at
     * runtime (which may dangle by design, D33), a dangling reference here is a data bug.
     */
    fun validate() {
        val ids = objects.map { it.id }
        val idSet = ids.toSet()
        require(ids.size == idSet.size) {
            "duplicate object ids: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}"
        }
        val typeCodes = types.map { it.code }.toSet()

        fun requireObject(
            id: String,
            what: String,
        ) = require(id in idSet) { "$what references unknown object '$id'" }

        types.forEach { t ->
            t.parentCode?.let {
                require(it in typeCodes) { "type '${t.code}' has unknown parent '$it'" }
            }
        }
        typeNames.forEach {
            require(it.code in typeCodes) { "type_name for unknown type '${it.code}'" }
        }
        objects.forEach { o ->
            require(o.type in typeCodes) { "object '${o.id}' has unknown type '${o.type}'" }
            o.parentObjectId?.let { requireObject(it, "parent of '${o.id}'") }
            require((o.ra == null) == (o.dec == null)) { "'${o.id}' has half a position" }
            o.ra?.let { require(it in 0.0..360.0) { "'${o.id}' ra out of range: $it" } }
            o.dec?.let { require(it in -90.0..90.0) { "'${o.id}' dec out of range: $it" } }
        }
        val showerIds = showers.map { it.objectId }
        require(showerIds.size == showerIds.toSet().size) { "duplicate shower rows" }
        val objectsById = objects.associateBy { it.id }
        showers.forEach { s ->
            requireObject(s.objectId, "meteor shower")
            val owner = objectsById.getValue(s.objectId)
            require(owner.ra != null) { "shower '${s.objectId}' has no radiant position" }
            require(owner.layerKind == "meteor_showers") {
                "shower '${s.objectId}' must have layer_kind 'meteor_showers'"
            }
            val window = listOf(s.activeFrom, s.peak, s.activeTo).map(MonthDay::parse)
            // Windows must not cross a year boundary (the layer's date math assumes it).
            require(window.zipWithNext().all { (a, b) -> a <= b }) {
                "shower '${s.objectId}' window is not ordered: " +
                    "${s.activeFrom} <= ${s.peak} <= ${s.activeTo}"
            }
            require(s.peakZhr > 0) { "shower '${s.objectId}' has non-positive peak_zhr" }
        }
        val showerIdSet = showerIds.toSet()
        objects.forEach { o ->
            require(o.layerKind != "meteor_showers" || o.id in showerIdSet) {
                "'${o.id}' is in the meteor-showers layer but has no activity window"
            }
        }
        names.forEach { n ->
            requireObject(n.objectId, "name '${n.name}'")
            require(n.name.isNotBlank()) { "blank name for '${n.objectId}'" }
        }
        links.forEach { l ->
            requireObject(l.objectId, "link")
            requireObject(l.linkedId, "link from '${l.objectId}'")
        }
        cards.forEach { requireObject(it.objectId, "info card") }
        val figureIds = figures.map { it.id }
        require(figureIds.size == figureIds.toSet().size) { "duplicate figure ids" }
        figures.forEach { requireObject(it.ownerObjectId, "figure '${it.id}'") }
        val figureIdSet = figureIds.toSet()
        vertices.forEach {
            require(it.figureId in figureIdSet) { "vertex for unknown figure '${it.figureId}'" }
            require(it.ra in 0.0..360.0) { "'${it.figureId}' vertex ra out of range: ${it.ra}" }
            require(it.dec in -90.0..90.0) {
                "'${it.figureId}' vertex dec out of range: ${it.dec}"
            }
        }
    }
}
