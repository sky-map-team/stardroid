/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.astronomy.KeplerianEphemeris
import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.sensors.MagneticDeclinationSource
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.ZeroMagneticDeclinationSource
import com.google.android.stardroid.settings.FakeSettings
import com.google.android.stardroid.settings.FontSize
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val orientations = MutableSharedFlow<Matrix3>()
    private val locations = MutableStateFlow(LocationController.DEFAULT_LOCATION)
    private val settings = FakeSettings()

    /** The shared clock bus, driven by hand (D42). */
    private val times = MutableStateFlow(TIME)

    /**
     * Frames on virtual time: one [FRAME_MILLIS] tick per frame, timestamped from the same
     * virtual clock. `advanceTimeBy` then drives the camera animations exactly as the display's
     * Choreographer drives them on a device (D93).
     */
    private class TestFrameTicker(
        private val scheduler: TestCoroutineScheduler,
        private val frameMillis: Long,
    ) : FrameTicker {
        override suspend fun awaitFrame(): Long {
            delay(frameMillis)
            return scheduler.currentTime * 1_000_000L
        }
    }

    private class FakeOrientationSource(
        override val available: Boolean,
        private val flow: Flow<Matrix3>,
    ) : OrientationSource {
        override fun orientations(): Flow<Matrix3> = flow
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val createdViewModels = mutableListOf<MapViewModel>()

    private fun viewModel(
        sensors: Boolean = true,
        declinationSource: MagneticDeclinationSource = ZeroMagneticDeclinationSource,
        frameMillis: Long = FRAME_MILLIS,
    ) = MapViewModel(
        orientationSource = FakeOrientationSource(sensors, orientations),
        declinationSource = declinationSource,
        locations = locations,
        settings = settings,
        ephemeris = KeplerianEphemeris,
        timeFlow = times,
        now = { times.value },
        frameTicker = TestFrameTicker(dispatcher.scheduler, frameMillis),
    ).also { createdViewModels += it }

    @Test
    fun `chrome-ever-toggled starts null so the auto-hide timer waits for the real value`() =
        testScope.runCurrentTest {
            // A first-run user must not lose the chrome to a default guessed before the stored
            // value arrives; MapScreen keys its timer on a non-null true.
            settings.chromeEverToggledState.value = false
            val vm = viewModel()
            assertThat(vm.chromeEverToggled.value).isNull()
            runCurrent()
            assertThat(vm.chromeEverToggled.value).isFalse()
        }

    @Test
    fun `toggling the chrome records it, so later runs auto-hide again`() =
        testScope.runCurrentTest {
            settings.chromeEverToggledState.value = false
            val vm = viewModel()
            runCurrent()

            vm.onChromeToggledByUser()
            runCurrent()
            assertThat(settings.chromeEverToggledState.value).isTrue()
            assertThat(vm.chromeEverToggled.value).isTrue()
        }

    @Test
    fun `boots into sensor frame when sensors exist, manual otherwise`() {
        assertThat(viewModel(sensors = true).referenceFrame.value)
            .isEqualTo(ReferenceFrame.SENSOR)
        assertThat(viewModel(sensors = false).referenceFrame.value)
            .isEqualTo(ReferenceFrame.MANUAL)
    }

    @Test
    fun `cannot switch to sensor frame without sensors`() {
        val vm = viewModel(sensors = false)
        vm.setReferenceFrame(ReferenceFrame.SENSOR)
        assertThat(vm.referenceFrame.value).isEqualTo(ReferenceFrame.MANUAL)
    }

    @Test
    fun `sensor orientation drives the camera through the sky model`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()

            val expected =
                SkyModel.pointing(
                    SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION),
                    Matrix3.IDENTITY,
                )
            assertThat(vm.camera.value.lineOfSight.distanceTo(expected.lineOfSight))
                .isLessThan(TOL)
            assertThat(vm.camera.value.up.distanceTo(expected.perpendicular)).isLessThan(TOL)
            assertThat(vm.camera.value.fovDeg).isEqualTo(MapViewModel.INITIAL_FOV_DEG)
        }

    @Test
    fun `location change refreshes the local frame`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()

            locations.value = LatLong(-33.9, 18.4)
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()

            val expected =
                SkyModel.pointing(
                    SkyModel.localFrame(TIME, LatLong(-33.9, 18.4)),
                    Matrix3.IDENTITY,
                )
            assertThat(vm.camera.value.lineOfSight.distanceTo(expected.lineOfSight))
                .isLessThan(TOL)
        }

    @Test
    fun `drag, rotate, and fling are ignored while sensors own the camera, but zoom works`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            runCurrent()
            val before = vm.camera.value
            vm.onDrag(100f, 50f, 1000)
            vm.onRotate(30f)
            vm.onFling(1000f, 0f, 1000)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(before)
            // v1 never disables its ZoomController in auto mode.
            vm.onStretch(2f)
            assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(MapViewModel.INITIAL_FOV_DEG / 2)
            assertThat(vm.camera.value.lineOfSight).isEqualTo(before.lineOfSight)
        }

    @Test
    fun `switching to manual keeps the sensor camera`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()
            val sensorCamera = vm.camera.value

            vm.setReferenceFrame(ReferenceFrame.MANUAL)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(sensorCamera)
        }

    @Test
    fun `drag right-left slides pointing along screen-right, v1 math`() {
        val vm = viewModel(sensors = false)
        // Initial camera: los (1,0,0), up (0,0,1); screen-right = los × up = (0,-1,0).
        // v1 MapMover: radians = -dx · fov·(π/180)/side, over the side the FOV spans — the
        // short side in v2 (SkyCamera.fovDeg); the initial FOV, 1000 px short side here.
        vm.onDrag(100f, 0f, 1000)
        val radians = -100.0 * (MapViewModel.INITIAL_FOV_DEG * DEGREES_TO_RADIANS / 1000.0)
        val len = sqrt(1.0 + radians * radians)
        val los = vm.camera.value.lineOfSight
        assertThat(abs(los.x - 1.0 / len)).isLessThan(TOL)
        assertThat(abs(los.y - -radians / len)).isLessThan(TOL)
        assertThat(abs(los.z)).isLessThan(TOL)
        // Right-left leaves up untouched (v1 changeRightLeft).
        assertThat(vm.camera.value.up.distanceTo(Vector3.UNIT_Z)).isLessThan(TOL)
    }

    @Test
    fun `drag up-down pitches pointing and up together`() {
        val vm = viewModel(sensors = false)
        vm.onDrag(0f, 200f, 1000)
        val los = vm.camera.value.lineOfSight
        val up = vm.camera.value.up
        // Perpendicularity is preserved and both moved in the los/up plane.
        assertThat(abs(los dot up)).isLessThan(TOL)
        assertThat(abs(los.y)).isLessThan(TOL)
        assertThat(los.z).isNonZero()
    }

    @Test
    fun `stretch zooms in, pinch zooms out, both clamped`() {
        val vm = viewModel(sensors = false)
        vm.onStretch(2f)
        assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(MapViewModel.INITIAL_FOV_DEG / 2)
        vm.onStretch(1e-6f)
        assertThat(vm.camera.value.fovDeg).isEqualTo(MapViewModel.MAX_FOV_DEG)
        vm.onStretch(1e6f)
        assertThat(vm.camera.value.fovDeg).isEqualTo(MapViewModel.MIN_FOV_DEG)
    }

    @Test
    fun `zooming deep offers the label-size hint exactly once`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            val hints = mutableListOf<Unit>()
            val job = launch { vm.labelSizeHints.collect { hints += it } }
            runCurrent()

            // Ratios derived from the threshold, not hardcoded: the exact trigger depth is a
            // tuning decision, and these assertions are about either side of it.
            val toThreshold =
                (MapViewModel.INITIAL_FOV_DEG / MapViewModel.LABEL_HINT_FOV_DEG).toFloat()

            // Stopping just short of the threshold: no hint.
            vm.onStretch(toThreshold * 0.9f)
            runCurrent()
            assertThat(vm.camera.value.fovDeg).isGreaterThan(MapViewModel.LABEL_HINT_FOV_DEG)
            assertThat(hints).isEmpty()

            // Crossing it: the sky has visibly outgrown the labels.
            vm.onStretch(1f / 0.9f * 1.05f)
            runCurrent()
            assertThat(vm.camera.value.fovDeg).isLessThan(MapViewModel.LABEL_HINT_FOV_DEG)
            assertThat(hints).hasSize(1)
            assertThat(settings.labelSizeHintShownState.value).isTrue()

            // Zooming out and back in again never re-offers it.
            vm.onStretch(0.01f)
            vm.onStretch(1e4f)
            runCurrent()
            assertThat(hints).hasSize(1)
            job.cancel()
        }

    @Test
    fun `a collector that attaches after the deep zoom still gets the hint`() =
        testScope.runCurrentTest {
            // The real ordering: the view model's init collector can cross the threshold
            // before the map composes and subscribes. A zero-replay SharedFlow silently drops
            // that emission, which is what kept the snackbar from ever appearing.
            val vm = viewModel(sensors = false)
            runCurrent()
            vm.onStretch(1e4f)
            runCurrent()

            val hints = mutableListOf<Unit>()
            val job = launch { vm.labelSizeHints.collect { hints += it } }
            runCurrent()
            assertThat(hints).hasSize(1)
            job.cancel()
        }

    @Test
    fun `a collector that restarts after showing the hint is not offered it again`() =
        testScope.runCurrentTest {
            // The map's LaunchedEffect collector restarts on every re-composition — backing
            // out of Settings or the gallery does exactly that. The replay cache exists only
            // to bridge the startup race in the test above, so the map clears it once it has
            // the hint; without that, each return from a sub-screen re-showed the snackbar.
            val vm = viewModel(sensors = false)
            runCurrent()
            vm.onStretch(1e4f)
            runCurrent()

            val first = mutableListOf<Unit>()
            val firstJob =
                launch {
                    vm.labelSizeHints.collect {
                        vm.labelSizeHints.resetReplayCache()
                        first += it
                    }
                }
            runCurrent()
            assertThat(first).hasSize(1)
            firstJob.cancel()

            // Back from Settings: the map composes again and re-subscribes.
            val second = mutableListOf<Unit>()
            val secondJob = launch { vm.labelSizeHints.collect { second += it } }
            runCurrent()
            assertThat(second).isEmpty()
            secondJob.cancel()
        }

    @Test
    fun `the label-size hint fires on any route to a deep zoom, not just pinch`() =
        testScope.runCurrentTest {
            // Search fly-to sets the FOV directly; the hint watches the camera, not the
            // gesture, so it must still fire.
            val vm = viewModel(sensors = false)
            val hints = mutableListOf<Unit>()
            val job = launch { vm.labelSizeHints.collect { hints += it } }
            runCurrent()

            vm.aimAt(Vector3(0.0, 1.0, 0.0), fovDeg = MapViewModel.LABEL_HINT_FOV_DEG / 2)
            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            assertThat(hints).hasSize(1)
            job.cancel()
        }

    @Test
    fun `rail labels show for the first three reveals, fading on the third`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            runCurrent()

            // Reveals 1 and 2: shown steadily.
            for (seen in 0 until MapViewModel.RAIL_LABEL_REVEALS - 1) {
                settings.railLabelRevealsState.value = seen
                runCurrent()
                assertThat(vm.railLabelState.value?.visible).isTrue()
                assertThat(vm.railLabelState.value?.fadingOut).isFalse()
            }

            // Reveal 3 is the farewell: still shown, but on its way out.
            settings.railLabelRevealsState.value = MapViewModel.RAIL_LABEL_REVEALS - 1
            runCurrent()
            assertThat(vm.railLabelState.value?.visible).isTrue()
            assertThat(vm.railLabelState.value?.fadingOut).isTrue()

            // Retired.
            settings.railLabelRevealsState.value = MapViewModel.RAIL_LABEL_REVEALS
            runCurrent()
            assertThat(vm.railLabelState.value?.visible).isFalse()
        }

    @Test
    fun `rail label state is null until the stored count arrives`() {
        // Eagerly stated, so the labels never flash on or off against a default guess.
        val vm = viewModel(sensors = false)
        assertThat(vm.railLabelState.value).isNull()
    }

    @Test
    fun `recording a reveal increments the stored count`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            runCurrent()
            vm.onRailLabelsRevealed()
            vm.onRailLabelsRevealed()
            runCurrent()
            assertThat(settings.railLabelRevealsState.value).isEqualTo(2)
        }

    @Test
    fun `recording the farewell reveal immediately retires the labels`() =
        testScope.runCurrentTest {
            // The state this flow reports is post-increment, so the map must latch a reveal's
            // state at entry rather than following it live — otherwise recording the farewell
            // reveal flips `visible` false a frame later and the last showing never happens.
            // This pins the flow's behaviour so the reason for that latch stays visible.
            settings.railLabelRevealsState.value = MapViewModel.RAIL_LABEL_REVEALS - 1
            val vm = viewModel(sensors = false)
            runCurrent()
            assertThat(vm.railLabelState.value?.fadingOut).isTrue()

            vm.onRailLabelsRevealed()
            runCurrent()
            assertThat(vm.railLabelState.value?.visible).isFalse()
        }

    @Test
    fun `the label-size hint stays silent once the flag is already set`() =
        testScope.runCurrentTest {
            settings.labelSizeHintShownState.value = true
            val vm = viewModel(sensors = false)
            val hints = mutableListOf<Unit>()
            val job = launch { vm.labelSizeHints.collect { hints += it } }
            runCurrent()

            vm.onStretch(1e4f)
            runCurrent()
            assertThat(hints).isEmpty()
            job.cancel()
        }

    @Test
    fun `rotate turns up about the line of sight by the gesture angle`() {
        val vm = viewModel(sensors = false)
        val before = vm.camera.value
        vm.onRotate(90f)
        val after = vm.camera.value
        assertThat(after.lineOfSight).isEqualTo(before.lineOfSight)
        // 90° rotation about los: new up is perpendicular to both old up and los, and the
        // direction is pinned — rotationMatrix(90°, +x) carries +z onto +y, so the sky turns
        // with the fingers rather than against them (v1's direction).
        assertThat(abs(after.up dot before.up)).isLessThan(TOL)
        assertThat(abs(after.up dot after.lineOfSight)).isLessThan(TOL)
        assertThat(after.up.distanceTo(Vector3(0.0, 1.0, 0.0))).isLessThan(TOL)
    }

    @Test
    fun `fling keeps panning after the finger lifts, then decays to a stop`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            vm.onFling(1000f, 0f, 1000)
            advanceTimeBy(2 * FRAME_MILLIS)
            runCurrent()
            val afterFirstFrame = vm.camera.value.lineOfSight
            assertThat(afterFirstFrame.distanceTo(MapViewModel.INITIAL_LOS)).isGreaterThan(0.0)
            // 1000 px/s decaying at v1's rate drops under the stop threshold in ~3 s.
            advanceTimeBy(10_000)
            runCurrent()
            val atRest = vm.camera.value
            advanceTimeBy(1_000)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(atRest)
        }

    /**
     * Issue #959: the fling used to advance the camera on a fixed 20 Hz pump, so on a 120 Hz
     * display five of every six frames were identical and the motion visibly stuttered the
     * moment the finger lifted. It now moves once per frame — at any refresh rate — while the
     * distance travelled stays v1's, within the sampling error of the same decay curve.
     */
    @Test
    fun `the fling advances every frame and travels the same distance at any refresh rate`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            vm.onFling(1000f, 0f, 1000)

            // Every frame moves the camera: no duplicate frames to stutter on.
            advanceTimeBy(2 * FRAME_MILLIS)
            runCurrent()
            var previous = vm.camera.value.lineOfSight
            repeat(10) {
                advanceTimeBy(FRAME_MILLIS)
                runCurrent()
                val next = vm.camera.value.lineOfSight
                assertThat(next.distanceTo(previous)).isGreaterThan(0.0)
                previous = next
            }

            advanceTimeBy(10_000)
            runCurrent()
            val travelledAt60Hz = vm.camera.value.lineOfSight.distanceTo(MapViewModel.INITIAL_LOS)

            // Same fling, sampled three times as often: the same arrival, not a third of it.
            val fast = viewModel(sensors = false, frameMillis = FRAME_MILLIS / 3)
            fast.onFling(1000f, 0f, 1000)
            advanceTimeBy(10_000)
            runCurrent()
            val travelledAt180Hz =
                fast.camera.value.lineOfSight.distanceTo(MapViewModel.INITIAL_LOS)
            assertThat(abs(travelledAt180Hz - travelledAt60Hz)).isLessThan(0.02)
        }

    @Test
    fun `a stalled frame is clamped, not applied as a whole second of momentum`() =
        testScope.runCurrentTest {
            // A GC pause or a backgrounded app must slow the fling, never teleport the camera.
            val stalled = viewModel(sensors = false, frameMillis = 2_000)
            stalled.onFling(1000f, 0f, 1000)
            advanceTimeBy(4_000)
            runCurrent()
            val steady = viewModel(sensors = false)
            steady.onFling(1000f, 0f, 1000)
            advanceTimeBy(10_000)
            runCurrent()
            // One clamped 100 ms step, so strictly short of the full decayed travel.
            assertThat(stalled.camera.value.lineOfSight.distanceTo(MapViewModel.INITIAL_LOS))
                .isLessThan(steady.camera.value.lineOfSight.distanceTo(MapViewModel.INITIAL_LOS))
        }

    @Test
    fun `a new touch-down stops the fling dead`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            vm.onFling(1000f, 0f, 1000)
            advanceTimeBy(2 * FRAME_MILLIS)
            runCurrent()
            vm.stopFling()
            val stopped = vm.camera.value
            advanceTimeBy(1_000)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(stopped)
        }

    @Test
    fun `night mode round-trips through settings`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            runCurrent()
            assertThat(vm.nightMode.value).isFalse()
            vm.setNightMode(true)
            runCurrent()
            assertThat(vm.nightMode.value).isTrue()
        }

    @Test
    fun `render state carries the sun direction while the gradient preference is on`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            val state = vm.renderState.first()
            val expected =
                KeplerianEphemeris
                    .geocentricPosition(SolarSystemBody.SUN, TIME)
                    .toGeocentricVector()
            assertThat(state.skyGradient).isNotNull()
            assertThat(state.skyGradient!!.sunDirection.distanceTo(expected)).isLessThan(TOL)
        }

    @Test
    fun `turning the gradient preference off drops it from render state`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            settings.setShowSkyGradient(false)
            assertThat(vm.renderState.first().skyGradient).isNull()
            settings.setShowSkyGradient(true)
            assertThat(vm.renderState.first().skyGradient).isNotNull()
        }

    @Test
    fun `the gradient tracks the clock bus — a time-travel tick moves the sun`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            val travelTime = Instant.parse("2026-12-25T00:00:00Z")
            times.value = travelTime
            val state = vm.renderState.first()
            val expected =
                KeplerianEphemeris
                    .geocentricPosition(SolarSystemBody.SUN, travelTime)
                    .toGeocentricVector()
            assertThat(state.skyGradient!!.sunDirection.distanceTo(expected)).isLessThan(TOL)
        }

    @Test
    fun `a clock-bus tick refreshes the local frame under the sensor camera`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            runCurrent()
            val travelTime = Instant.parse("2026-12-25T00:00:00Z")
            times.value = travelTime
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()

            val expected =
                SkyModel.pointing(
                    SkyModel.localFrame(travelTime, LocationController.DEFAULT_LOCATION),
                    Matrix3.IDENTITY,
                )
            assertThat(vm.camera.value.lineOfSight.distanceTo(expected.lineOfSight))
                .isLessThan(TOL)
        }

    @Test
    fun `night mode and the gradient ride the same render state`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            settings.setNightMode(true)
            val state = vm.renderState.first()
            assertThat(state.nightMode).isTrue()
            // The app still reports the gradient; skipping it at night is the backend's job.
            assertThat(state.skyGradient).isNotNull()
        }

    @Test
    fun `aimAt slews to the target in manual mode, carrying up along`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            val target = Vector3(0.0, 1.0, 1.0)
            vm.aimAt(target)

            // Mid-flight: the camera has left the start but not yet arrived.
            advanceTimeBy(MapViewModel.SLEW_MILLIS / 2)
            runCurrent()
            val midway = vm.camera.value
            assertThat(midway.lineOfSight.distanceTo(MapViewModel.INITIAL_LOS))
                .isGreaterThan(0.1)
            assertThat(midway.lineOfSight.distanceTo(target.normalized())).isGreaterThan(0.1)
            assertThat(abs(midway.lineOfSight dot midway.up)).isLessThan(TOL)

            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            val cam = vm.camera.value
            assertThat(cam.lineOfSight.distanceTo(target.normalized())).isLessThan(TOL)
            assertThat(abs(cam.lineOfSight dot cam.up)).isLessThan(TOL)
            assertThat(abs(cam.up.length - 1.0)).isLessThan(TOL)
            // No FOV given: zoom is untouched (v1 never zoomed on search).
            assertThat(cam.fovDeg).isEqualTo(MapViewModel.INITIAL_FOV_DEG)
        }

    @Test
    fun `aimAt eases to a clamped search FOV`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            vm.aimAt(Vector3(0.0, 1.0, 0.0), fovDeg = 10.0)
            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            assertThat(vm.camera.value.fovDeg).isEqualTo(10.0)

            vm.aimAt(Vector3(0.0, 1.0, 0.0), fovDeg = 500.0)
            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            assertThat(vm.camera.value.fovDeg).isEqualTo(MapViewModel.MAX_FOV_DEG)
        }

    @Test
    fun `aimAt recovers when the target is collinear with the current up`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            // Initial up is UNIT_Z; aim straight at the pole. The great-circle rotation
            // carries up to a perpendicular direction by construction.
            vm.aimAt(Vector3.UNIT_Z)
            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            val cam = vm.camera.value
            assertThat(cam.lineOfSight.distanceTo(Vector3.UNIT_Z)).isLessThan(TOL)
            assertThat(abs(cam.lineOfSight dot cam.up)).isLessThan(TOL)
            assertThat(abs(cam.up.length - 1.0)).isLessThan(TOL)
        }

    @Test
    fun `aimAt slews through an exact about-face`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            // Antiparallel to the initial line of sight: the cross product vanishes and the
            // slew pivots about the current up instead.
            vm.aimAt(Vector3(-1.0, 0.0, 0.0))
            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            val cam = vm.camera.value
            assertThat(cam.lineOfSight.distanceTo(Vector3(-1.0, 0.0, 0.0))).isLessThan(TOL)
            assertThat(cam.up.distanceTo(MapViewModel.INITIAL_UP)).isLessThan(TOL)
        }

    @Test
    fun `a touch-down cancels the slew mid-flight`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            val target = Vector3(0.0, 1.0, 0.0)
            vm.aimAt(target)
            advanceTimeBy(MapViewModel.SLEW_MILLIS / 2)
            runCurrent()
            vm.stopFling()
            val atCancel = vm.camera.value

            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(atCancel)
            assertThat(vm.camera.value.lineOfSight.distanceTo(target)).isGreaterThan(0.1)
        }

    @Test
    fun `aimAt is a no-op in sensor mode`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = true)
            val before = vm.camera.value
            vm.aimAt(Vector3(0.0, 1.0, 0.0), fovDeg = 10.0)
            advanceTimeBy(SLEW_SETTLE_MILLIS)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(before)
        }

    @Test
    fun `font size preference drives the render state label scale`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            assertThat(vm.renderState.first().labelScaleFactor).isEqualTo(1.5)
            settings.setFontSize(FontSize.EXTRA_LARGE)
            assertThat(vm.renderState.first().labelScaleFactor).isEqualTo(2.5)
        }

    @Test
    fun `telescope view direction sights along the phone's long edge`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            settings.setViewDirectionMode(ViewDirectionMode.TELESCOPE)
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()

            val expected =
                SkyModel.pointing(
                    SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION),
                    Matrix3.IDENTITY,
                    ViewDirectionMode.TELESCOPE,
                )
            assertThat(vm.camera.value.lineOfSight.distanceTo(expected.lineOfSight))
                .isLessThan(TOL)
            assertThat(vm.camera.value.up.distanceTo(expected.perpendicular)).isLessThan(TOL)
        }

    @Test
    fun `disabling magnetic correction zeroes the model declination but keeps the offset`() =
        testScope.runCurrentTest {
            val fixedDeclination =
                object : MagneticDeclinationSource {
                    override fun declinationDeg(
                        location: LatLong,
                        time: Instant,
                    ) = 7.0
                }
            val vm = viewModel(declinationSource = fixedDeclination)
            settings.setSensorAzimuthAdjustmentDeg(3.0)
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()
            val frameFor = { declinationDeg: Double ->
                SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION, declinationDeg)
            }
            val corrected = SkyModel.pointing(frameFor(10.0), Matrix3.IDENTITY)
            assertThat(vm.camera.value.lineOfSight.distanceTo(corrected.lineOfSight))
                .isLessThan(TOL)

            settings.setUseMagneticCorrection(false)
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()
            val offsetOnly = SkyModel.pointing(frameFor(3.0), Matrix3.IDENTITY)
            assertThat(vm.camera.value.lineOfSight.distanceTo(offsetOnly.lineOfSight))
                .isLessThan(TOL)
        }

    @Test
    fun `gesture end springs the horizon level in manual mode`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            runCurrent()
            vm.onRotate(37f)
            val tilted = vm.camera.value.up

            vm.onGestureEnd()
            advanceTimeBy(10_000)
            runCurrent()

            val cam = vm.camera.value
            val zenith = SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION).up
            val target =
                (zenith - cam.lineOfSight * (zenith dot cam.lineOfSight)).normalized()
            // Converged to within the leveler's 0.1° stop threshold; los untouched.
            assertThat(cam.up.distanceTo(target)).isLessThan(0.01)
            assertThat(cam.up.distanceTo(tilted)).isGreaterThan(0.01)
            assertThat(cam.lineOfSight).isEqualTo(MapViewModel.INITIAL_LOS)
        }

    @Test
    fun `horizon leveling respects the preference and a stop request`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            settings.setAutoLevelHorizon(false)
            runCurrent()
            vm.onRotate(37f)
            val tilted = vm.camera.value

            vm.onGestureEnd()
            advanceTimeBy(10_000)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(tilted)

            settings.setAutoLevelHorizon(true)
            runCurrent()
            vm.onGestureEnd()
            runCurrent()
            vm.stopLeveling()
            val stopped = vm.camera.value
            advanceTimeBy(10_000)
            runCurrent()
            assertThat(vm.camera.value).isEqualTo(stopped)
        }

    /**
     * Issue #960: `onFling` and `onGestureEnd` are two independent coroutines racing on the
     * same camera. The old exit condition — "the angle read under the stop threshold, so
     * stop" — only ever sampled the angle instantaneously; if the fling was still running,
     * and still capable of re-tilting the horizon (as a diagonal fling does, frame by frame),
     * the leveler could see a momentary zero and quit for good, leaving any later drift
     * uncorrected until the next gesture. A rotate mid-fling stands in for that drift here,
     * since it's a controllable way to introduce it without depending on the exact numeric
     * roll error a diagonal drag happens to accumulate.
     */
    @Test
    fun `the leveler keeps fixing drift that lands while a fling is still running`() =
        testScope.runCurrentTest {
            val vm = viewModel(sensors = false)
            runCurrent()

            // Level the horizon first and let it fully settle with nothing else running.
            vm.onRotate(37f)
            vm.onGestureEnd()
            advanceTimeBy(10_000)
            runCurrent()
            vm.stopLeveling()

            // Start a fling; onGestureEnd fires right after it, as MapScreen does on lift.
            vm.onFling(1000f, 0f, 1000)
            vm.onGestureEnd()
            // Give the leveler time to read the (already level) horizon and, pre-fix, exit.
            advanceTimeBy(500)
            runCurrent()

            // Something re-tilts the horizon while the fling is still going.
            vm.onRotate(5f)
            val tilted = vm.camera.value.up

            // ~1000 px/s decays under the fling's stop threshold at ~3 s (see the fling tests
            // above); give the leveler ample time after that too.
            advanceTimeBy(4_500)
            runCurrent()

            val cam = vm.camera.value
            val zenith = SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION).up
            val target =
                (zenith - cam.lineOfSight * (zenith dot cam.lineOfSight)).normalized()
            assertThat(cam.up.distanceTo(target)).isLessThan(0.01)
            assertThat(cam.up.distanceTo(tilted)).isGreaterThan(0.01)
        }

    // ---- The map HUD (map-hud.md, D65) ---------------------------------------------------

    @Test
    fun `hud state reports the pointing in equatorial and horizontal coordinates`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            val states = mutableListOf<HudState?>()
            backgroundScope.launch { vm.hudState.collect { states += it } }
            runCurrent()
            advanceTimeBy(MapViewModel.HUD_SAMPLE_MILLIS + 50)
            runCurrent()

            val hud = checkNotNull(states.last())
            // The initial camera looks along (1, 0, 0): RA 0, Dec 0 by construction.
            assertThat(hud.raDeg).isWithin(TOL).of(0.0)
            assertThat(hud.decDeg).isWithin(TOL).of(0.0)
            assertThat(hud.fovDeg).isEqualTo(MapViewModel.INITIAL_FOV_DEG)
            assertThat(hud.azDeg).isAtLeast(0.0)
            assertThat(hud.azDeg).isLessThan(360.0)
            // Independent check: rebuilding the line of sight from the reported alt/az in
            // the observer's frame must land back on the camera's actual line of sight.
            val frame = SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION)
            val altRad = hud.altDeg * DEGREES_TO_RADIANS
            val azRad = hud.azDeg * DEGREES_TO_RADIANS
            val reconstructed =
                frame.trueNorth * (cos(altRad) * cos(azRad)) +
                    frame.trueEast * (cos(altRad) * sin(azRad)) +
                    frame.up * sin(altRad)
            assertThat(reconstructed.distanceTo(vm.camera.value.lineOfSight)).isLessThan(TOL)
        }

    @Test
    fun `hud state carries the drag-to-align correction`() =
        testScope.runCurrentTest {
            settings.sensorAzimuthAdjustmentState.value = 4.5
            settings.sensorAltitudeAdjustmentState.value = -1.2
            val vm = viewModel()
            val states = mutableListOf<HudState?>()
            backgroundScope.launch { vm.hudState.collect { states += it } }
            runCurrent()
            advanceTimeBy(MapViewModel.HUD_SAMPLE_MILLIS + 50)
            runCurrent()

            val hud = checkNotNull(states.last())
            assertThat(hud.correctionAzDeg).isEqualTo(4.5)
            assertThat(hud.correctionAltDeg).isEqualTo(-1.2)
        }

    @Test
    fun `alignment reset zeroes both axes and undo restores them`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            settings.sensorAzimuthAdjustmentState.value = 4.5
            settings.sensorAltitudeAdjustmentState.value = -1.2
            // The view model mirrors the preferences; let the sync collectors run.
            runCurrent()

            vm.resetAlignment()
            runCurrent()
            assertThat(settings.sensorAzimuthAdjustmentState.value).isEqualTo(0.0)
            assertThat(settings.sensorAltitudeAdjustmentState.value).isEqualTo(0.0)

            vm.undoAlignmentReset()
            runCurrent()
            assertThat(settings.sensorAzimuthAdjustmentState.value).isEqualTo(4.5)
            assertThat(settings.sensorAltitudeAdjustmentState.value).isEqualTo(-1.2)

            // A second undo has nothing cached: a no-op, not a crash or a re-apply.
            settings.sensorAzimuthAdjustmentState.value = 9.9
            vm.undoAlignmentReset()
            runCurrent()
            assertThat(settings.sensorAzimuthAdjustmentState.value).isEqualTo(9.9)
        }

    // ---- Through-camera (AR) mode (camera-ar-mode.md, D64) -------------------------------

    @Test
    fun `enabling ar forces sensor mode, locks the fov, and disabling restores it`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.setReferenceFrame(ReferenceFrame.MANUAL)
            vm.onStretch(2f)
            val zoomed = MapViewModel.INITIAL_FOV_DEG / 2
            assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(zoomed)

            assertThat(vm.setArMode(true)).isTrue()
            assertThat(vm.referenceFrame.value).isEqualTo(ReferenceFrame.SENSOR)
            vm.onArCameraReady(AR_SPECS)
            assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(40.0)

            vm.setArMode(false)
            assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(zoomed)
        }

    @Test
    fun `ar is unavailable without sensors`() {
        val vm = viewModel(sensors = false)
        assertThat(vm.setArMode(true)).isFalse()
        assertThat(vm.arMode.value).isFalse()
    }

    @Test
    fun `pinch drives the camera zoom within its range while the fov is locked`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.setArMode(true)
            vm.onArCameraReady(AR_SPECS)

            vm.onStretch(2f)
            assertThat(vm.arZoomRatio.value).isWithin(TOL).of(2.0)
            assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(20.0)

            // The zoom range is the camera's, far narrower than the map's 0.5°–90°.
            vm.onStretch(10f)
            assertThat(vm.arZoomRatio.value).isWithin(TOL).of(4.0)
            assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(10.0)
            vm.onStretch(0.01f)
            assertThat(vm.arZoomRatio.value).isWithin(TOL).of(1.0)
            assertThat(vm.camera.value.fovDeg).isWithin(TOL).of(40.0)
        }

    @Test
    fun `taking the map manual turns the camera layer off`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.setArMode(true)
            vm.setReferenceFrame(ReferenceFrame.MANUAL)
            assertThat(vm.arMode.value).isFalse()
        }

    @Test
    fun `render state goes transparent with the scrim, and night mode floors it`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            vm.setArMode(true)
            vm.setArScrim(0.2)
            val day = vm.renderState.first()
            assertThat(day.transparentBackground).isTrue()
            assertThat(day.cameraScrim).isWithin(TOL).of(0.2)

            settings.setNightMode(true)
            runCurrent()
            val night = vm.renderState.first()
            assertThat(night.cameraScrim)
                .isWithin(TOL)
                .of(MapViewModel.NIGHT_AR_SCRIM_FLOOR)

            vm.setArMode(false)
            val off = vm.renderState.first()
            assertThat(off.transparentBackground).isFalse()
            assertThat(off.cameraScrim).isEqualTo(0.0)
        }

    // ---- Drag-to-align (camera-ar-mode.md, D64, slice 2) ---------------------------------

    @Test
    fun `azimuth correction shifts sensor pointing exactly like extra declination`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            settings.sensorAzimuthAdjustmentState.value = 30.0
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()

            val expected =
                SkyModel.pointing(
                    SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION, 30.0),
                    Matrix3.IDENTITY,
                )
            assertThat(vm.camera.value.lineOfSight.distanceTo(expected.lineOfSight))
                .isLessThan(TOL)
        }

    @Test
    fun `altitude correction raises the view by the set number of degrees`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            runCurrent()
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()
            val frame = SkyModel.localFrame(TIME, LocationController.DEFAULT_LOCATION)
            val baseAltRad = asin((vm.camera.value.lineOfSight dot frame.up).coerceIn(-1.0, 1.0))

            settings.sensorAltitudeAdjustmentState.value = 10.0
            runCurrent()

            val correctedAltRad =
                asin((vm.camera.value.lineOfSight dot frame.up).coerceIn(-1.0, 1.0))
            assertThat((correctedAltRad - baseAltRad) * RADIANS_TO_DEGREES)
                .isWithin(1e-6)
                .of(10.0)
        }

    @Test
    fun `ar drag adjusts the correction, persists on gesture end, and undo rolls it back`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            val receipts = mutableListOf<Unit>()
            backgroundScope.launch { vm.arAlignmentReceipts.collect { receipts += it } }
            vm.setArMode(true)
            vm.onArCameraReady(AR_SPECS)
            orientations.emit(Matrix3.IDENTITY)
            runCurrent()

            // FOV locked at 40° over a 1000 px short side → 0.04°/px. The azimuth drag
            // subtracts (sky tracks the finger): dx = −250 px → +10° az; dy = +250 px
            // (downward) → +10° alt.
            vm.onDrag(-250f, 250f, 1000, pointerCount = 1)
            vm.onGestureEnd()
            runCurrent()

            assertThat(settings.sensorAzimuthAdjustmentState.value).isWithin(1e-9).of(10.0)
            assertThat(settings.sensorAltitudeAdjustmentState.value).isWithin(1e-9).of(10.0)
            assertThat(receipts).hasSize(1)

            vm.undoAlignmentDrag()
            runCurrent()
            assertThat(settings.sensorAzimuthAdjustmentState.value).isWithin(1e-9).of(0.0)
            assertThat(settings.sensorAltitudeAdjustmentState.value).isWithin(1e-9).of(0.0)
        }

    @Test
    fun `tap-slop noise rolls back silently and two-finger pans never align`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            val receipts = mutableListOf<Unit>()
            backgroundScope.launch { vm.arAlignmentReceipts.collect { receipts += it } }
            vm.setArMode(true)
            vm.onArCameraReady(AR_SPECS)
            runCurrent()

            // A sub-threshold wiggle: applied momentarily, rolled back at gesture end.
            vm.onDrag(0.5f, 0.0f, 1000, pointerCount = 1)
            vm.onGestureEnd()
            runCurrent()
            assertThat(settings.sensorAzimuthAdjustmentState.value).isEqualTo(0.0)
            assertThat(receipts).isEmpty()

            // Two-finger pan noise during a pinch never touches the correction.
            vm.onDrag(-250f, 250f, 1000, pointerCount = 2)
            vm.onGestureEnd()
            runCurrent()
            assertThat(settings.sensorAzimuthAdjustmentState.value).isEqualTo(0.0)
            assertThat(settings.sensorAltitudeAdjustmentState.value).isEqualTo(0.0)
            assertThat(receipts).isEmpty()
        }

    @Test
    fun `manual exposure engages when a dev slider moves and clears back to auto`() =
        testScope.runCurrentTest {
            val vm = viewModel()
            val values = mutableListOf<ArManualExposure?>()
            backgroundScope.launch { vm.arManualExposure.collect { values += it } }
            vm.setArMode(true)
            vm.onArCameraReady(AR_SPECS)
            runCurrent()
            assertThat(values.last()).isNull()

            // Only the shutter slider moves: ISO rides at its default.
            vm.setArShutterFraction(1.0)
            runCurrent()
            assertThat(values.last())
                .isEqualTo(ArManualExposure(iso = 800, exposureTimeNs = 1_000_000_000L))

            vm.setArIsoFraction(1.0)
            runCurrent()
            assertThat(values.last())
                .isEqualTo(ArManualExposure(iso = 6400, exposureTimeNs = 1_000_000_000L))

            // Back to the auto stops: manual clears entirely.
            vm.setArShutterFraction(0.0)
            vm.setArIsoFraction(0.0)
            runCurrent()
            assertThat(values.last()).isNull()
        }

    /**
     * `runTest` on the shared scheduler, cancelling every created view model's scope before
     * `runTest`'s until-idle cleanup runs, so nothing the view model launched (a fling pump
     * mid-decay, flow collectors) can keep the scheduler busy or leak between tests.
     */
    private fun TestScope.runCurrentTest(body: suspend TestScope.() -> Unit) =
        runTest {
            try {
                body()
            } finally {
                createdViewModels.forEach { it.viewModelScope.cancel() }
            }
        }

    companion object {
        private val TIME = Instant.parse("2026-07-03T21:00:00Z")
        private const val TOL = 1e-9

        /** The fake display's frame interval: 60 Hz, so a frame is a round 16 ms. */
        private const val FRAME_MILLIS = 16L

        /** Long enough for a slew to reach its last frame, whose eased t is exactly 1. */
        private const val SLEW_SETTLE_MILLIS = MapViewModel.SLEW_MILLIS + 2 * FRAME_MILLIS

        /** A camera reporting a 40° short-side FOV, 1×–4× zoom, and a manual sensor. */
        private val AR_SPECS =
            ArCameraSpecs(
                shortSideFovDeg = 40.0,
                zoomMin = 1.0,
                zoomMax = 4.0,
                exposureMin = -4,
                exposureMax = 4,
                isoRange = 100..6400,
                exposureTimeRangeNs = 1_000_000L..1_000_000_000L,
            )
    }
}
