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

/**
 * Stable key for a catalog entry, e.g. `"star/sirius"`, `"dso/m31"`, `"planet/jupiter"`,
 * `"constellation/orion"`. Identity survives pack updates: names, info cards, and links attach
 * to this id (catalog-and-schema.md).
 */
@JvmInline
value class CelestialObjectId(val value: String)

/**
 * Hierarchical object type code, e.g. `"galaxy.spiral.barred"`. Types are **data** (the
 * `object_type` table), not an enum: downloaded packs may introduce subtypes the shipped app has
 * never seen. Code anchors only on root codes; behavior (icons, grouping) resolves by walking
 * *up* the hierarchy via [isA], so unknown subtypes inherit their nearest known ancestor's
 * treatment.
 */
@JvmInline
value class TypeCode(val path: String) {
    /** `"galaxy.spiral".isA(TypeCode("galaxy"))` and `x.isA(x)` are both `true`. */
    fun isA(ancestor: TypeCode): Boolean =
        path == ancestor.path || path.startsWith(ancestor.path + ".")
}

/**
 * The renderable layer a catalog object belongs to. Future kinds are data, not code — this enum
 * only names the layers the shipped app renders (catalog-and-schema.md).
 */
enum class LayerKind {
    STARS,
    DEEP_SKY,
    CONSTELLATIONS,
    METEOR_SHOWERS,
}

/**
 * One render-ready catalog object: semantic data only (type, magnitude, color index) — styling
 * is layer/backend policy (D12). [name] is already resolved for the requesting [LocaleSpec].
 */
data class CatalogObject(
    val id: CelestialObjectId,
    val layerKind: LayerKind,
    val type: TypeCode,
    /** J2000. */
    val position: RaDec,
    val magnitude: Double?,
    /** B–V where known (stars). */
    val colorIndex: Double?,
    val name: String,
    /**
     * Whether [name] is the object's primary name rather than a secondary designation
     * ("Alpha Cygni"). Map labels use primary names only; secondary names are search-only.
     */
    val nameIsPrimary: Boolean,
    /** Target FOV in degrees when this object is searched (v1's `search_level`). */
    val searchFovDeg: Double?,
)

/**
 * A named figure on the sky (constellation stick figure): [strokes] are independent polylines in
 * J2000 coordinates, owned by a searchable catalog object (the constellation itself).
 */
data class Figure(val owner: CelestialObjectId, val strokes: List<List<RaDec>>)

/**
 * A year-agnostic calendar day ("July 17"), the unit of a meteor shower's activity window.
 * Comparable in calendar order; windows never cross a year boundary (the generator validates
 * `from <= peak <= to`), matching v1's `MeteorShowerLayer` constraint.
 */
data class MonthDay(val month: Int, val day: Int) : Comparable<MonthDay> {
    init {
        require(month in 1..12) { "month out of range: $month" }
        // 29 for February keeps leap-day rows representable year-agnostically.
        val maxDays =
            when (month) {
                2 -> 29
                4, 6, 9, 11 -> 30
                else -> 31
            }
        require(day in 1..maxDays) { "day $day out of range for month $month" }
    }

    override fun compareTo(other: MonthDay): Int =
        compareValuesBy(this, other, { it.month }, { it.day })

    companion object {
        /** Parses the schema's `"MM-DD"` column format ("07-17"). */
        fun parse(text: String): MonthDay {
            val (month, day) = text.split('-').map { it.toInt() }
            return MonthDay(month, day)
        }
    }
}

/**
 * One meteor shower: a radiant position plus the annual activity window from the
 * `meteor_shower` sidecar table (layers-and-app.md — static annual data as catalog rows, not
 * code). [name] is already resolved for the requesting [LocaleSpec]; [peakZhr] is the peak
 * zenithal hourly rate the layer interpolates toward across the window.
 */
data class MeteorShower(
    val id: CelestialObjectId,
    val name: String,
    /** J2000. */
    val radiant: RaDec,
    val activeFrom: MonthDay,
    val peak: MonthDay,
    val activeTo: MonthDay,
    val peakZhr: Int,
) {
    /**
     * The annual activity window. Windows never cross a year boundary (generator-validated), so
     * a plain range comparison holds. Drawing and tapping share this one definition — a radiant
     * is tappable exactly while the layer draws it.
     */
    val window: ClosedRange<MonthDay> get() = activeFrom..activeTo

    /** Whether this shower is active on [date] — its month/day inside [window]. */
    fun isActiveOn(date: MonthDay): Boolean = date in window
}
