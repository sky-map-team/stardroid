/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.search

import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.FakeAnalytics
import com.google.android.stardroid.astronomy.KeplerianEphemeris
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.satellites.SatelliteIds
import com.google.android.stardroid.satellites.TrackedSatellite
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val repository = FakeSearchRepository()
    private val settings = FakeSettings()

    private class FakeSearchRepository : CatalogRepository {
        var hits = listOf<SearchHit>()
        val infos = mutableMapOf<CelestialObjectId, ObjectInfo>()
        var lastPrefix: String? = null
        var searchCount = 0

        /** Overrides [hits] when set, so a test can vary the results by locale. */
        var hitsFor: ((LocaleSpec) -> List<SearchHit>)? = null

        /** Set to simulate a Room/SQLite failure (issue #1003) instead of returning [hits]. */
        var throwOnSearch: (() -> Throwable)? = null

        override fun layerObjects(
            kind: LayerKind,
            locale: LocaleSpec,
        ): Flow<List<CatalogObject>> = emptyFlow()

        override fun figures(
            kind: LayerKind,
            culture: String,
        ): Flow<List<Figure>> = emptyFlow()

        override suspend fun searchByPrefix(
            prefix: String,
            locale: LocaleSpec,
            limit: Int,
        ): List<SearchHit> {
            lastPrefix = prefix
            searchCount++
            throwOnSearch?.let { throw it() }
            return (hitsFor?.invoke(locale) ?: hits)
                .filter { it.name.startsWith(prefix, ignoreCase = true) }
        }

        override suspend fun objectInfo(
            id: CelestialObjectId,
            locale: LocaleSpec,
        ): ObjectInfo? = infos[id]

        override suspend fun infoCardObjectIds(): Set<CelestialObjectId> = infos.keys

        override fun meteorShowers(locale: LocaleSpec): Flow<List<MeteorShower>> = emptyFlow()

        override suspend fun galleryItems(locale: LocaleSpec): List<GalleryItem> = emptyList()
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val createdViewModels = mutableListOf<SearchViewModel>()

    private val analytics = FakeAnalytics()

    private var manualMode = false

    private var trackedSatellites = listOf<TrackedSatellite>()

    private var aliases = mapOf(25544 to listOf("International Space Station"))

    private var satelliteFailure: (() -> Throwable)? = null

    private val locale = MutableStateFlow(LocaleSpec("en"))

    private fun viewModel(): SearchViewModel =
        SearchViewModel(
            catalog = { repository },
            locale = locale,
            ephemeris = KeplerianEphemeris,
            now = { NOW },
            settings = settings,
            analytics = analytics,
            isManualMode = { manualMode },
            satelliteAliases = { aliases[it].orEmpty() },
            satellites = {
                satelliteFailure?.let { throw it() }
                trackedSatellites
            },
        ).also { createdViewModels += it }

    @Test
    fun `suggestions track the query`() =
        testScope.runCurrentTest {
            repository.hits = listOf(SIRIUS_HIT, JUPITER_HIT)
            val vm = viewModel()
            backgroundScope.launch { vm.suggestions.collect {} }
            runCurrent()

            vm.setQuery("sir")
            advanceTimeBy(debounceSettle)
            runCurrent()
            assertThat(vm.suggestions.value).containsExactly(SIRIUS_HIT)

            // Clearing skips the debounce: emptying the field blanks the list at once.
            vm.setQuery("")
            runCurrent()
            assertThat(vm.suggestions.value).isEmpty()
        }

    @Test
    fun `suggestions re-run in the new language when the app language changes`() =
        testScope.runCurrentTest {
            val spanish = SIRIUS_HIT.copy(name = "Sirio")
            repository.hitsFor = { if (it.tag == "es") listOf(spanish) else listOf(SIRIUS_HIT) }
            val vm = viewModel()
            backgroundScope.launch { vm.suggestions.collect {} }
            runCurrent()

            vm.setQuery("sir")
            advanceTimeBy(debounceSettle)
            runCurrent()
            assertThat(vm.suggestions.value).containsExactly(SIRIUS_HIT)

            // The view model outlives the activity recreation a language switch triggers.
            locale.value = LocaleSpec("es")
            advanceTimeBy(debounceSettle)
            runCurrent()

            assertThat(vm.suggestions.value).containsExactly(spanish)
        }

    @Test
    fun `typing a word issues one search, not one per keystroke`() =
        testScope.runCurrentTest {
            repository.hits = listOf(SIRIUS_HIT, JUPITER_HIT)
            val vm = viewModel()
            backgroundScope.launch { vm.suggestions.collect {} }
            runCurrent()
            repository.searchCount = 0

            for (prefix in listOf("s", "si", "sir", "siri", "siriu", "sirius")) {
                vm.setQuery(prefix)
                // Faster than the debounce window, i.e. an ordinary typing cadence.
                advanceTimeBy(40)
                runCurrent()
            }
            assertThat(repository.searchCount).isEqualTo(0)

            advanceTimeBy(debounceSettle)
            runCurrent()
            assertThat(repository.searchCount).isEqualTo(1)
            assertThat(repository.lastPrefix).isEqualTo("sirius")
            assertThat(vm.suggestions.value).containsExactly(SIRIUS_HIT)
        }

    @Test
    fun `selecting a positioned hit snapshots its direction and search FOV`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.select(SIRIUS_HIT)
            runCurrent()

            val target = vm.target.value!!
            assertThat(target.name).isEqualTo("Sirius")
            assertThat(
                target.direction.distanceTo(SIRIUS_POSITION.toGeocentricVector()),
            ).isLessThan(TOL)
            assertThat(target.fovDeg).isEqualTo(20.0)
        }

    @Test
    fun `satellites are found by name, word and alias even though they are not in the catalog`() =
        testScope.runCurrentTest {
            trackedSatellites = listOf(ISS)
            val vm = viewModel()
            backgroundScope.launch { vm.suggestions.collect {} }
            runCurrent()

            for (query in listOf("iss", "ISS (Z", "zarya", "international sp", "space stat")) {
                vm.setQuery(query)
                advanceTimeBy(debounceSettle)
                runCurrent()
                assertThat(vm.suggestions.value.map { it.name }).containsExactly("ISS (ZARYA)")
            }

            vm.setQuery("tian")
            advanceTimeBy(debounceSettle)
            runCurrent()
            assertThat(vm.suggestions.value).isEmpty()
        }

    @Test
    fun `selecting a satellite aims at its fresh position and re-enables its layer`() =
        testScope.runCurrentTest {
            trackedSatellites = listOf(ISS)
            settings.setLayerEnabled(SatelliteLayer.LAYER_ID, enabled = false)
            val vm = viewModel()
            val stale = ISS_HIT.copy(position = RaDec(0.0, 0.0))
            vm.select(stale)
            runCurrent()

            val target = vm.target.value!!
            assertThat(target.name).isEqualTo("ISS (ZARYA)")
            assertThat(
                target.direction.distanceTo(ISS_POSITION.toGeocentricVector()),
            ).isLessThan(TOL)
            assertThat(settings.layerEnabled(SatelliteLayer.LAYER_ID).first()).isTrue()
        }

    @Test
    fun `selecting a satellite that is no longer tracked falls back to the hit position`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.select(ISS_HIT)
            runCurrent()

            val target = vm.target.value!!
            assertThat(
                target.direction.distanceTo(ISS_POSITION.toGeocentricVector()),
            ).isLessThan(TOL)
        }

    @Test
    fun `a failing satellite lookup does not blank catalog results or crash select`() =
        testScope.runCurrentTest {
            repository.hits = listOf(ISS_LIKE_HIT)
            val vm = viewModel()
            backgroundScope.launch { vm.suggestions.collect {} }
            runCurrent()
            satelliteFailure = { IllegalStateException("boom") }

            vm.setQuery("iss")
            advanceTimeBy(debounceSettle)
            runCurrent()
            assertThat(vm.suggestions.value).containsExactly(ISS_LIKE_HIT)

            vm.select(ISS_HIT)
            runCurrent()
            assertThat(vm.target.value).isNotNull()
        }

    @Test
    fun `submitting a satellite name selects the sole hit`() =
        testScope.runCurrentTest {
            trackedSatellites = listOf(ISS)
            val vm = viewModel()
            vm.setQuery("iss")
            vm.submit()
            runCurrent()

            assertThat(vm.target.value?.name).isEqualTo("ISS (ZARYA)")
        }

    @Test
    fun `satellite aliases come from the injected, localized provider`() =
        testScope.runCurrentTest {
            trackedSatellites = listOf(ISS)
            aliases = mapOf(25544 to listOf("Estación Espacial Internacional"))
            val vm = viewModel()
            backgroundScope.launch { vm.suggestions.collect {} }
            runCurrent()

            vm.setQuery("estación esp")
            advanceTimeBy(debounceSettle)
            runCurrent()
            assertThat(vm.suggestions.value.map { it.name }).containsExactly("ISS (ZARYA)")

            vm.setQuery("international")
            advanceTimeBy(debounceSettle)
            runCurrent()
            assertThat(vm.suggestions.value).isEmpty()
        }

    @Test
    fun `a separator-only query matches no satellite`() {
        for (q in listOf("(", ")", "-", "( )")) {
            assertThat(SearchViewModel.matchesWordPrefix("ISS (ZARYA)", q)).isFalse()
        }
    }

    @Test
    fun `an engaged search logs the query and the lock with the frame mode`() =
        testScope.runCurrentTest {
            repository.hits = listOf(SIRIUS_HIT)
            manualMode = true
            val vm = viewModel()
            vm.setQuery("sir")
            vm.select(SIRIUS_HIT)
            runCurrent()

            assertThat(analytics.eventNames())
                .containsExactly(
                    AnalyticsEvents.SEARCH_EVENT,
                    AnalyticsEvents.OBJECT_LOCKED_EVENT,
                ).inOrder()
            assertThat(analytics.events[0].params)
                .containsEntry(AnalyticsEvents.SEARCH_TERM, "sir")
            assertThat(analytics.events[1].params)
                .containsEntry(
                    AnalyticsEvents.OBJECT_LOCKED_MODE,
                    AnalyticsEvents.OBJECT_LOCKED_MODE_MANUAL,
                )
        }

    @Test
    fun `a submit with no hits logs the failure`() =
        testScope.runCurrentTest {
            repository.hits = emptyList()
            val vm = viewModel()
            vm.setQuery("xyzzy")
            vm.submit()
            runCurrent()

            assertThat(vm.noResults.value).isTrue()
            assertThat(analytics.eventNames())
                .containsExactly(AnalyticsEvents.SEARCH_FAILED_EVENT)
            assertThat(analytics.events[0].params)
                .containsEntry(AnalyticsEvents.SEARCH_TERM, "xyzzy")
        }

    @Test
    fun `a catalog exception while typing is caught, not thrown`() =
        testScope.runCurrentTest {
            repository.throwOnSearch = { IllegalStateException("boom") }
            val vm = viewModel()
            backgroundScope.launch { vm.suggestions.collect {} }
            runCurrent()

            vm.setQuery("sir")
            advanceTimeBy(debounceSettle)
            runCurrent()

            assertThat(vm.suggestions.value).isEmpty()
            assertThat(analytics.eventNames())
                .containsExactly(AnalyticsEvents.SEARCH_QUERY_ERROR_EVENT)
            assertThat(analytics.events[0].params)
                .containsEntry(AnalyticsEvents.SEARCH_QUERY_ERROR_TYPE, "IllegalStateException")
        }

    @Test
    fun `a catalog exception on submit reports no results instead of crashing`() =
        testScope.runCurrentTest {
            repository.throwOnSearch = { IllegalStateException("boom") }
            val vm = viewModel()
            vm.setQuery("sir")
            vm.submit()
            runCurrent()

            assertThat(vm.target.value).isNull()
            assertThat(vm.noResults.value).isTrue()
            // The caught exception yields an empty hit list, which submit() then reports as an
            // ordinary no-results failure on top of the error event — the user sees one dialog,
            // not a crash, but two signals reach analytics.
            assertThat(analytics.eventNames())
                .containsExactly(
                    AnalyticsEvents.SEARCH_QUERY_ERROR_EVENT,
                    AnalyticsEvents.SEARCH_FAILED_EVENT,
                ).inOrder()
        }

    @Test
    fun `a planet hit resolves through the ephemeris at the shared clock`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.select(JUPITER_HIT)
            runCurrent()

            val expected =
                KeplerianEphemeris
                    .geocentricPosition(SolarSystemBody.JUPITER, NOW)
                    .toGeocentricVector()
            assertThat(vm.target.value!!.direction.distanceTo(expected)).isLessThan(TOL)
        }

    @Test
    fun `a card-only moon resolves to its parent planet's position`() =
        testScope.runCurrentTest {
            repository.infos[IO_HIT.id] =
                objectInfo(IO_HIT.id, parent = CelestialObjectId("planet/jupiter"))
            val vm = viewModel()
            vm.select(IO_HIT)
            runCurrent()

            val expected =
                KeplerianEphemeris
                    .geocentricPosition(SolarSystemBody.JUPITER, NOW)
                    .toGeocentricVector()
            assertThat(vm.target.value!!.name).isEqualTo("Io")
            assertThat(vm.target.value!!.direction.distanceTo(expected)).isLessThan(TOL)
        }

    @Test
    fun `an unresolvable hit reports no results instead of a target`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.select(IO_HIT)
            runCurrent()

            assertThat(vm.target.value).isNull()
            assertThat(vm.noResults.value).isTrue()
        }

    @Test
    fun `submitting coordinates targets them directly`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.setQuery("12h 30m, +45")
            vm.submit()
            runCurrent()

            val target = vm.target.value!!
            assertThat(target.name).isEqualTo("12h 30m, +45.0°")
            assertThat(
                target.direction.distanceTo(RaDec(187.5, 45.0).toGeocentricVector()),
            ).isLessThan(TOL)
            assertThat(target.fovDeg).isNull()
        }

    @Test
    fun `submitting an exact name wins over other prefix hits`() =
        testScope.runCurrentTest {
            val mizar =
                SearchHit(CelestialObjectId("star/mizar"), "Mizar", null, SIRIUS_POSITION, null)
            val mizarB =
                SearchHit(
                    CelestialObjectId("star/mizar_b"),
                    "Mizar B",
                    null,
                    SIRIUS_POSITION,
                    null,
                )
            repository.hits = listOf(mizarB, mizar)
            val vm = viewModel()
            vm.setQuery("mizar")
            vm.submit()
            runCurrent()

            assertThat(vm.target.value!!.name).isEqualTo("Mizar")
        }

    @Test
    fun `submitting an unknown name reports no results, cleared by typing`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.setQuery("xyzzy")
            vm.submit()
            runCurrent()
            assertThat(vm.noResults.value).isTrue()
            assertThat(vm.target.value).isNull()

            vm.setQuery("xyzz")
            assertThat(vm.noResults.value).isFalse()
        }

    @Test
    fun `several inexact hits leave the list standing without a target`() =
        testScope.runCurrentTest {
            val one =
                SearchHit(CelestialObjectId("star/a"), "Alpha One", null, SIRIUS_POSITION, null)
            val two =
                SearchHit(CelestialObjectId("star/b"), "Alpha Two", null, SIRIUS_POSITION, null)
            repository.hits = listOf(one, two)
            val vm = viewModel()
            vm.setQuery("alpha")
            vm.submit()
            runCurrent()

            assertThat(vm.target.value).isNull()
            assertThat(vm.noResults.value).isFalse()
        }

    @Test
    fun `selectById aims at the catalog card's object like a search`() =
        testScope.runCurrentTest {
            // The time-travel presets' path: a stable id, no user query. The position-less
            // planet row resolves through the ephemeris.
            val sunId = CelestialObjectId("planet/sun")
            repository.infos[sunId] = objectInfo(sunId, parent = null, name = "Sun")
            val vm = viewModel()
            vm.selectById(sunId)
            runCurrent()

            val target = vm.target.value!!
            assertThat(target.name).isEqualTo("Sun")
            val expected =
                KeplerianEphemeris
                    .geocentricPosition(SolarSystemBody.SUN, NOW)
                    .toGeocentricVector()
            assertThat(target.direction.distanceTo(expected)).isLessThan(TOL)
        }

    @Test
    fun `selectById with an unknown id stays quiet`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.selectById(CelestialObjectId("planet/vulcan"))
            runCurrent()

            assertThat(vm.target.value).isNull()
            assertThat(vm.noResults.value).isFalse()
        }

    @Test
    fun `selecting a hit re-enables its hidden layer`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_HIT.id] =
                objectInfo(SIRIUS_HIT.id, parent = null, layerKind = LayerKind.STARS)
            settings.setLayerEnabled(CatalogLayers.STARS_LAYER_ID, enabled = false)
            val vm = viewModel()
            vm.select(SIRIUS_HIT)
            runCurrent()

            assertThat(vm.target.value).isNotNull()
            assertThat(settings.layerEnabled(CatalogLayers.STARS_LAYER_ID).first()).isTrue()
        }

    @Test
    fun `selecting a planet re-enables the solar-system layer`() =
        testScope.runCurrentTest {
            settings.setLayerEnabled(SolarSystemLayer.LAYER_ID, enabled = false)
            val vm = viewModel()
            vm.select(JUPITER_HIT)
            runCurrent()

            assertThat(vm.target.value).isNotNull()
            assertThat(settings.layerEnabled(SolarSystemLayer.LAYER_ID).first()).isTrue()
        }

    @Test
    fun `a coordinate target leaves layer toggles alone`() =
        testScope.runCurrentTest {
            settings.setLayerEnabled(CatalogLayers.STARS_LAYER_ID, enabled = false)
            val vm = viewModel()
            vm.setQuery("12h 30m, +45")
            vm.submit()
            runCurrent()

            assertThat(vm.target.value).isNotNull()
            assertThat(settings.layerEnabled(CatalogLayers.STARS_LAYER_ID).first()).isFalse()
        }

    @Test
    fun `cancel ends search mode`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.select(SIRIUS_HIT)
            runCurrent()
            assertThat(vm.target.value).isNotNull()

            vm.cancelSearch()
            assertThat(vm.target.value).isNull()
        }

    /** Comfortably past SearchViewModel's 150 ms debounce window. */
    private val debounceSettle = 200L

    private fun TestScope.runCurrentTest(body: suspend TestScope.() -> Unit) =
        runTest {
            try {
                body()
            } finally {
                createdViewModels.forEach { it.viewModelScope.cancel() }
            }
        }

    private companion object {
        val NOW = Instant.parse("2026-07-03T21:00:00Z")
        const val TOL = 1e-9

        val SIRIUS_POSITION = RaDec(101.287, -16.716)
        val SIRIUS_HIT =
            SearchHit(CelestialObjectId("star/sirius"), "Sirius", "Star", SIRIUS_POSITION, 20.0)
        val JUPITER_HIT =
            SearchHit(CelestialObjectId("planet/jupiter"), "Jupiter", "Planet", null, 15.0)
        val ISS_POSITION = RaDec(210.0, 35.0)
        val ISS_TLE =
            Tle.parse(
                line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
                line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
                name = "ISS (ZARYA)",
            )
        val ISS =
            TrackedSatellite(
                tle = ISS_TLE,
                info = SatelliteIds.cardFor(ISS_TLE, ISS_POSITION, null),
                position = ISS_POSITION,
            )
        val ISS_HIT =
            SearchHit(SatelliteIds.idFor(25544), "ISS (ZARYA)", null, ISS_POSITION, null)
        val ISS_LIKE_HIT =
            SearchHit(CelestialObjectId("star/issa"), "Issa", "Star", SIRIUS_POSITION, 20.0)
        val IO_HIT = SearchHit(CelestialObjectId("moon/io"), "Io", "Orbits Jupiter", null, null)

        fun objectInfo(
            id: CelestialObjectId,
            parent: CelestialObjectId?,
            name: String = "Io",
            layerKind: LayerKind? = null,
        ) = ObjectInfo(
            id = id,
            name = name,
            type = TypeCode("moon"),
            layerKind = layerKind,
            position = null,
            parent = parent,
            magnitude = null,
            description = null,
            funFact = null,
            distance = null,
            size = null,
            mass = null,
            spectralClass = null,
            imageRef = null,
            imageCredit = null,
            searchSubtext = null,
        )
    }
}
