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
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.SolarSystemLayer
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
