/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.location

import com.google.android.stardroid.location.FakeLocationProvider
import com.google.android.stardroid.location.Geocoding
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.location.LocationSource
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val provider = FakeLocationProvider()
    private val settings = FakeSettings()
    private val geocoding = FakeGeocoding()

    private class FakeGeocoding : Geocoding {
        var placeResult: Geocoding.PlaceResult = Geocoding.PlaceResult.NotFound
        var reverseName: String? = null
        var lastQuery: String? = null

        override suspend fun resolvePlace(name: String): Geocoding.PlaceResult {
            lastQuery = name
            return placeResult
        }

        override suspend fun reverseGeocode(location: LatLong): String? = reverseName
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun controller(scope: CoroutineScope) =
        LocationController(
            provider = provider,
            settings = settings,
            hasLocationPermission = { true },
            wallTimeMillis = { 1_000_000L },
            scope = scope,
        )

    @Test
    fun `submitting valid coordinates confirms a manual location`() =
        runTest(dispatcher.scheduler) {
            val controller = controller(backgroundScope)
            val vm = LocationViewModel(controller, geocoding)
            val accepted = vm.submitManualLocation("48.85", "2.35")
            runCurrent()

            assertThat(accepted).isTrue()
            val state = controller.state.value as LocationState.Confirmed
            assertThat(state.source).isEqualTo(LocationSource.MANUAL)
            assertThat(state.location).isEqualTo(LatLong(48.85, 2.35))
            assertThat(vm.manualEntry.value.latitudeInvalid).isFalse()
            assertThat(vm.manualEntry.value.longitudeInvalid).isFalse()
        }

    @Test
    fun `out-of-range or unparseable coordinates are flagged, not clamped`() =
        runTest(dispatcher.scheduler) {
            val controller = controller(backgroundScope)
            val vm = LocationViewModel(controller, geocoding)

            assertThat(vm.submitManualLocation("91", "2.35")).isFalse()
            assertThat(vm.manualEntry.value.latitudeInvalid).isTrue()
            assertThat(vm.manualEntry.value.longitudeInvalid).isFalse()

            assertThat(vm.submitManualLocation("48.85", "-180.5")).isFalse()
            assertThat(vm.manualEntry.value.longitudeInvalid).isTrue()

            assertThat(vm.submitManualLocation("north", "2.35")).isFalse()
            assertThat(vm.manualEntry.value.latitudeInvalid).isTrue()

            assertThat(controller.state.value).isEqualTo(LocationState.Unset)
        }

    @Test
    fun `coordinate parsing accepts comma decimals and Arabic-Indic digits`() {
        assertThat(LocationViewModel.parseCoordinate("48,85")).isEqualTo(48.85)
        assertThat(LocationViewModel.parseCoordinate("٤٨٫٨٥")).isEqualTo(48.85)
        assertThat(LocationViewModel.parseCoordinate("۴۸٫۸۵")).isEqualTo(48.85)
        assertThat(LocationViewModel.parseCoordinate(" -12.5 ")).isEqualTo(-12.5)
        assertThat(LocationViewModel.parseCoordinate("−12.5")).isEqualTo(-12.5)
        assertThat(LocationViewModel.parseCoordinate("–12.5")).isEqualTo(-12.5)
        assertThat(LocationViewModel.parseCoordinate("—12.5")).isEqualTo(-12.5)
        assertThat(LocationViewModel.parseCoordinate("nonsense")).isNull()
        assertThat(LocationViewModel.parseCoordinate("")).isNull()
    }

    @Test
    fun `resolving a place fills the resolved coordinates`() =
        runTest(dispatcher.scheduler) {
            val paris = LatLong(48.85, 2.35)
            geocoding.placeResult = Geocoding.PlaceResult.Found(paris)
            val vm = LocationViewModel(controller(backgroundScope), geocoding)
            vm.resolvePlace("  Paris  ")
            runCurrent()

            assertThat(geocoding.lastQuery).isEqualTo("Paris")
            assertThat(vm.manualEntry.value.resolved).isEqualTo(paris)
            assertThat(vm.manualEntry.value.resolving).isFalse()
            assertThat(vm.manualEntry.value.placeError).isNull()
        }

    @Test
    fun `Set Location auto-resolves a typed place the user forgot to resolve`() =
        runTest(dispatcher.scheduler) {
            val paris = LatLong(48.85, 2.35)
            geocoding.placeResult = Geocoding.PlaceResult.Found(paris)
            val controller = controller(backgroundScope)
            val vm = LocationViewModel(controller, geocoding)

            // Place typed, Resolve never tapped, coordinate fields left blank.
            val accepted = vm.confirmManualLocation("Paris", "", "")
            runCurrent()

            assertThat(accepted).isTrue()
            assertThat(geocoding.lastQuery).isEqualTo("Paris")
            val state = controller.state.value as LocationState.Confirmed
            assertThat(state.location).isEqualTo(paris)
            assertThat(state.source).isEqualTo(LocationSource.MANUAL)
        }

    @Test
    fun `Set Location reports a failed auto-resolve and keeps the dialog open`() =
        runTest(dispatcher.scheduler) {
            geocoding.placeResult = Geocoding.PlaceResult.NotFound
            val controller = controller(backgroundScope)
            val vm = LocationViewModel(controller, geocoding)

            val accepted = vm.confirmManualLocation("Atlantis", "", "")
            runCurrent()

            assertThat(accepted).isFalse()
            assertThat(vm.manualEntry.value.placeError)
                .isEqualTo(LocationViewModel.PlaceError.NOT_FOUND)
            assertThat(controller.state.value).isEqualTo(LocationState.Unset)
        }

    @Test
    fun `Set Location does not re-geocode an already-resolved place`() =
        runTest(dispatcher.scheduler) {
            val paris = LatLong(48.85, 2.35)
            geocoding.placeResult = Geocoding.PlaceResult.Found(paris)
            val vm = LocationViewModel(controller(backgroundScope), geocoding)
            vm.resolvePlace("Paris")
            runCurrent()
            geocoding.lastQuery = null

            // Confirm with the resolved coordinates in the fields; no second lookup.
            val accepted = vm.confirmManualLocation("Paris", "48.85", "2.35")
            runCurrent()

            assertThat(accepted).isTrue()
            assertThat(geocoding.lastQuery).isNull()
        }

    @Test
    fun `place lookup failures map onto the two error kinds`() =
        runTest(dispatcher.scheduler) {
            val vm = LocationViewModel(controller(backgroundScope), geocoding)

            geocoding.placeResult = Geocoding.PlaceResult.NotFound
            vm.resolvePlace("Atlantis")
            runCurrent()
            assertThat(vm.manualEntry.value.placeError)
                .isEqualTo(LocationViewModel.PlaceError.NOT_FOUND)

            geocoding.placeResult = Geocoding.PlaceResult.Unavailable
            vm.resolvePlace("Paris")
            runCurrent()
            assertThat(vm.manualEntry.value.placeError)
                .isEqualTo(LocationViewModel.PlaceError.UNAVAILABLE)
        }

    @Test
    fun `blank place names are ignored`() =
        runTest(dispatcher.scheduler) {
            val vm = LocationViewModel(controller(backgroundScope), geocoding)
            vm.resolvePlace("   ")
            runCurrent()
            assertThat(geocoding.lastQuery).isNull()
            assertThat(vm.manualEntry.value.resolving).isFalse()
        }

    @Test
    fun `resetManualEntry clears errors and stale resolutions`() =
        runTest(dispatcher.scheduler) {
            val vm = LocationViewModel(controller(backgroundScope), geocoding)
            vm.submitManualLocation("91", "181")
            geocoding.placeResult = Geocoding.PlaceResult.NotFound
            vm.resolvePlace("Atlantis")
            runCurrent()
            vm.resetManualEntry()
            assertThat(vm.manualEntry.value).isEqualTo(LocationViewModel.ManualEntryUi())
        }

    @Test
    fun `a fresh fix produces a toast with its reverse-geocoded name`() =
        runTest(dispatcher.scheduler) {
            val controller = controller(backgroundScope)
            val vm = LocationViewModel(controller, geocoding)
            val toasts = mutableListOf<LocationViewModel.LocationToast>()
            backgroundScope.launch { vm.toasts.collect(toasts::add) }
            runCurrent()

            geocoding.reverseName = "San Francisco"
            controller.start()
            runCurrent()
            val fix = LatLong(37.77, -122.42)
            provider.sendFix(fix)
            runCurrent()

            assertThat(toasts).containsExactly(
                LocationViewModel.LocationToast(fix, "San Francisco"),
            )
        }

    @Test
    fun `the silent startup load of a saved manual location does not toast`() =
        runTest(dispatcher.scheduler) {
            settings.noAutoLocateState.value = true
            settings.savedLocationState.value = LatLong(48.85, 2.35)
            val controller = controller(backgroundScope)
            val vm = LocationViewModel(controller, geocoding)
            val toasts = mutableListOf<LocationViewModel.LocationToast>()
            backgroundScope.launch { vm.toasts.collect(toasts::add) }
            runCurrent()

            controller.start()
            runCurrent()

            assertThat(controller.state.value)
                .isInstanceOf(LocationState.Confirmed::class.java)
            assertThat(toasts).isEmpty()
        }
}
