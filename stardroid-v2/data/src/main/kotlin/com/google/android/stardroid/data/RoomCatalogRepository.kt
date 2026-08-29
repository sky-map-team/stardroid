/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.catalog.NameNormalizer
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.math.RaDec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The Room-backed [CatalogRepository] (catalog-and-schema.md). SQL narrows to candidates
 * (FTS word-prefix match, locale-chain restriction); Kotlin resolves the winners (best name
 * per object along the fallback chain, search ranking) — see [CatalogDao].
 */
class RoomCatalogRepository(
    database: SkyMapDatabase,
    private val backgroundContext: CoroutineDispatcher = Dispatchers.Default,
) : CatalogRepository {
    private val dao = database.catalogDao()

    override fun layerObjects(
        kind: LayerKind,
        locale: LocaleSpec,
    ): Flow<List<CatalogObject>> =
        dao.layerObjectRows(kind.dbCode, locale.fallbackChain)
            .map { rows ->
                rows.groupBy { it.id }.map { (_, group) -> group.toCatalogObject(kind, locale) }
            }
            .distinctUntilChanged()

    override fun figures(
        kind: LayerKind,
        culture: String,
    ): Flow<List<Figure>> =
        dao.figureVertexRows(kind.dbCode, culture)
            .map { rows ->
                // Rows arrive ordered by (figure, stroke, seq); one Figure per figure id.
                rows.groupBy { it.figureId }.map { (_, vertices) ->
                    Figure(
                        owner = CelestialObjectId(vertices.first().ownerObjectId),
                        strokes =
                            vertices.groupBy { it.stroke }.map { (_, stroke) ->
                                stroke.map { RaDec(it.ra, it.dec) }
                            },
                    )
                }
            }
            .distinctUntilChanged()

    override fun meteorShowers(locale: LocaleSpec): Flow<List<MeteorShower>> =
        dao.meteorShowerRows(locale.fallbackChain)
            .map { rows ->
                rows.groupBy { it.id }.map { (_, group) ->
                    val row = group.first()
                    MeteorShower(
                        id = CelestialObjectId(row.id),
                        name = group.bestNameInChain(locale)?.name.orEmpty(),
                        radiant = RaDec(row.ra, row.dec),
                        activeFrom = MonthDay.parse(row.activeFrom),
                        peak = MonthDay.parse(row.peak),
                        activeTo = MonthDay.parse(row.activeTo),
                        peakZhr = row.peakZhr,
                    )
                }
            }
            .distinctUntilChanged()

    override suspend fun searchByPrefix(
        prefix: String,
        locale: LocaleSpec,
        limit: Int,
    ): List<SearchHit> {
        val terms = tokenize(prefix)
        if (terms.isEmpty() || limit <= 0) return emptyList()
        // Terms are lowercased alphanumeric-only after tokenizing, so no FTS syntax — including
        // the uppercase-only AND/OR/NOT operator keywords — can leak through; unicode61 folds
        // case/diacritics on query terms the same way it did on the index.
        val ftsQuery = terms.joinToString(" ") { "$it*" }
        val normalizedPrefix = NameNormalizer.normalize(prefix)
        // Over-fetch relative to the display limit: the candidate set is name rows, several per
        // object across the locale chain, and the Kotlin ranking below still needs something to
        // rank (audit-2026-08 M3).
        val candidateLimit = limit * SEARCH_CANDIDATE_OVERFETCH
        val candidates =
            dao.searchNameRows(ftsQuery, locale.fallbackChain, normalizedPrefix, candidateLimit)
        if (candidates.isEmpty()) return emptyList()

        // Whole-object locale fallback (D33): once an object is translated, only its winning
        // locale's names are searchable, so a request locale never matches — and so never
        // displays — the English rows that its own translation shadows. Universal designations
        // ("M31") stay matchable everywhere. The full row set decides the winner, not just the
        // matched rows: "Seven Sisters" must lose to a French translation that "sev" never hit.
        val winners =
            dao.namesFor(candidates.map { it.objectId }.distinct(), locale.fallbackChain)
                .groupBy { it.objectId }
                .mapValues { (_, names) -> names.winningLocale(locale) { it.locale } }
        val rows = candidates.filter { it.locale.isEmpty() || it.locale == winners[it.objectId] }
        if (rows.isEmpty()) return emptyList()

        // Shared ranking head: whole-name-prefix beats word-prefix, then primary-name status.
        val matchQuality =
            compareByDescending<SearchNameRow> { it.nameNormalized.startsWith(normalizedPrefix) }
                .thenByDescending { it.isPrimary }
        // Best matched name per object: match quality, then locale specificity.
        val bestNameOrder =
            matchQuality
                .thenBy { locale.fallbackChain.indexOf(it.locale) }
                .thenBy { it.name }
        val rankedOrder =
            matchQuality
                // Brighter (smaller magnitude) first; unknown magnitude last.
                .thenBy { it.magnitude ?: Double.MAX_VALUE }
                .thenBy { it.name }
        val bestPerObject =
            rows.groupBy { it.objectId }.map { (_, matches) -> matches.minWith(bestNameOrder) }
        val ranked = bestPerObject.sortedWith(rankedOrder).take(limit)

        val subtexts = resolveSearchSubtexts(ranked.map { it.objectId }, locale)
        return ranked.map { row ->
            val position = resolvePosition(row.ra, row.dec, row.parentRa, row.parentDec)
            SearchHit(
                id = CelestialObjectId(row.objectId),
                name = row.name,
                subtext = subtexts[row.objectId],
                position = position,
                searchFovDeg =
                    resolveSearchFov(
                        row.ra,
                        row.dec,
                        row.searchFov,
                        row.parentSearchFov,
                    ),
            )
        }
    }

    override suspend fun objectInfo(
        id: CelestialObjectId,
        locale: LocaleSpec,
    ): ObjectInfo? {
        val entity = dao.objectById(id.value) ?: return null
        val chain = locale.fallbackChain
        val linkRows = dao.linkRows(entity.id)
        val names =
            dao.namesFor(listOf(entity.id) + linkRows.map { it.id }, chain)
                .groupBy { it.objectId }
        val card = dao.infoCardsFor(listOf(entity.id), chain).pickByChain(locale) { it.locale }
        val parent = entity.parentObjectId?.let { dao.objectById(it) }
        val linkSubtexts = resolveSearchSubtexts(linkRows.map { it.id }, locale)
        val displayName = names[entity.id].bestName(locale)

        return ObjectInfo(
            id = id,
            name = displayName,
            otherNames = names[entity.id].otherNames(locale, displayName),
            type = TypeCode(entity.type),
            layerKind = LayerKind.entries.firstOrNull { it.dbCode == entity.layerKind },
            position = resolvePosition(entity.ra, entity.dec, parent?.ra, parent?.dec),
            parent = entity.parentObjectId?.let { CelestialObjectId(it) },
            magnitude = entity.magnitude,
            description = card?.description,
            funFact = card?.funFact,
            distance = card?.distance,
            size = card?.size,
            mass = card?.mass,
            spectralClass = card?.spectralClass,
            imageRef = entity.imageRef,
            imageCredit = card?.imageCredit,
            searchSubtext = card?.searchSubtext,
            searchFovDeg =
                resolveSearchFov(entity.ra, entity.dec, entity.searchFov, parent?.searchFov),
            links =
                linkRows.map { link ->
                    SearchHit(
                        id = CelestialObjectId(link.id),
                        name = names[link.id].bestName(locale),
                        subtext = linkSubtexts[link.id],
                        position =
                            resolvePosition(
                                link.ra,
                                link.dec,
                                link.parentRa,
                                link.parentDec,
                            ),
                        searchFovDeg =
                            resolveSearchFov(
                                link.ra,
                                link.dec,
                                link.searchFov,
                                link.parentSearchFov,
                            ),
                    )
                },
        )
    }

    override suspend fun infoCardObjectIds(): Set<CelestialObjectId> =
        dao.infoCardObjectIds().mapTo(mutableSetOf()) { CelestialObjectId(it) }

    override suspend fun galleryItems(locale: LocaleSpec): List<GalleryItem> {
        val rows = dao.galleryRows(locale.fallbackChain)
        return withContext(backgroundContext) {
            rows
                .groupBy { it.id }
                .map { (id, group) ->
                    val name = group.bestNameInChain(locale)?.name
                    GalleryItem(
                        id = CelestialObjectId(id),
                        name = name.orEmpty(),
                        imageRef = group.first().imageRef,
                    )
                }
                // v1 getAllWithImages: sorted by display name.
                .sortedBy { it.name }
        }
    }

    /** Subtext per object: the locale-chain-best info card's `search_subtext`. */
    private suspend fun resolveSearchSubtexts(
        objectIds: List<String>,
        locale: LocaleSpec,
    ): Map<String, String?> =
        dao.infoCardsFor(objectIds, locale.fallbackChain)
            .groupBy { it.objectId }
            .mapValues { (_, cards) -> cards.pickByChain(locale) { it.locale }?.searchSubtext }

    private fun List<LayerObjectRow>.toCatalogObject(
        kind: LayerKind,
        locale: LocaleSpec,
    ): CatalogObject {
        val row = first()
        val nameRow = bestNameInChain(locale)
        return CatalogObject(
            id = CelestialObjectId(row.id),
            layerKind = kind,
            type = TypeCode(row.type),
            position = RaDec(row.ra, row.dec),
            magnitude = row.magnitude,
            colorIndex = row.colorIndex,
            // Objects are expected to carry at least a universal designation; an empty name is
            // the graceful floor for data that doesn't (layers just skip the label).
            name = nameRow?.name.orEmpty(),
            nameIsPrimary = nameRow?.isPrimary == true,
            searchFovDeg = row.searchFov,
        )
    }

    /**
     * The object's other names — every searchable alias except [displayName], the card title.
     * Restricted to the winning locale plus universal designations by the same whole-object rule
     * [searchByPrefix] applies, so a translated object's card never advertises the English names
     * its translation shadows. Primary before secondary, then alphabetical, and de-duplicated so
     * a name shared with the universal locale appears once. This stays exactly the set a search
     * in this locale could match without matching the title.
     */
    private fun List<ObjectNameEntity>?.otherNames(
        locale: LocaleSpec,
        displayName: String,
    ): List<String> {
        val rows = orEmpty()
        val winner = rows.winningLocale(locale) { it.locale }
        return rows
            .filter { it.locale.isEmpty() || it.locale == winner }
            .sortedWith(
                compareBy<ObjectNameEntity> {
                    val index = locale.fallbackChain.indexOf(it.locale)
                    if (index < 0) Int.MAX_VALUE else index
                }
                    .thenByDescending { it.isPrimary }
                    .thenBy { it.name },
            )
            .map { it.name }
            .distinct()
            .filter { it != displayName }
    }

    /**
     * The chain locale whose rows shadow every other language's for this object: the most
     * specific chain locale carrying any row. The universal locale is excluded — its
     * designations join the winner rather than competing with it — so this is null for an
     * object named only universally, leaving just those designations visible.
     */
    private fun <T> List<T>.winningLocale(
        locale: LocaleSpec,
        localeOf: (T) -> String,
    ): String? =
        map(localeOf)
            .filter { it.isNotEmpty() && it in locale.fallbackChain }
            .minByOrNull { locale.fallbackChain.indexOf(it) }

    private fun List<ObjectNameEntity>?.bestName(locale: LocaleSpec): String =
        orEmpty()
            .sortedWith(compareByDescending<ObjectNameEntity> { it.isPrimary }.thenBy { it.name })
            .pickByChain(locale) { it.locale }
            ?.name
            .orEmpty()

    /**
     * The locale-fallback name-picking idiom shared by [meteorShowers], [galleryItems], and
     * [toCatalogObject]: only rows with a name, primary-first then alphabetical for a
     * deterministic tie inside [pickByChain], then the chain's best locale.
     */
    private fun <T : NameCandidate> List<T>.bestNameInChain(locale: LocaleSpec): T? =
        filter { it.name != null }
            .sortedWith(compareByDescending<T> { it.isPrimary == true }.thenBy { it.name })
            .pickByChain(locale) { it.nameLocale.orEmpty() }

    /**
     * The locale fallback rule: the first chain locale with any candidate wins, whole-row —
     * fields never mix across locales, so cards stay coherent.
     */
    private fun <T> List<T>.pickByChain(
        locale: LocaleSpec,
        localeOf: (T) -> String,
    ): T? =
        minByOrNull {
            val index = locale.fallbackChain.indexOf(localeOf(it))
            if (index < 0) Int.MAX_VALUE else index
        }

    private fun resolvePosition(
        ra: Double?,
        dec: Double?,
        parentRa: Double?,
        parentDec: Double?,
    ): RaDec? =
        when {
            ra != null && dec != null -> RaDec(ra, dec)
            parentRa != null && parentDec != null -> RaDec(parentRa, parentDec)
            else -> null
        }

    /** Own FOV wins; the parent's applies only when the hit resolves to the parent's position. */
    private fun resolveSearchFov(
        ra: Double?,
        dec: Double?,
        searchFov: Double?,
        parentSearchFov: Double?,
    ): Double? = if (ra != null && dec != null) searchFov else searchFov ?: parentSearchFov

    // Lowercased (Kotlin's lowercase() is locale-invariant) so a bare AND/OR/NOT term cannot
    // be parsed as an FTS4 operator keyword — those are recognized only in uppercase.
    private fun tokenize(query: String): List<String> =
        query.lowercase().split(NON_ALPHANUMERIC).filter { it.isNotEmpty() }

    private companion object {
        val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

        /**
         * How many name rows the SQL candidate set holds per requested hit. Each object can
         * contribute one row per locale in the chain, and the Kotlin ranking reorders within the
         * set, so a generous multiple keeps the answer identical to the unbounded query for any
         * realistic query while capping the worst case ("a" over the whole catalog) at a few
         * hundred rows.
         *
         * This also bounds the `namesFor` lookup that resolves each candidate's winning locale:
         * that query binds one variable per distinct candidate object, so `limit * OVERFETCH`
         * must stay clear of SQLite's 999-variable ceiling. At the app's limit of 20 the ceiling
         * is 400 — raise either number together with a chunked lookup, not on its own.
         */
        const val SEARCH_CANDIDATE_OVERFETCH = 20
    }
}

/** The `celestial_object.layer_kind` / `figure.layer_kind` column value for this kind. */
internal val LayerKind.dbCode: String
    get() =
        when (this) {
            LayerKind.STARS -> "stars"
            LayerKind.DEEP_SKY -> "deep_sky"
            LayerKind.CONSTELLATIONS -> "constellations"
            LayerKind.METEOR_SHOWERS -> "meteor_showers"
        }
