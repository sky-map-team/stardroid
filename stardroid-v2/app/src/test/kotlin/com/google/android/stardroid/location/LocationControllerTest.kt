/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationControllerTest {
    private val provider = FakeLocationProvider()
    private val settings = FakeSettings()

    private fun TestScope.controller(scope: CoroutineScope = backgroundScope) =
        LocationController(
            provider = provider,
            settings = settings,
            hasLocationPermission = { permissionGranted },
            wallTimeMillis = { 1_000_000L },
            scope = scope,
        )

    private var permissionGranted = true

    @Test
    fun `start with permission begins acquiring and starts provider updates`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            assertThat(controller.state.value).isEqualTo(LocationState.Acquiring)
            assertThat(provider.updating).isTrue()
        }

    @Test
    fun `a fix confirms, moves the location bus, persists, and emits a fresh-fix event`() =
        runTest {
            val controller = controller()
            val fixes = mutableListOf<LatLong>()
            backgroundScope.launch { controller.fixes.collect(fixes::add) }
            controller.start()
            runCurrent()
            val sanFrancisco = LatLong(37.77, -122.42)
            provider.sendFix(sanFrancisco, accuracyM = 25f)
            runCurrent()

            val state = controller.state.value as LocationState.Confirmed
            assertThat(state.location).isEqualTo(sanFrancisco)
            assertThat(state.source).isEqualTo(LocationSource.AUTO)
            assertThat(state.accuracyM).isEqualTo(25f)
            assertThat(controller.locations.value).isEqualTo(sanFrancisco)
            assertThat(settings.savedLocationState.value).isEqualTo(sanFrancisco)
            assertThat(fixes).containsExactly(sanFrancisco)
        }

    @Test
    fun `a re-delivered unmoved fix does not repeat the fresh-fix event`() =
        runTest {
            val controller = controller()
            val fixes = mutableListOf<LatLong>()
            backgroundScope.launch { controller.fixes.collect(fixes::add) }
            controller.start()
            runCurrent()
            val sanFrancisco = LatLong(37.77, -122.42)
            provider.sendFix(sanFrancisco, accuracyM = 25f)
            runCurrent()

            // Rotation: the activity restarts the controller and the provider re-delivers
            // the same position. The state updates but no new event (no repeated snackbar).
            controller.stop()
            controller.start()
            runCurrent()
            provider.sendFix(sanFrancisco, accuracyM = 25f)
            runCurrent()
            assertThat(fixes).containsExactly(sanFrancisco)

            // An actual move past the provider's min-distance filter is news again.
            val oakland = LatLong(37.80, -122.27)
            provider.sendFix(oakland, accuracyM = 25f)
            runCurrent()
            assertThat(fixes).containsExactly(sanFrancisco, oakland).inOrder()
        }

    @Test
    fun `a repeated manual entry still emits its confirmation event`() =
        runTest {
            val controller = controller()
            val fixes = mutableListOf<LatLong>()
            backgroundScope.launch { controller.fixes.collect(fixes::add) }
            runCurrent()
            val tokyo = LatLong(35.68, 139.69)
            controller.setManualLocation(tokyo)
            runCurrent()
            controller.setManualLocation(tokyo)
            runCurrent()
            assertThat(fixes).containsExactly(tokyo, tokyo)
        }

    @Test
    fun `start without permission rests at Unset`() =
        runTest {
            permissionGranted = false
            val controller = controller()
            controller.start()
            runCurrent()
            assertThat(controller.state.value).isEqualTo(LocationState.Unset)
            assertThat(provider.updating).isFalse()
        }

    @Test
    fun `no enabled provider surfaces HardwareUnavailable`() =
        runTest {
            provider.available = false
            val controller = controller()
            controller.start()
            runCurrent()
            assertThat(controller.state.value).isEqualTo(LocationState.HardwareUnavailable)
        }

    @Test
    fun `acquiring times out after thirty seconds without a fix`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            advanceTimeBy(LocationController.LOCATION_ACQUIRING_TIMEOUT_MS + 1)
            assertThat(controller.state.value).isEqualTo(LocationState.AcquiringTimeout)
        }

    @Test
    fun `keepWaiting rearms the timeout`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            advanceTimeBy(LocationController.LOCATION_ACQUIRING_TIMEOUT_MS + 1)
            controller.keepWaiting()
            assertThat(controller.state.value).isEqualTo(LocationState.Acquiring)
            advanceTimeBy(LocationController.LOCATION_ACQUIRING_TIMEOUT_MS + 1)
            assertThat(controller.state.value).isEqualTo(LocationState.AcquiringTimeout)
        }

    @Test
    fun `a fix cancels the pending timeout`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            provider.sendFix(LatLong(37.77, -122.42))
            advanceTimeBy(LocationController.LOCATION_ACQUIRING_TIMEOUT_MS + 1)
            assertThat(controller.state.value).isInstanceOf(LocationState.Confirmed::class.java)
        }

    @Test
    fun `restart while confirmed auto keeps the fix instead of regressing to Acquiring`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            provider.sendFix(LatLong(37.77, -122.42))
            controller.startAuto()
            runCurrent()
            assertThat(controller.state.value).isInstanceOf(LocationState.Confirmed::class.java)
            // And no timeout fires against the healthy fix.
            advanceTimeBy(LocationController.LOCATION_ACQUIRING_TIMEOUT_MS + 1)
            assertThat(controller.state.value).isInstanceOf(LocationState.Confirmed::class.java)
        }

    @Test
    fun `manual mode start loads the saved location as a manual confirmation`() =
        runTest {
            val stored = LatLong(48.85, 2.35)
            settings.noAutoLocateState.value = true
            settings.savedLocationState.value = stored
            val controller = controller()
            controller.start()
            runCurrent()

            val state = controller.state.value as LocationState.Confirmed
            assertThat(state.source).isEqualTo(LocationSource.MANUAL)
            assertThat(state.location).isEqualTo(stored)
            assertThat(controller.locations.value).isEqualTo(stored)
            assertThat(provider.updating).isFalse()
        }

    @Test
    fun `manual mode start without a saved location rests at Unset`() =
        runTest {
            settings.noAutoLocateState.value = true
            val controller = controller()
            controller.start()
            runCurrent()
            assertThat(controller.state.value).isEqualTo(LocationState.Unset)
        }

    @Test
    fun `auto-mode start seeds the location bus from the saved position before any fix`() =
        runTest {
            val stored = LatLong(48.85, 2.35)
            settings.savedLocationState.value = stored
            val controller = controller()
            controller.start()
            runCurrent()
            assertThat(controller.locations.value).isEqualTo(stored)
            assertThat(controller.state.value).isEqualTo(LocationState.Acquiring)
        }

    @Test
    fun `setManualLocation stops updates, persists, confirms, and enters manual mode`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            val paris = LatLong(48.85, 2.35)
            controller.setManualLocation(paris)
            runCurrent()

            val state = controller.state.value as LocationState.Confirmed
            assertThat(state.source).isEqualTo(LocationSource.MANUAL)
            assertThat(state.accuracyM).isNull()
            assertThat(controller.locations.value).isEqualTo(paris)
            assertThat(settings.savedLocationState.value).isEqualTo(paris)
            assertThat(settings.noAutoLocateState.value).isTrue()
            assertThat(provider.updating).isFalse()
        }

    @Test
    fun `switchToAuto clears manual mode and starts acquiring`() =
        runTest {
            settings.noAutoLocateState.value = true
            val controller = controller()
            controller.switchToAuto()
            runCurrent()
            assertThat(settings.noAutoLocateState.value).isFalse()
            assertThat(controller.state.value).isEqualTo(LocationState.Acquiring)
            assertThat(provider.updating).isTrue()
        }

    @Test
    fun `switchToManual stops updates and marks manual mode without touching the state`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            controller.switchToManual()
            runCurrent()
            assertThat(settings.noAutoLocateState.value).isTrue()
            assertThat(provider.updating).isFalse()
        }

    @Test
    fun `permission denial maps canAsk onto the two denied states`() =
        runTest {
            val controller = controller()
            controller.onPermissionDenied(canAsk = true)
            assertThat(controller.state.value).isEqualTo(LocationState.PermissionDenied)
            controller.onPermissionDenied(canAsk = false)
            assertThat(controller.state.value)
                .isEqualTo(LocationState.PermissionPermanentlyDenied)
        }

    @Test
    fun `revocation while tracking returns to PermissionDenied and stops updates`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            provider.sendFix(LatLong(37.77, -122.42))
            controller.onPermissionRevoked()
            assertThat(controller.state.value).isEqualTo(LocationState.PermissionDenied)
            assertThat(provider.updating).isFalse()
        }

    @Test
    fun `stop halts provider updates but keeps the state`() =
        runTest {
            val controller = controller()
            controller.start()
            runCurrent()
            provider.sendFix(LatLong(37.77, -122.42))
            controller.stop()
            assertThat(provider.updating).isFalse()
            assertThat(controller.state.value).isInstanceOf(LocationState.Confirmed::class.java)
        }

    @Test
    fun `restart without permission preserves an existing denied state`() =
        runTest {
            permissionGranted = false
            val controller = controller()
            controller.onPermissionDenied(canAsk = false)
            controller.start()
            runCurrent()
            assertThat(controller.state.value)
                .isEqualTo(LocationState.PermissionPermanentlyDenied)
        }

    @Test
    fun `stop before start's coroutine resumes cancels the pending start`() =
        runTest {
            val controller = controller()
            controller.start()
            controller.stop()
            runCurrent()
            assertThat(provider.updating).isFalse()
            assertThat(controller.state.value).isEqualTo(LocationState.Unset)
        }
}
