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
import com.google.android.stardroid.astronomy.SolarSystemBody
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
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val createdViewModels = mutableListOf<ObjectInfoViewModel>()

    private val locale = MutableStateFlow(LocaleSpec("en"))

    private fun viewModel(): ObjectInfoViewModel =
        ObjectInfoViewModel(
            catalog = { repository },
            locale = locale,
            ephemeris = KeplerianEphemeris,
            now = { NOW },
            settings = settings,
            location = { LONDON },
            computeContext = UnconfinedTestDispatcher(dispatcher.scheduler),
        ).also { createdViewModels += it }

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
    fun `a fixed-position card carries the next rise and set after now`() =
        testScope.runCurrentTest {
            // Sirius from London crosses the horizon twice a day; both events land in the
            // day after `now`.
            repository.infos[SIRIUS_ID] =
                objectInfo(SIRIUS_ID, "Sirius").copy(position = SIRIUS_POSITION)
            val vm = viewModel()

            vm.show(SIRIUS_ID)
            runCurrent()
            val times = vm.riseSet.value as RiseSetState.Times
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
            val times = vm.riseSet.value as RiseSetState.Times
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
            assertThat(vm.riseSet.value).isInstanceOf(RiseSetState.Times::class.java)
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
            assertThat(vm.riseSet.value).isEqualTo(RiseSetState.AlwaysAbove)

            vm.show(acruxId)
            runCurrent()
            assertThat(vm.riseSet.value).isEqualTo(RiseSetState.AlwaysBelow)
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
            assertThat(vm.riseSet.value).isNotNull()

            vm.show(shower)
            runCurrent()
            assertThat(vm.riseSet.value).isNull()

            vm.show(SIRIUS_ID)
            runCurrent()
            vm.dismiss()
            assertThat(vm.riseSet.value).isNull()
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

        val SIRIUS_ID = CelestialObjectId("star/sirius")
        val SIRIUS_POSITION = RaDec(101.287, -16.716)

        val PERSEIDS_ID = CelestialObjectId("shower/perseids")

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
