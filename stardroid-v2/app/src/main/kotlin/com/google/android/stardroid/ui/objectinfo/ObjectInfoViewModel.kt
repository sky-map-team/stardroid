/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.astronomy.LunarEclipseCircumstances
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.RiseSetIndicator
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.altitudeDeg
import com.google.android.stardroid.astronomy.horizonAltitudeDeg
import com.google.android.stardroid.astronomy.nextLunarEclipse
import com.google.android.stardroid.astronomy.nextRiseSetTime
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.layers.utcMonthDay
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.satellites.SatelliteIds
import com.google.android.stardroid.satellites.TrackedSatellite
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.ui.search.SolarSystemIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.math.min

/**
 * The card's rise/set line (D51): the next horizon crossings after the map's current time at
 * the observer's location, or the two no-crossing cases the solver folds into null.
 */
sealed interface RiseSetState {
    /** The next rise and set; either alone may be null if only one converged. */
    data class Times(val rise: Instant?, val set: Instant?) : RiseSetState

    /** Circumpolar at this location — never sets. */
    data object AlwaysAbove : RiseSetState

    /** Never clears the horizon at this location. */
    data object AlwaysBelow : RiseSetState
}

/**
 * Card content per id + locale, see-also links, tap-to-identify hit results
 * (layers-and-app.md). Ports v1's `education/`: `ObjectInfoRegistry` is the catalog's
 * `objectInfo` (the card rows shipped in the DB replace v1's JSON + string-resource join),
 * `CelestialHitTester` is [IdentifyGeometry] over the curated candidate set, and
 * `ObjectInfoTapHandler`'s preference gates carry over — tap-to-identify is on by default,
 * including in the sensor frame (both gates default on; either can be switched off in
 * settings — the D45 opt-in became an opt-out on user feedback).
 */
