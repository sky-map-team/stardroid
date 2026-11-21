/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.tonight

import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.surfaces.NotificationText
import com.google.android.stardroid.testing.assertThat
import com.google.android.stardroid.text.TextSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TonightViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val now = LocalDateTime(2024, 8, 12, 18, 0, 0).toInstant(TimeZone.UTC)

    private fun viewModel(
        location: LatLong? = LONDON,
        passes: suspend (LatLong) -> List<SatellitePass> = { emptyList() },
        notices: suspend () -> List<TonightNotice> = { emptyList() },
    ) = TonightViewModel(
        now = { now },
        location = { location },
        showers = { listOf(PERSEIDS) },
        passes = passes,
        notices = notices,
        words = {
            TonightWords(
                NotificationText(TextSource { id, args -> "$id(${args.joinToString()})" }) { "t" },
            ) { "t" }
        },
        zone = { TimeZone.UTC },
    )

    @Test
    fun `it is hidden until opened and hidden again after closing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertThat(vm.visible.value).isFalse()

            vm.open()
            runCurrent()
            assertThat(vm.visible.value).isTrue()

            vm.close()
            assertThat(vm.visible.value).isFalse()
        }

    @Test
    fun `the rows are the highlights tonightSky ranks and each opens its object`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.open()
            runCurrent()

            val expected = tonightSky(now, LONDON, listOf(PERSEIDS), TimeZone.UTC)
            val state = vm.state.value!!
            assertThat(state.rows.size).isEqualTo(expected.highlights.size)
            assertThat(state.rows.first().targetId).isEqualTo("shower/perseids")
        }

    @Test
    fun `without a location there are no sun times`() =
        runTest(dispatcher) {
            val vm = viewModel(location = null)
            vm.open()
            runCurrent()

            val state = vm.state.value!!
            assertThat(state.sunset).isNull()
            assertThat(state.darkness).isNull()
        }

    @Test
    fun `passes are asked for at the saved location`() =
        runTest(dispatcher) {
            var askedAt: LatLong? = null
            val vm =
                viewModel(
                    passes = {
                        askedAt = it
                        emptyList()
                    },
                )
            vm.open()
            runCurrent()

            assertThat(askedAt).isEqualTo(LONDON)
        }

    @Test
    fun `announcements are carried through in full`() =
        runTest(dispatcher) {
            val notice = TonightNotice("Voyager", "A long body", searchQuery = "Voyager 1")
            val vm = viewModel(notices = { listOf(notice) })
            vm.open()
            runCurrent()

            assertThat(vm.state.value!!.notices).isEqualTo(listOf(notice))
        }

    @Test
    fun `a failing announcement source leaves the highlights`() =
        runTest(dispatcher) {
            val vm = viewModel(notices = { error("remote config unavailable") })
            vm.open()
            runCurrent()

            val state = vm.state.value!!
            assertThat(state.notices).isEqualTo(emptyList<TonightNotice>())
            assertThat(state.rows.isNotEmpty()).isTrue()
        }

    @Test
    fun `a failing build shows an empty sheet rather than crashing`() =
        runTest(dispatcher) {
            val vm = viewModel(passes = { error("elements unreadable") })
            vm.open()
            runCurrent()

            assertThat(vm.state.value!!.rows).isEqualTo(emptyList<TonightRow>())
        }

    @Test
    fun `opening again clears the old contents and uses the latest inputs`() =
        runTest(dispatcher) {
            var announced = listOf(TonightNotice("First", "", null))
            val vm = viewModel(notices = { announced })
            vm.open()
            runCurrent()
            vm.close()

            announced = listOf(TonightNotice("Second", "", null))
            vm.open()
            assertThat(vm.state.value).isNull()
            runCurrent()

            assertThat(vm.state.value!!.notices.map { it.title }).isEqualTo(listOf("Second"))
        }

    private companion object {
        val LONDON = LatLong(51.51, -0.13)
        val PERSEIDS =
            MeteorShower(
                id = CelestialObjectId("shower/perseids"),
                name = "Perseids",
                radiant = RaDec(46.2, 57.4),
                activeFrom = MonthDay(7, 17),
                peak = MonthDay(8, 12),
                activeTo = MonthDay(8, 24),
                peakZhr = 100,
            )
    }
}
