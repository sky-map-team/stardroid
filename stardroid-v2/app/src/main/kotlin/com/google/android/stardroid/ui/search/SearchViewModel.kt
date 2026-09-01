/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.CoordinateParser
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The active search target: a **static celestial snapshot**, exactly as v1's `SearchHelper`
 * copied the target on activation — a planet found under time travel does not drift within the
 * search session. [fovDeg] is the catalog's `search_level`, `null` for coordinates and objects
 * without one.
 */
data class SearchTarget(
    val name: String,
    val direction: Vector3,
    val fovDeg: Double?,
)

/**
 * Query → ranked hits, target selection/resolution, and search-mode state
 * (layers-and-app.md). Ports v1's `doSearchWithIntent`: coordinates parse first
 * ([CoordinateParser]), then the catalog's ranked prefix search — which replaces v1's
 * per-layer `PrefixStore` tries and visibility gating; the FTS query spans the whole catalog.
 *
 * Solar-system hits are position-less in the DB (the `planet/` rows and their moons are
 * card-only), so resolution falls through to the [ephemeris] at the shared clock's [now] —
 * matching v1, where `SearchResult.coords()` read the renderable's live location at search
 * time. Resolution is topocentric for the observer at [location] (D54), so the search arrow
 * aims at the Moon where the layer draws it, not up to ~1° away.
 */