class ObjectInfoViewModel(
    private val catalog: suspend () -> CatalogRepository,
    private val locale: StateFlow<LocaleSpec>,
    private val ephemeris: Ephemeris,
    private val now: () -> Instant,
    private val settings: Settings,
    private val analytics: Analytics = NoOpAnalytics,
    private val location: () -> LatLong = { LatLong(0.0, 0.0) },
    private val computeContext: CoroutineContext = Dispatchers.Default,
    private val experimentConfig: ExperimentConfig = ExperimentConfig.Static,
    private val moonWidgetPlaced: () -> Boolean = { false },
    /**
     * The satellites currently drawn, with their sky positions — the tap targets and the source
     * of the synthesized card. Empty when the feature is off or nothing is cached.
     */
    private val satellites: suspend () -> List<TrackedSatellite> = { emptyList() },
    /** Next visible pass for a tapped satellite, or null. */
    private val nextPass: suspend (Int) -> SatellitePass? = { null },
    /**
     * Whether the cached element sets are fresh enough to time a pass honestly.
     *
     * Separate from [nextPass] because a null pass means two different things: "nothing is coming,
     * which is normal" versus "we could tell you, but it would be wrong". The card says which.
     */
    private val passTimesReliable: suspend () -> Boolean = { true },
) : ViewModel() {
    /** The card being shown, `null` when no card is up. */
    private val _card = MutableStateFlow<ObjectInfo?>(null)
    val card: StateFlow<ObjectInfo?> = _card.asStateFlow()

    /** Rise/set for the shown card; `null` when no card is up or the object has no position. */
    private val _riseSet = MutableStateFlow<RiseSetState?>(null)
    val riseSet: StateFlow<RiseSetState?> = _riseSet.asStateFlow()

    /**
     * Whether the Moon card shows its add-a-widget row (D75 discovery): only on the Moon,
     * only while the experiment is on, gone on its own once a moon widget is placed, and
     * never again after an explicit dismissal. A cancelled pin dialog changes nothing —
     * the offer survives anything short of success or a deliberate no.
     */
    val moonWidgetPromo: StateFlow<Boolean> =
        combine(_card, settings.moonWidgetPromoDismissed) { card, dismissed ->
            !dismissed &&
                card?.id == SolarSystemIds.idFor(SolarSystemBody.MOON) &&
                experimentConfig.isEnabled(Experiment.MOON_WIDGET) &&
                !moonWidgetPlaced()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The next visible pass for the shown satellite card, or null.
     *
     * Null covers three different situations that all render the same: the card is not a
     * satellite; the element sets are too stale to time a pass honestly; or the satellite simply
     * has no visible pass coming. The last is a real answer — visible passes cluster into
     * multi-day seasons separated by multi-week gaps — so the row says so rather than vanishing.
     */
    val nextSatellitePass: StateFlow<SatellitePass?> =
        _card
            .map { card ->
                card?.id?.let(SatelliteIds::noradIdFor)?.let { nextPass(it) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** See [passTimesReliable] — drives which empty message the pass row shows. */
    val satellitePassTimesReliable: StateFlow<Boolean> =
        _card
            .map { card ->
                card?.id?.let(SatelliteIds::noradIdFor)?.let { passTimesReliable() } ?: true
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** True while the shown card is a satellite, so the sheet knows to render the pass row. */
    val showingSatellite: StateFlow<Boolean> =
        _card
            .map { it?.id?.let(SatelliteIds::noradIdFor) != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True while the shown card is the Moon, so the sheet knows to render the eclipse row. */
    val showingMoon: StateFlow<Boolean> =
        _card
            .map { it?.id == MOON_ID }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The next lunar eclipse from the map's current time, or null if none is found — computed
     * only while the Moon's card is showing (D106), the same lazy shape [nextSatellitePass] uses.
     *
     * Always [MeeusEphemeris], regardless of [ephemeris]: eclipse timing needs the accurate,
     * precession-consistent Sun/Moon frame (D84) rather than whatever cheaper baseline this view
     * model was constructed with for less timing-sensitive things like tap identification.
     */
    val lunarEclipse: StateFlow<LunarEclipseCircumstances?> =
        _card
            .map { card ->
                if (card?.id != MOON_ID) {
                    null
                } else {
                    withContext(computeContext) { nextLunarEclipse(now(), MeeusEphemeris) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The explicit close button — the only path that permanently ends the offer. */
    fun dismissMoonWidgetPromo() {
        viewModelScope.launch { settings.setMoonWidgetPromoDismissed() }
    }

    /** Opens [id]'s card — a see-also chip tap, v1's `onSeeAlsoClicked`. */
    fun show(id: CelestialObjectId) {
        viewModelScope.launch {
            cardFor(id)?.let {
                analytics.trackEvent(
                    AnalyticsEvents.OBJECT_INFO_VIEWED_EVENT,
                    mapOf(AnalyticsEvents.OBJECT_INFO_ID to id.value),
                )
                openCard(it)
            }
        }
    }

    fun dismiss() {
        riseSetJob?.cancel()
        _card.value = null
        _riseSet.value = null
    }

    /**
     * A sky tap (v1 `ObjectInfoTapHandler.handleTap`): if the feature gates allow, hit-test the
     * tap against every curated object on an enabled layer and open the nearest card within
     * [IdentifyGeometry.labelInclusiveTapThresholdDeg]. Silently does nothing on a miss, as v1
     * did.
     *
     * [densityDpPerPx] lets the tolerance cover each object's label as well as the object,
     * which is what people actually aim at; see the threshold's own documentation.
     */
    fun onSkyTap(
        xPx: Float,
        yPx: Float,
        widthPx: Int,
        heightPx: Int,
        camera: SkyCamera,
        sensorFrame: Boolean,
        densityDpPerPx: Float,
    ) {
        if (widthPx <= 0 || heightPx <= 0) return
        viewModelScope.launch {
            if (!settings.tapToIdentify.first()) return@launch
            if (sensorFrame && !settings.tapToIdentifyInAutoMode.first()) return@launch
            val tapDirection =
                IdentifyGeometry.screenToDirection(camera, widthPx, heightPx, xPx, yPx)
            val threshold =
                IdentifyGeometry.labelInclusiveTapThresholdDeg(
                    fovDeg = camera.fovDeg,
                    shortSidePx = min(widthPx, heightPx),
                    densityDpPerPx = densityDpPerPx,
                    labelScaleFactor = settings.fontSize.first().scale,
                )
            val hit =
                withContext(computeContext) {
                    candidates()
                        .map {
                            it to IdentifyGeometry.angularSeparationDeg(tapDirection, it.direction)
                        }
                        .filter { (_, separation) -> separation < threshold }
                        .minByOrNull { (_, separation) -> separation }
                        ?.first
                } ?: return@launch
            catalog().objectInfo(hit.id, locale.value)?.let { openCard(it) }
        }
    }

    private var riseSetJob: Job? = null

    private fun openCard(info: ObjectInfo) {
        riseSetJob?.cancel()
        _riseSet.value = null
        _card.value = info
        riseSetJob =
            viewModelScope.launch {
                _riseSet.value = withContext(computeContext) { riseSetFor(info) }
            }
    }

    /**
     * Rise/set for [info] after the map's current time at the observer's location (D51):
     * solar-system cards (position-less in the catalog, [SolarSystemIds]) track the ephemeris —
     * a moon rides its parent planet, well within the solver's tolerance — while everything
     * else sits fixed at its catalog position; card-only objects have no line at all. When
     * neither event converges, the current altitude tells circumpolar from never-up.
     */
    private fun riseSetFor(info: ObjectInfo): RiseSetState? {
        val after = now()
        val observer = location()
        val body =
            SolarSystemIds.bodyFor(info.id)
                ?: info.parent?.let { SolarSystemIds.bodyFor(it) }
        val rise: Instant?
        val set: Instant?
        val positionNow: RaDec
        val horizonDeg: Double
        if (body != null) {
            rise = nextRiseSetTime(body, after, observer, RiseSetIndicator.RISE, ephemeris)
            set = nextRiseSetTime(body, after, observer, RiseSetIndicator.SET, ephemeris)
            positionNow = ephemeris.topocentricPosition(body, after, observer)
            horizonDeg = horizonAltitudeDeg(body)
        } else {
            val fixed = info.position ?: return null
            rise = nextRiseSetTime({ fixed }, after, observer, RiseSetIndicator.RISE)
            set = nextRiseSetTime({ fixed }, after, observer, RiseSetIndicator.SET)
            positionNow = fixed
            horizonDeg = 0.0
        }
        if (rise != null || set != null) return RiseSetState.Times(rise, set)
        return if (altitudeDeg(positionNow, after, observer) > horizonDeg) {
            RiseSetState.AlwaysAbove
        } else {
            RiseSetState.AlwaysBelow
        }
    }

    /**
     * The card as a [SearchHit], for the Find action: a see-also chip carries exactly a search
     * hit's payload so the card can re-aim the search arrow exactly like a search (D33); the
     * position-less solar-system rows resolve through the ephemeris in `SearchViewModel`.
     */
    fun asSearchHit(info: ObjectInfo): SearchHit =
        SearchHit(
            id = info.id,
            name = info.name,
            subtext = info.searchSubtext,
            position = info.position,
            searchFovDeg = info.searchFovDeg,
        )

    /** Whether Find can resolve a direction: an own/parent position or an ephemeris body. */
    fun isFindable(info: ObjectInfo): Boolean =
        info.position != null ||
            SolarSystemIds.bodyFor(info.id) != null ||
            info.parent?.let { SolarSystemIds.bodyFor(it) } != null

    private data class TapCandidate(val id: CelestialObjectId, val direction: Vector3)

    /**
     * v1's `supportedObjectIds` resolved against the live sky: curated (carded) objects from
     * each *enabled* catalog layer at their J2000 positions, plus every ephemeris body at its
     * position now when the solar-system layer is on, plus the meteor-shower radiants that are
     * *currently active* — an out-of-season radiant isn't drawn, so tapping its patch of sky must
     * find nothing. Hidden layers aren't tappable — you can't identify what isn't drawn. The
     * per-layer DB reads, the settings gates, and the carded-id lookup are independent I/O, so
     * they run concurrently rather than queued one after another.
     */
    private suspend fun candidates(): List<TapCandidate> =
        coroutineScope {
            val repo = catalog()
            val cardedDeferred = async { repo.infoCardObjectIds() }
            val layerDeferreds =
                CATALOG_LAYER_KINDS.map { (layerId, kind) ->
                    async {
                        if (!settings.layerEnabled(layerId).first()) {
                            emptyList()
                        } else {
                            repo.layerObjects(kind, locale.value).first()
                        }
                    }
                }
            val showerDeferred =
                async {
                    if (!settings.layerEnabled(MeteorShowerLayer.LAYER_ID).first()) {
                        emptyList()
                    } else {
                        val today = utcMonthDay(now())
                        repo.meteorShowers(locale.value).first().filter { it.isActiveOn(today) }
                    }
                }
            val solarSystemDeferred =
                async {
                    if (!settings.layerEnabled(SolarSystemLayer.LAYER_ID).first()) {
                        emptyList()
                    } else {
                        val time = now()
                        // Topocentric (D54) so the tap target sits where the layer draws it.
                        val observer = location()
                        SolarSystemBody.entries.filter { it != SolarSystemBody.EARTH }.map { body ->
                            TapCandidate(
                                SolarSystemIds.idFor(body),
                                ephemeris
                                    .topocentricPosition(body, time, observer)
                                    .toGeocentricVector(),
                            )
                        }
                    }
                }
            val carded = cardedDeferred.await()
            val catalogCandidates =
                layerDeferreds.awaitAll().flatten()
                    .filter { it.id in carded }
                    .map { TapCandidate(it.id, it.position.toGeocentricVector()) }
            val showerCandidates =
                showerDeferred.await()
                    .filter { it.id in carded }
                    .map { TapCandidate(it.id, it.radiant.toGeocentricVector()) }
            val satelliteCandidates =
                satellites().map { TapCandidate(it.info.id, it.position.toGeocentricVector()) }
            catalogCandidates + showerCandidates + solarSystemDeferred.await() +
                satelliteCandidates
        }

    /**
     * [id]'s card content in the current locale. Satellites are not in the bundled catalog —
     * they arrive from the network — so their card is synthesized rather than looked up. See
     * [SatelliteIds].
     */
    private suspend fun cardFor(id: CelestialObjectId): ObjectInfo? =
        SatelliteIds.noradIdFor(id)?.let { norad ->
            satellites().firstOrNull { it.info.id == id }?.info
        } ?: catalog().objectInfo(id, locale.value)

    // A language switch recreates the activity but not this view model, so a card left open
    // across one would keep the language it was opened in: look it up again in the new locale.
    // Last in the class body so every property it touches is initialized before it runs.
    init {
        viewModelScope.launch {
            locale.drop(1).collect {
                val open = _card.value ?: return@collect
                // The re-read suspends, so the user may have dismissed the card or opened a
                // different one meanwhile; reopening then would resurrect what they closed.
                cardFor(open.id)?.let { if (_card.value?.id == open.id) openCard(it) }
            }
        }
    }

    private companion object {
        val MOON_ID: CelestialObjectId = SolarSystemIds.idFor(SolarSystemBody.MOON)
        val CATALOG_LAYER_KINDS: List<Pair<LayerId, LayerKind>> =
            listOf(
                CatalogLayers.STARS_LAYER_ID to LayerKind.STARS,
                CatalogLayers.DEEP_SKY_LAYER_ID to LayerKind.DEEP_SKY,
                CatalogLayers.CONSTELLATIONS_LAYER_ID to LayerKind.CONSTELLATIONS,
            )
    }
}
