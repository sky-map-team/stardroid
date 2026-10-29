/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.astronomy.KeplerianEphemeris
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.astronomy.dayOfCycle
import com.google.android.stardroid.astronomy.illuminationPercent
import com.google.android.stardroid.astronomy.moonWidgetModel
import com.google.android.stardroid.astronomy.nextLunarPhaseEvent
import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.satellites.SatelliteIds
import com.google.android.stardroid.satellites.TrackedSatellite
import com.google.android.stardroid.settings.FakeSettings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.testing.assertThat
import com.google.android.stardroid.ui.search.SolarSystemIds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObjectInfoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val repository = FakeObjectInfoRepository()
    private val settings = FakeSettings()

    private class FakeObjectInfoRepository : CatalogRepository {
        val objectsByKind =
            LayerKind.entries.associateWith { MutableStateFlow<List<CatalogObject>>(emptyList()) }
        val infos = mutableMapOf<CelestialObjectId, ObjectInfo>()
        var lastInfoLocale: LocaleSpec? = null

        /** Set to hold [objectInfo] mid-read, so a test can interleave a call against it. */
        var infoGate: CompletableDeferred<Unit>? = null
        val showers = MutableStateFlow<List<MeteorShower>>(emptyList())

        override fun layerObjects(
            kind: LayerKind,
            locale: LocaleSpec,
        ): Flow<List<CatalogObject>> = objectsByKind.getValue(kind)

        override fun figures(
            kind: LayerKind,
            culture: String,
        ): Flow<List<Figure>> = emptyFlow()

        override suspend fun searchByPrefix(
            prefix: String,
            locale: LocaleSpec,
            limit: Int,
        ): List<SearchHit> = emptyList()

        override suspend fun objectInfo(
            id: CelestialObjectId,
            locale: LocaleSpec,
        ): ObjectInfo? {
            lastInfoLocale = locale
            infoGate?.await()
            return infos[id]
        }

        override suspend fun infoCardObjectIds(): Set<CelestialObjectId> = infos.keys

        override fun meteorShowers(locale: LocaleSpec): Flow<List<MeteorShower>> = showers

        override suspend fun galleryItems(locale: LocaleSpec): List<GalleryItem> = emptyList()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val createdViewModels = mutableListOf<ObjectInfoViewModel>()

    private val locale = MutableStateFlow(LocaleSpec("en"))

    private fun viewModel(
        experimentConfig: ExperimentConfig = ExperimentConfig.Static,
        moonWidgetPlaced: () -> Boolean = { false },
        satellites: List<TrackedSatellite> = emptyList(),
        upcomingPasses: List<SatellitePass> = emptyList(),
        passTimesReliable: Boolean = true,
        passSearch: (suspend (Int) -> List<SatellitePass>)? = null,
    ): ObjectInfoViewModel =
        ObjectInfoViewModel(
            catalog = { repository },
            locale = locale,
            ephemeris = KeplerianEphemeris,
            now = { NOW },
            settings = settings,
            location = { LONDON },
            computeContext = UnconfinedTestDispatcher(dispatcher.scheduler),
            experimentConfig = experimentConfig,
            moonWidgetPlaced = moonWidgetPlaced,
            satellites = { satellites },
            upcomingPasses = passSearch ?: { upcomingPasses },
            passTimesReliable = { passTimesReliable },
        ).also { createdViewModels += it }

    private fun ObjectInfoViewModel.riseSetState(): RiseSetState? =
        live.value?.now?.filterIsInstance<CardFact.RiseSet>()?.firstOrNull()?.state

    private fun pass() =
        SatellitePass(
            satelliteName = "ISS",
            noradId = 25544,
            start = NOW,
            culmination = NOW,
            end = NOW,
            maxAltitudeDeg = 60.0,
            startAzimuthDeg = 0.0,
            endAzimuthDeg = 0.0,
            peakMagnitude = -2.0,
            shadowEntry = null,
        )

    @Test
    fun `show opens the card for an id and dismiss closes it`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            val vm = viewModel()

            vm.show(SIRIUS_ID)
            runCurrent()
            assertThat(vm.card.value?.name).isEqualTo("Sirius")

            vm.dismiss()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `show looks the card up in the language the app is in now`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            val vm = viewModel()
            vm.show(SIRIUS_ID)
            runCurrent()

            // A language switch recreates the activity but not the view model, so a captured
            // locale would keep every later card in the language the app started in.
            locale.value = LocaleSpec("es")
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirio")
            vm.dismiss()
            vm.show(SIRIUS_ID)
            runCurrent()

            assertThat(repository.lastInfoLocale).isEqualTo(LocaleSpec("es"))
            assertThat(vm.card.value?.name).isEqualTo("Sirio")
        }

    @Test
    fun `an open card re-reads itself when the app language changes`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            val vm = viewModel()
            vm.show(SIRIUS_ID)
            runCurrent()

            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirio")
            locale.value = LocaleSpec("es")
            runCurrent()

            assertThat(vm.card.value?.name).isEqualTo("Sirio")
        }

    @Test
    fun `a card dismissed while a language change re-reads it stays dismissed`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            val vm = viewModel()
            vm.show(SIRIUS_ID)
            runCurrent()

            val gate = CompletableDeferred<Unit>()
            repository.infoGate = gate
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirio")
            locale.value = LocaleSpec("es")
            runCurrent()

            // The re-read is suspended in the catalog; the user closes the card before it lands.
            vm.dismiss()
            repository.infoGate = null
            gate.complete(Unit)
            runCurrent()

            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `a language change with no card open opens nothing`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            val vm = viewModel()

            locale.value = LocaleSpec("es")
            runCurrent()

            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `show keeps the current card when the id is unknown`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            val vm = viewModel()
            vm.show(SIRIUS_ID)
            runCurrent()

            vm.show(CelestialObjectId("star/unknown"))
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(SIRIUS_ID)
        }

    @Test
    fun `a tap on a carded star opens its card`() =
        testScope.runCurrentTest {
            givenCardedStarAtLookDirection()
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(SIRIUS_ID)
        }

    @Test
    fun `a tap beyond the threshold finds nothing`() =
        testScope.runCurrentTest {
            // 10° off the look direction; at 60° FOV the tolerance is 5·60/90 ≈ 3.3°.
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            repository.objectsByKind.getValue(LayerKind.STARS).value =
                listOf(catalogObject(SIRIUS_ID, RaDec(LOOK_RA + 10.0, LOOK_DEC)))
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `the nearest of several candidates wins`() =
        testScope.runCurrentTest {
            val rival = CelestialObjectId("star/rival")
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            repository.infos[rival] = objectInfo(rival, "Rival")
            repository.objectsByKind.getValue(LayerKind.STARS).value =
                listOf(
                    catalogObject(rival, RaDec(LOOK_RA + 2.0, LOOK_DEC)),
                    catalogObject(SIRIUS_ID, RaDec(LOOK_RA + 0.5, LOOK_DEC)),
                )
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(SIRIUS_ID)
        }

    @Test
    fun `objects without a curated card are not tappable`() =
        testScope.runCurrentTest {
            // In the stars layer, dead ahead — but not in the carded set.
            repository.objectsByKind.getValue(LayerKind.STARS).value =
                listOf(catalogObject(SIRIUS_ID, RaDec(LOOK_RA, LOOK_DEC)))
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `objects on a hidden layer are not tappable`() =
        testScope.runCurrentTest {
            givenCardedStarAtLookDirection()
            settings.setLayerEnabled(CatalogLayers.STARS_LAYER_ID, false)
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `the master switch disables tap-to-identify`() =
        testScope.runCurrentTest {
            givenCardedStarAtLookDirection()
            settings.setTapToIdentify(false)
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `the sensor frame honors the auto-mode opt-out`() =
        testScope.runCurrentTest {
            givenCardedStarAtLookDirection()
            val vm = viewModel()

            settings.setTapToIdentifyInAutoMode(false)
            vm.tapAtCenter(sensorFrame = true)
            runCurrent()
            assertThat(vm.card.value).isNull()

            settings.setTapToIdentifyInAutoMode(true)
            vm.tapAtCenter(sensorFrame = true)
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(SIRIUS_ID)
        }

    @Test
    fun `solar-system bodies are tappable at their ephemeris positions`() =
        testScope.runCurrentTest {
            val jupiterId = CelestialObjectId("planet/jupiter")
            repository.infos[jupiterId] = objectInfo(jupiterId, "Jupiter")
            val jupiter =
                KeplerianEphemeris.geocentricPosition(SolarSystemBody.JUPITER, NOW)
            val vm = viewModel()

            vm.onSkyTap(
                xPx = WIDTH / 2f,
                yPx = HEIGHT / 2f,
                widthPx = WIDTH,
                heightPx = HEIGHT,
                camera = camera(jupiter),
                sensorFrame = false,
                densityDpPerPx = DENSITY,
            )
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(jupiterId)
        }

    @Test
    fun `an active meteor shower is tappable at its radiant`() =
        testScope.runCurrentTest {
            repository.infos[PERSEIDS_ID] = objectInfo(PERSEIDS_ID, "Perseids")
            repository.showers.value = listOf(activeShower())
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(PERSEIDS_ID)
        }

    @Test
    fun `an out-of-season shower is not tappable`() =
        testScope.runCurrentTest {
            // Same radiant, dead ahead — but `now` sits outside the activity window, so the
            // layer draws nothing there and the tap must find nothing.
            repository.infos[PERSEIDS_ID] = objectInfo(PERSEIDS_ID, "Perseids")
            repository.showers.value =
                listOf(
                    activeShower().copy(
                        activeFrom = MonthDay(11, 1),
                        peak = MonthDay(11, 17),
                        activeTo = MonthDay(11, 30),
                    ),
                )
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `showers on a hidden layer are not tappable`() =
        testScope.runCurrentTest {
            repository.infos[PERSEIDS_ID] = objectInfo(PERSEIDS_ID, "Perseids")
            repository.showers.value = listOf(activeShower())
            settings.setLayerEnabled(MeteorShowerLayer.LAYER_ID, false)
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `a shower without a curated card is not tappable`() =
        testScope.runCurrentTest {
            // Active and dead ahead, but absent from the carded set.
            repository.showers.value = listOf(activeShower())
            val vm = viewModel()

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `a hidden solar-system layer hides its bodies from taps`() =
        testScope.runCurrentTest {
            val jupiterId = CelestialObjectId("planet/jupiter")
            repository.infos[jupiterId] = objectInfo(jupiterId, "Jupiter")
            settings.setLayerEnabled(SolarSystemLayer.LAYER_ID, false)
            val jupiter =
                KeplerianEphemeris.geocentricPosition(SolarSystemBody.JUPITER, NOW)
            val vm = viewModel()

            vm.onSkyTap(
                xPx = WIDTH / 2f,
                yPx = HEIGHT / 2f,
                widthPx = WIDTH,
                heightPx = HEIGHT,
                camera = camera(jupiter),
                sensorFrame = false,
                densityDpPerPx = DENSITY,
            )
            runCurrent()
            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `a satellite is tappable and opens its synthesized card`() =
        testScope.runCurrentTest {
            // No catalog row: the card exists only as the tracked satellite's own.
            val vm = viewModel(satellites = listOf(issAt(RaDec(LOOK_RA, LOOK_DEC))))

            vm.tapAtCenter()
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(ISS_ID)
        }

    @Test
    fun `a satellite card takes its prose and image from its catalog row`() =
        testScope.runCurrentTest {
            repository.infos[ISS_ID] =
                objectInfo(ISS_ID, "").copy(
                    description = "The station",
                    imageRef = "spacecraft/nasa_iss.webp",
                )
            val vm = viewModel(satellites = listOf(issAt(RaDec(LOOK_RA, LOOK_DEC))))

            vm.show(ISS_ID)
            runCurrent()

            assertThat(vm.card.value?.name).isEqualTo("ISS (ZARYA)")
            assertThat(vm.card.value?.description).isEqualTo("The station")
            assertThat(vm.card.value?.imageRef).isEqualTo("spacecraft/nasa_iss.webp")
        }

    @Test
    fun `a satellite that is not currently tracked has no card despite its catalog row`() =
        testScope.runCurrentTest {
            repository.infos[ISS_ID] = objectInfo(ISS_ID, "")
            val vm = viewModel()

            vm.show(ISS_ID)
            runCurrent()

            assertThat(vm.card.value).isNull()
        }

    @Test
    fun `show opens a satellite card by its id as a widget pass row does`() =
        testScope.runCurrentTest {
            val vm = viewModel(satellites = listOf(issAt(RaDec(LOOK_RA, LOOK_DEC))))

            vm.show(ISS_ID)
            runCurrent()
            assertThat(vm.card.value?.name).isEqualTo("ISS (ZARYA)")
        }

    @Test
    fun `a satellite card has no rise-set line`() =
        testScope.runCurrentTest {
            // Its position is a moment's snapshot; solved as a fixed point it would give
            // confident nonsense times.
            val vm = viewModel(satellites = listOf(issAt(SIRIUS_POSITION)))

            vm.show(ISS_ID)
            runCurrent()
            assertThat(vm.card.value?.id).isEqualTo(ISS_ID)
            assertThat(vm.riseSetState()).isNull()
        }

    @Test
    fun `a fixed-position card carries the next rise and set after now`() =
        testScope.runCurrentTest {
            // Sirius from London crosses the horizon twice a day; both events land in the
            // day after `now`.
            repository.infos[SIRIUS_ID] =
                objectInfo(SIRIUS_ID, "Sirius").copy(position = SIRIUS_POSITION)
            val vm = viewModel()

            vm.show(SIRIUS_ID)
            runCurrent()
            val times = vm.riseSetState() as RiseSetState.Times
            assertThat(times.rise).isNotNull()
            assertThat(times.set).isNotNull()
            assertThat(times.rise).isGreaterThan(NOW)
            assertThat(times.set).isGreaterThan(NOW)
        }

    @Test
    fun `a solar-system card resolves rise and set through the ephemeris`() =
        testScope.runCurrentTest {
            // Position-less in the catalog (the ephemeris owns it), as the planet rows are.
            val jupiterId = CelestialObjectId("planet/jupiter")
            repository.infos[jupiterId] = objectInfo(jupiterId, "Jupiter")
            val vm = viewModel()

            vm.show(jupiterId)
            runCurrent()
            val times = vm.riseSetState() as RiseSetState.Times
            assertThat(times.rise).isNotNull()
            assertThat(times.set).isNotNull()
        }

    @Test
    fun `a moon rides its parent planet for rise and set`() =
        testScope.runCurrentTest {
            val ioId = CelestialObjectId("moon/io")
            repository.infos[ioId] =
                objectInfo(ioId, "Io").copy(parent = CelestialObjectId("planet/jupiter"))
            val vm = viewModel()

            vm.show(ioId)
            runCurrent()
            assertThat(vm.riseSetState()).isInstanceOf(RiseSetState.Times::class)
        }

    @Test
    fun `circumpolar and never-risen objects report the no-crossing states`() =
        testScope.runCurrentTest {
            val polarisId = CelestialObjectId("star/polaris")
            val acruxId = CelestialObjectId("star/acrux")
            repository.infos[polarisId] =
                objectInfo(polarisId, "Polaris").copy(position = RaDec(37.955, 89.264))
            repository.infos[acruxId] =
                objectInfo(acruxId, "Acrux").copy(position = RaDec(186.650, -63.099))
            val vm = viewModel()

            // From London: Polaris never sets, Acrux never rises.
            vm.show(polarisId)
            runCurrent()
            assertThat(vm.riseSetState()).isEqualTo(RiseSetState.AlwaysAbove)

            vm.show(acruxId)
            runCurrent()
            assertThat(vm.riseSetState()).isEqualTo(RiseSetState.AlwaysBelow)
        }

    @Test
    fun `a card-only object has no rise-set line and dismiss clears it`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] =
                objectInfo(SIRIUS_ID, "Sirius").copy(position = SIRIUS_POSITION)
            val shower = CelestialObjectId("shower/perseids")
            repository.infos[shower] = objectInfo(shower, "Perseids")
            val vm = viewModel()

            vm.show(SIRIUS_ID)
            runCurrent()
            assertThat(vm.riseSetState()).isNotNull()

            vm.show(shower)
            runCurrent()
            assertThat(vm.riseSetState()).isNull()

            vm.show(SIRIUS_ID)
            runCurrent()
            vm.dismiss()
            assertThat(vm.riseSetState()).isNull()
        }

    @Test
    fun `a satellite card lists its upcoming passes`() =
        testScope.runCurrentTest {
            val passes = listOf(pass())
            val vm =
                viewModel(satellites = listOf(issAt(SIRIUS_POSITION)), upcomingPasses = passes)

            vm.show(ISS_ID)
            runCurrent()
            val upcoming = vm.live.value?.upcoming as Upcoming.Events
            assertThat(upcoming.events.map { (it as SkyEvent.SatellitePassTonight).pass })
                .isEqualTo(passes)
        }

    @Test
    fun `a satellite card with no passes says nothing is coming`() =
        testScope.runCurrentTest {
            val vm = viewModel(satellites = listOf(issAt(SIRIUS_POSITION)))

            vm.show(ISS_ID)
            runCurrent()
            assertThat(vm.live.value?.upcoming)
                .isEqualTo(Upcoming.NothingComing(UpcomingKind.SATELLITE_PASSES))
        }

    @Test
    fun `a satellite card with stale elements says the data is too old`() =
        testScope.runCurrentTest {
            val vm =
                viewModel(
                    satellites = listOf(issAt(SIRIUS_POSITION)),
                    upcomingPasses = listOf(pass()),
                    passTimesReliable = false,
                )

            vm.show(ISS_ID)
            runCurrent()
            assertThat(vm.live.value?.upcoming).isEqualTo(Upcoming.DataTooOld)
        }

    @Test
    fun `the Moon card lists the next full moon and new moon and eclipse`() =
        testScope.runCurrentTest {
            val moonId = SolarSystemIds.idFor(SolarSystemBody.MOON)
            repository.infos[moonId] = objectInfo(moonId, "Moon")
            val vm = viewModel()

            vm.show(moonId)
            runCurrent()
            val events = (vm.live.value?.upcoming as Upcoming.Events).events
            val phases = events.filterIsInstance<SkyEvent.MoonPhaseTonight>()
            assertThat(phases.map { it.phase }.toSet())
                .isEqualTo(setOf(LunarPhase.FULL, LunarPhase.NEW))
            phases.forEach {
                assertThat(it.time)
                    .isEqualTo(nextLunarPhaseEvent(it.phase, NOW, KeplerianEphemeris))
            }
            assertThat(events.filterIsInstance<SkyEvent.LunarEclipseUpcoming>().size)
                .isEqualTo(1)
        }

    @Test
    fun `the Moon card lists its upcoming events soonest first`() =
        testScope.runCurrentTest {
            val moonId = SolarSystemIds.idFor(SolarSystemBody.MOON)
            repository.infos[moonId] = objectInfo(moonId, "Moon")
            val vm = viewModel()

            vm.show(moonId)
            runCurrent()
            val times =
                (vm.live.value?.upcoming as Upcoming.Events).events.map {
                    if (it is SkyEvent.MoonPhaseTonight) {
                        it.time
                    } else {
                        (it as SkyEvent.LunarEclipseUpcoming).circumstances.greatestEclipse
                    }
                }
            assertThat(times).isEqualTo(times.sorted())
        }

    @Test
    fun `now facts are published before the upcoming search finishes`() =
        testScope.runCurrentTest {
            val gate = CompletableDeferred<List<SatellitePass>>()
            val vm =
                viewModel(
                    satellites = listOf(issAt(SIRIUS_POSITION)),
                    passSearch = { gate.await() },
                )

            vm.show(ISS_ID)
            runCurrent()
            assertThat(vm.live.value).isEqualTo(CardLive())

            gate.complete(listOf(pass()))
            runCurrent()
            assertThat(vm.live.value?.upcoming).isInstanceOf(Upcoming.Events::class)
        }

    @Test
    fun `a pass search finishing after a card switch does not write under the new card`() =
        testScope.runCurrentTest {
            val gate = CompletableDeferred<List<SatellitePass>>()
            repository.infos[SIRIUS_ID] =
                objectInfo(SIRIUS_ID, "Sirius").copy(position = SIRIUS_POSITION)
            // Ignores cancellation, as a non-suspending caller-supplied lambda would.
            val vm =
                viewModel(
                    satellites = listOf(issAt(SIRIUS_POSITION)),
                    passSearch = { withContext(NonCancellable) { gate.await() } },
                )

            vm.show(ISS_ID)
            runCurrent()
            vm.show(SIRIUS_ID)
            runCurrent()
            gate.complete(listOf(pass()))
            runCurrent()

            assertThat(vm.card.value?.id).isEqualTo(SIRIUS_ID)
            assertThat(vm.live.value?.upcoming).isNull()
        }

    @Test
    fun `the Moon card carries phase and illumination and age beside rise and set`() =
        testScope.runCurrentTest {
            val moonId = SolarSystemIds.idFor(SolarSystemBody.MOON)
            repository.infos[moonId] = objectInfo(moonId, "Moon")
            val vm = viewModel()

            vm.show(moonId)
            runCurrent()
            val expected = moonWidgetModel(NOW, location = null)
            val now = vm.live.value?.now.orEmpty()
            assertThat(now.filterIsInstance<CardFact.RiseSet>()).isNotEmpty()
            assertThat(now.filterIsInstance<CardFact.MoonPhase>())
                .isEqualTo(listOf(CardFact.MoonPhase(expected.phase, expected.illuminationPercent)))
            assertThat(now.filterIsInstance<CardFact.MoonAge>())
                .isEqualTo(listOf(CardFact.MoonAge(expected.dayOfCycle)))
        }

    @Test
    fun `only the Moon card carries Moon facts`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] =
                objectInfo(SIRIUS_ID, "Sirius").copy(position = SIRIUS_POSITION)
            val vm = viewModel()

            vm.show(SIRIUS_ID)
            runCurrent()
            val now = vm.live.value?.now.orEmpty()
            assertThat(now.filterIsInstance<CardFact.MoonAge>()).isEmpty()
            assertThat(now.filterIsInstance<CardFact.MoonPhase>()).isEmpty()
        }

    @Test
    fun `an ordinary card has no upcoming section`() =
        testScope.runCurrentTest {
            repository.infos[SIRIUS_ID] =
                objectInfo(SIRIUS_ID, "Sirius").copy(position = SIRIUS_POSITION)
            val vm = viewModel()

            vm.show(SIRIUS_ID)
            runCurrent()
            assertThat(vm.live.value?.upcoming).isNull()
        }

    @Test
    fun `asSearchHit carries the card's search payload`() {
        val info =
            objectInfo(SIRIUS_ID, "Sirius").copy(
                position = RaDec(101.287, -16.716),
                searchSubtext = "Star in Canis Major",
                searchFovDeg = 20.0,
            )
        val hit = viewModel().asSearchHit(info)
        assertThat(hit.id).isEqualTo(SIRIUS_ID)
        assertThat(hit.name).isEqualTo("Sirius")
        assertThat(hit.subtext).isEqualTo("Star in Canis Major")
        assertThat(hit.position).isEqualTo(RaDec(101.287, -16.716))
        assertThat(hit.searchFovDeg).isEqualTo(20.0)
    }

    @Test
    fun `findability needs a position or an ephemeris body`() {
        val vm = viewModel()
        val positioned = objectInfo(SIRIUS_ID, "Sirius").copy(position = RaDec(1.0, 2.0))
        val planet = objectInfo(CelestialObjectId("planet/neptune"), "Neptune")
        val moon =
            objectInfo(CelestialObjectId("moon/io"), "Io")
                .copy(parent = CelestialObjectId("planet/jupiter"))
        val shower = objectInfo(CelestialObjectId("shower/perseids"), "Perseids")

        assertThat(vm.isFindable(positioned)).isTrue()
        assertThat(vm.isFindable(planet)).isTrue()
        assertThat(vm.isFindable(moon)).isTrue()
        assertThat(vm.isFindable(shower)).isFalse()
    }

    private fun givenCardedStarAtLookDirection() {
        repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
        repository.objectsByKind.getValue(LayerKind.STARS).value =
            listOf(catalogObject(SIRIUS_ID, RaDec(LOOK_RA, LOOK_DEC)))
    }

    private fun ObjectInfoViewModel.tapAtCenter(sensorFrame: Boolean = false) =
        onSkyTap(
            xPx = WIDTH / 2f,
            yPx = HEIGHT / 2f,
            widthPx = WIDTH,
            heightPx = HEIGHT,
            camera = CAMERA,
            sensorFrame = sensorFrame,
            densityDpPerPx = DENSITY,
        )

    @Test
    fun `the moon card offers the widget until one is placed - then only quietly`() =
        testScope.runCurrentTest {
            repository.infos[MOON_ID] = objectInfo(MOON_ID, "Moon")
            val noWidget = viewModel(moonWidgetPlaced = { false })
            val hasWidget = viewModel(moonWidgetPlaced = { true })
            backgroundScope.launch { noWidget.moonWidgetPromo.collect {} }
            backgroundScope.launch { hasWidget.moonWidgetPromo.collect {} }

            noWidget.show(MOON_ID)
            hasWidget.show(MOON_ID)
            runCurrent()

            assertThat(noWidget.moonWidgetPromo.value).isEqualTo(MoonWidgetPromo.OFFER)
            assertThat(hasWidget.moonWidgetPromo.value).isEqualTo(MoonWidgetPromo.PLACED)
        }

    @Test
    fun `the widget row is hidden when no card is open`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            backgroundScope.launch { vm.moonWidgetPromo.collect {} }
            runCurrent()

            assertThat(vm.moonWidgetPromo.value).isEqualTo(MoonWidgetPromo.HIDDEN)
        }

    @Test
    fun `other cards and a disabled experiment show no widget row`() =
        testScope.runCurrentTest {
            repository.infos[MOON_ID] = objectInfo(MOON_ID, "Moon")
            repository.infos[SIRIUS_ID] = objectInfo(SIRIUS_ID, "Sirius")
            val flagOff = ExperimentConfig { it != Experiment.MOON_WIDGET }
            val onMoon = viewModel()
            val moonFlagOff = viewModel(experimentConfig = flagOff)
            backgroundScope.launch { onMoon.moonWidgetPromo.collect {} }
            backgroundScope.launch { moonFlagOff.moonWidgetPromo.collect {} }

            onMoon.show(SIRIUS_ID)
            moonFlagOff.show(MOON_ID)
            runCurrent()

            assertThat(onMoon.moonWidgetPromo.value).isEqualTo(MoonWidgetPromo.HIDDEN)
            assertThat(moonFlagOff.moonWidgetPromo.value).isEqualTo(MoonWidgetPromo.HIDDEN)
        }

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
        val LONDON = LatLong(51.51, -0.13)

        const val WIDTH = 1080
        const val HEIGHT = 2280

        /** A typical 3x phone, so the label-inclusive tap tolerance gets a realistic scale. */
        const val DENSITY = 3f

        const val LOOK_RA = 30.0
        const val LOOK_DEC = 10.0

        val MOON_ID = SolarSystemIds.idFor(SolarSystemBody.MOON)
        val SIRIUS_ID = CelestialObjectId("star/sirius")
        val SIRIUS_POSITION = RaDec(101.287, -16.716)

        val PERSEIDS_ID = CelestialObjectId("shower/perseids")

        val ISS_ID = SatelliteIds.idFor(25544)
        val ISS_TLE =
            Tle.parse(
                line1 = "1 25544U 98067A   26227.08368470  .00004985  00000+0  97076-4 0  9993",
                line2 = "2 25544  51.6331   8.6030 0007568  47.4901 312.6726 15.49446860580882",
                name = "ISS (ZARYA)",
            )

        fun issAt(position: RaDec) =
            TrackedSatellite(
                tle = ISS_TLE,
                info = SatelliteIds.cardFor(ISS_TLE, position, null),
                position = position,
            )

        /** A shower radiating from the look direction, active on [NOW] (2026-07-03). */
        fun activeShower() =
            MeteorShower(
                id = PERSEIDS_ID,
                name = "Perseids",
                radiant = RaDec(LOOK_RA, LOOK_DEC),
                activeFrom = MonthDay(7, 1),
                peak = MonthDay(7, 15),
                activeTo = MonthDay(7, 31),
                peakZhr = 100,
            )

        val CAMERA = camera(RaDec(LOOK_RA, LOOK_DEC))

        fun camera(lookAt: RaDec) =
            SkyCamera(
                lineOfSight = lookAt.toGeocentricVector(),
                up = Vector3(0.0, 0.0, 1.0),
                fovDeg = 60.0,
            )

        fun catalogObject(
            id: CelestialObjectId,
            position: RaDec,
        ) = CatalogObject(
            id = id,
            layerKind = LayerKind.STARS,
            type = TypeCode("star"),
            position = position,
            magnitude = 1.0,
            colorIndex = null,
            name = "Star",
            nameIsPrimary = true,
            searchFovDeg = null,
        )

        fun objectInfo(
            id: CelestialObjectId,
            name: String,
        ) = ObjectInfo(
            id = id,
            name = name,
            type = TypeCode("star"),
            position = null,
            parent = null,
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