class SearchViewModel(
    private val catalog: suspend () -> CatalogRepository,
    private val locale: StateFlow<LocaleSpec>,
    private val ephemeris: Ephemeris,
    private val now: () -> Instant,
    private val settings: Settings,
    private val analytics: Analytics = NoOpAnalytics,
    private val isManualMode: () -> Boolean = { false },
    private val location: () -> LatLong = { LatLong(0.0, 0.0) },
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** True after a submit/selection that resolved nothing (v1's no-results dialog). */
    private val _noResults = MutableStateFlow(false)
    val noResults: StateFlow<Boolean> = _noResults.asStateFlow()

    /** Non-null while search mode is engaged (the overlay and control bar show). */
    private val _target = MutableStateFlow<SearchTarget?>(null)
    val target: StateFlow<SearchTarget?> = _target.asStateFlow()

    /**
     * Ranked as-you-type hits, one query per typing pause rather than one per keystroke.
     *
     * `mapLatest` alone was not enough: it cancels the stale *coroutine*, but a Room suspend
     * query is not cancellable mid-flight and runs to completion on the query executor anyway,
     * so typing "andromeda" fired nine full searches (audit-2026-08 M3). The debounce is what
     * actually stops them being issued; `mapLatest` still discards a result that a newer query
     * has overtaken.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val suggestions: StateFlow<List<SearchHit>> =
        combine(
            _query.debounce { if (it.isBlank()) Duration.ZERO else SEARCH_DEBOUNCE },
            // A language switch leaves this view model alive, so a list already on screen would
            // otherwise keep the old language's names until the next keystroke.
            locale,
        ) { q, spec -> q to spec }
            .mapLatest { (q, spec) ->
                if (q.isBlank()) {
                    emptyList()
                } else {
                    catalog().searchByPrefix(q.trim(), spec, SUGGESTION_LIMIT)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun setQuery(text: String) {
        _query.value = text
        _noResults.value = false
    }

    /**
     * A programmatic aim by stable catalog id — the time-travel presets' per-event target
     * (v1 searched the layers by localized name after the travel transition). Unknown ids do
     * nothing: the target is decoration on the travel, not a user search to fail loudly.
     */
    fun selectById(id: CelestialObjectId) {
        viewModelScope.launch {
            val info = catalog().objectInfo(id, locale.value) ?: return@launch
            select(
                SearchHit(
                    id = info.id,
                    name = info.name,
                    subtext = info.searchSubtext,
                    position = info.position,
                    searchFovDeg = info.searchFovDeg,
                ),
            )
        }
    }

    /** The user picked a hit from the list (v1's single-result / "Did you mean?" selection). */
    fun select(hit: SearchHit) {
        viewModelScope.launch {
            val info = catalog().objectInfo(hit.id, locale.value)
            val direction = resolveDirection(hit, info)
            if (direction != null) {
                // Search spans hidden layers (D43); re-enable the hit's layer so the arrow
                // never guides the user to empty sky (PR #23 review).
                layerIdFor(hit, info)?.let { settings.setLayerEnabled(it, enabled = true) }
                activate(SearchTarget(hit.name, direction, hit.searchFovDeg))
            } else {
                searchFailed(hit.name)
            }
        }
    }

    /**
     * The keyboard search action: coordinates win ([CoordinateParser], as in v1), then an
     * exact-name hit, then a sole prefix hit. Several inexact hits leave the suggestion list
     * standing — that list *is* v1's "Did you mean?" dialog.
     */
    fun submit() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        CoordinateParser.parseCoordinates(q)?.let { raDec ->
            activate(SearchTarget(formatRaDec(raDec), raDec.toGeocentricVector(), null))
            return
        }
        viewModelScope.launch {
            val hits = catalog().searchByPrefix(q, locale.value, SUGGESTION_LIMIT)
            val chosen =
                hits.firstOrNull { it.name.equals(q, ignoreCase = true) } ?: hits.singleOrNull()
            if (chosen != null) {
                select(chosen)
            } else if (hits.isEmpty()) {
                searchFailed(q)
            }
        }
    }

    /** v1 `cancelSearch`: fired by the cancel button or BACK. */
    fun cancelSearch() {
        _target.value = null
    }

    /** Engaging a target starts a fresh session: the next dialog opens with an empty query. */
    private fun activate(target: SearchTarget) {
        analytics.trackEvent(
            AnalyticsEvents.SEARCH_EVENT,
            mapOf(
                // The query when typed; the target name for a tap-through (gallery Find).
                AnalyticsEvents.SEARCH_TERM to _query.value.trim().ifEmpty { target.name },
                AnalyticsEvents.SEARCH_SUCCESS to "true",
            ),
        )
        analytics.trackEvent(
            AnalyticsEvents.OBJECT_LOCKED_EVENT,
            mapOf(
                AnalyticsEvents.OBJECT_LOCKED_NAME to target.name,
                AnalyticsEvents.OBJECT_LOCKED_MODE to
                    if (isManualMode()) {
                        AnalyticsEvents.OBJECT_LOCKED_MODE_MANUAL
                    } else {
                        AnalyticsEvents.OBJECT_LOCKED_MODE_AUTO
                    },
            ),
        )
        _target.value = target
        setQuery("")
    }

    private fun searchFailed(term: String) {
        analytics.trackEvent(
            AnalyticsEvents.SEARCH_FAILED_EVENT,
            mapOf(AnalyticsEvents.SEARCH_TERM to term),
        )
        _noResults.value = true
    }

    /**
     * The schema's independent-capabilities rule, extended to the solar system: the hit's own
     * resolved position, else the ephemeris for a `planet/` id, else the parent's — which for
     * a card-only moon (`moon/io` → Jupiter) again means the ephemeris.
     */
    private fun resolveDirection(
        hit: SearchHit,
        info: ObjectInfo?,
    ): Vector3? {
        hit.position?.let { return it.toGeocentricVector() }
        SolarSystemIds.bodyFor(hit.id)?.let { return bodyDirection(it) }
        if (info == null) return null
        info.parent?.let { parent ->
            SolarSystemIds.bodyFor(parent)?.let { return bodyDirection(it) }
        }
        return info.position?.toGeocentricVector()
    }

    /** The toggleable layer that draws [hit], `null` for coordinate-only/card-only targets. */
    private fun layerIdFor(
        hit: SearchHit,
        info: ObjectInfo?,
    ): LayerId? =
        when {
            SolarSystemIds.bodyFor(hit.id) != null -> SolarSystemLayer.LAYER_ID
            info?.parent?.let(SolarSystemIds::bodyFor) != null -> SolarSystemLayer.LAYER_ID
            else ->
                when (info?.layerKind) {
                    LayerKind.STARS -> CatalogLayers.STARS_LAYER_ID
                    LayerKind.DEEP_SKY -> CatalogLayers.DEEP_SKY_LAYER_ID
                    LayerKind.CONSTELLATIONS -> CatalogLayers.CONSTELLATIONS_LAYER_ID
                    LayerKind.METEOR_SHOWERS -> MeteorShowerLayer.LAYER_ID
                    null -> null
                }
        }

    private fun bodyDirection(body: SolarSystemBody): Vector3 =
        ephemeris.topocentricPosition(body, now(), location()).toGeocentricVector()

    companion object {
        /** Ranked-list length; v1's suggestion cursor was unbounded, the FTS query is not. */
        private const val SUGGESTION_LIMIT = 20

        /**
         * How long typing has to pause before a search is issued. Short enough to feel
         * immediate (below the ~200 ms where a UI response starts reading as lag), long enough
         * that a normal typing cadence collapses a word into one query. A cleared query skips
         * the wait — emptying the field should blank the list at once.
         */
        private val SEARCH_DEBOUNCE = 150.milliseconds

        /**
         * The target label for a coordinate search (v1 `search_target_ra_dec_format`), e.g.
         * `"12h 30m, +45.0°"`. ROOT locale: it's a coordinate, not prose.
         */
        internal fun formatRaDec(raDec: RaDec): String {
            val totalMinutes = (raDec.raDeg / 15.0 * 60.0).roundToInt()
            val h = (totalMinutes / 60) % 24
            val m = totalMinutes % 60
            return String.format(Locale.ROOT, "%dh %02dm, %+.1f°", h, m, raDec.decDeg)
        }
    }
}
