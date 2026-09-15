/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.normalizeDegrees
import com.google.android.stardroid.math.rotationMatrix
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyGradient
import com.google.android.stardroid.sensors.MagneticDeclinationSource
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.exp

/** Where the camera comes from (D14): the sensor pipeline, or the user's fingers. */
enum class ReferenceFrame { SENSOR, MANUAL }

/**
 * Whether the layer rail shows its name labels on this chrome reveal, and whether this is the
 * reveal they retire on (D83). [fadingOut] only means anything while [visible].
 */
data class RailLabelState(
    val visible: Boolean,
    val fadingOut: Boolean,
)

/**
 * Owns the map camera (layers-and-app.md): the reference frame, camera resolution from
 * orientation + local frame + FOV in sensor mode, and v1's manual drag/rotate/zoom semantics
 * (`ManualOrientationController`, `MapMover`, `ZoomController`) in manual mode.
 *
 * Pure JVM-testable: sensors, declination, location, and time arrive as injected
 * interfaces/flows; no GL or Android types. Devices without usable sensors boot into
 * [ReferenceFrame.MANUAL] and stay there ([sensorsAvailable] drives the UI warning).
 *
 * Time comes from the shared clock bus (`TimeController`, D42): [timeFlow] paces the local
 * frame and the sky gradient — a lazy tick on the wall clock, fast ticks under time travel —
 * and [now] samples the same clock, so the map, dome, and layers always agree on the instant.
 */
class MapViewModel(
    private val orientationSource: OrientationSource,
    private val declinationSource: MagneticDeclinationSource,
    private val locations: Flow<LatLong>,
    private val settings: Settings,
    private val ephemeris: Ephemeris,
    private val timeFlow: Flow<Instant>,
    private val now: () -> Instant,
    private val analytics: Analytics = NoOpAnalytics,
    private val frameTicker: FrameTicker = ChoreographerFrameTicker,
) : ViewModel() {
    val sensorsAvailable: Boolean = orientationSource.available

    private val _referenceFrame =
        MutableStateFlow(if (sensorsAvailable) ReferenceFrame.SENSOR else ReferenceFrame.MANUAL)
    val referenceFrame: StateFlow<ReferenceFrame> = _referenceFrame.asStateFlow()

    private val _camera = MutableStateFlow(SkyCamera(INITIAL_LOS, INITIAL_UP, INITIAL_FOV_DEG))
    val camera: StateFlow<SkyCamera> = _camera.asStateFlow()

    val nightMode: StateFlow<Boolean> =
        settings.nightMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Whether the user has ever worked the chrome's show/hide toggle. Null until the stored
     * value has been read — the map must not start its auto-hide timer on a guess, or a
     * first-run user loses the chrome before the real value arrives.
     */
    val chromeEverToggled: StateFlow<Boolean?> =
        settings.chromeEverToggled.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onChromeToggledByUser() {
        viewModelScope.launch { settings.setChromeEverToggled(true) }
    }

    /**
     * Whether the rail's name labels should be drawn on this reveal, and whether this is the
     * last one (the map fades them out on that reveal rather than showing them steadily, so
     * their departure is visible instead of them simply being absent next time).
     *
     * Null until the stored count arrives, for the same reason as [chromeEverToggled]: the
     * labels must not flash on and then vanish, or off and then appear, on a guess.
     */
    val railLabelState: StateFlow<RailLabelState?> =
        settings.railLabelReveals
            .map { seen ->
                RailLabelState(
                    visible = seen < RAIL_LABEL_REVEALS,
                    fadingOut = seen == RAIL_LABEL_REVEALS - 1,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Records that the chrome has been revealed with the labels attached. Called once per
     * reveal by the map, and only while the labels are actually showing — a reveal after they
     * have retired must not keep incrementing a counter nothing reads.
     */
    fun onRailLabelsRevealed() {
        viewModelScope.launch { settings.incrementRailLabelReveals() }
    }

    // The drag-to-align correction pair (D64), mirrored from Settings so pointing and the
    // HUD react mid-drag; the DataStore write happens once, at gesture end.
    private val azimuthAdjustment = MutableStateFlow(0.0)
    private val altitudeAdjustment = MutableStateFlow(0.0)

    // ---- Through-camera (AR) mode (camera-ar-mode.md, D64) -------------------------------
    // Session state by decision: the layer always starts off and nothing here persists.

    private val _arMode = MutableStateFlow(false)

    /** Whether the camera layer is on — drives the preview binding and the transparent GL. */
    val arMode: StateFlow<Boolean> = _arMode.asStateFlow()

    private val arScrimSetting = MutableStateFlow(DEFAULT_AR_SCRIM)

    private val _arExposureIndex = MutableStateFlow(0)

    private val _arZoomRatio = MutableStateFlow(1.0)

    // The temporary manual-exposure dev sliders (0 = auto), while night behavior is tuned.
    private val arIsoFractionSetting = MutableStateFlow(0.0)
    private val arShutterFractionSetting = MutableStateFlow(0.0)

    /** The camera zoomRatio the hardware edge applies while the FOV is locked. */
    val arZoomRatio: StateFlow<Double> = _arZoomRatio.asStateFlow()

    /**
     * Camera permission denials, reported by the activity's launcher; `true` means the
     * system will let us ask again (plain snackbar), `false` means permanently denied
     * (snackbar with an app-settings action). Buffered so an emit before the map screen
     * collects isn't dropped.
     */
    val arPermissionDenials = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    // A flow, not a plain field: the camera reports in asynchronously, and the control
    // panel must grow its exposure row the moment the envelope is known.
    private val arSpecs = MutableStateFlow<ArCameraSpecs?>(null)

    /** The running camera's envelope, for the control panel's value readouts. */
    val arCameraSpecs: StateFlow<ArCameraSpecs?> = arSpecs.asStateFlow()

    private var fovBeforeAr: Double? = null

    /** The in-AR control panel's state; null hides the panel (layer off). */
    val arUi: StateFlow<ArUiState?> =
        combine(
            _arMode,
            arScrimSetting,
            _arExposureIndex,
            arSpecs,
            combine(arIsoFractionSetting, arShutterFractionSetting, ::Pair),
        ) { on, scrim, exp, specs, (iso, shutter) ->
            if (!on) {
                null
            } else {
                ArUiState(
                    scrim = scrim,
                    exposureIndex = exp,
                    exposureMin = specs?.exposureMin ?: 0,
                    exposureMax = specs?.exposureMax ?: 0,
                    isoFraction = iso,
                    shutterFraction = shutter,
                    manualExposureSupported =
                        specs?.isoRange != null && specs.exposureTimeRangeNs != null,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The manual exposure the hardware edge applies; null returns the camera to auto.
     * Engaged the moment either dev slider leaves its auto stop — the other axis then rides
     * at a sensible default (ISO 800, 1/30 s) until its own slider moves.
     */
    val arManualExposure: StateFlow<ArManualExposure?> =
        combine(arIsoFractionSetting, arShutterFractionSetting, arSpecs) { isoF, shutterF, specs ->
            val isoRange = specs?.isoRange
            val timeRange = specs?.exposureTimeRangeNs
            if (isoRange == null || timeRange == null) {
                null
            } else if (isoF <= 0.0 && shutterF <= 0.0) {
                null
            } else {
                ArManualExposure(
                    iso =
                        if (isoF > 0.0) {
                            ArExposureMath.isoForFraction(isoRange, isoF)
                        } else {
                            DEFAULT_MANUAL_ISO.coerceIn(isoRange.first, isoRange.last)
                        },
                    exposureTimeNs =
                        if (shutterF > 0.0) {
                            ArExposureMath.exposureTimeForFraction(timeRange, shutterF)
                        } else {
                            DEFAULT_MANUAL_SHUTTER_NS.coerceIn(
                                timeRange.first,
                                timeRange.last,
                            )
                        },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Temporary dev control: manual ISO slider fraction, 0 = auto. */
    fun setArIsoFraction(fraction: Double) {
        arIsoFractionSetting.value = fraction.coerceIn(0.0, 1.0)
    }

    /** Temporary dev control: manual shutter slider fraction, 0 = auto. */
    fun setArShutterFraction(fraction: Double) {
        arShutterFractionSetting.value = fraction.coerceIn(0.0, 1.0)
    }

    /** What the render binder feeds `SkyRenderer.setRenderState`. */
    val renderState: Flow<RenderState> =
        combine(
            settings.nightMode,
            settings.fontSize,
            skyGradients(),
            _arMode,
            arScrimSetting,
        ) { night, font, gradient, ar, scrim ->
            RenderState(
                nightMode = night,
                labelScaleFactor = font.scale,
                skyGradient = gradient,
                transparentBackground = ar,
                // Night mode imposes the scrim floor (D64): the un-reddened video must not
                // wreck dark adaptation — dim further, never brighter.
                cameraScrim =
                    when {
                        !ar -> 0.0
                        night -> maxOf(scrim, NIGHT_AR_SCRIM_FLOOR)
                        else -> scrim
                    },
            )
        }

    /**
     * The map HUD's readout (map-hud.md, D65): the screen centre in equatorial and horizontal
     * coordinates, the FOV, and the drag-to-align correction (D64). The camera is sampled at
     * [HUD_SAMPLE_MILLIS] so sensor-rate updates never drive per-sample text recomposition;
     * while the camera is still, the clock-bus tick keeps alt/az drifting with the sky. Null
     * until the first sample lands. Cold below (`WhileSubscribed`): the HUD is only composed
     * while the chrome is up, so nothing computes when it isn't.
     */
    @OptIn(FlowPreview::class)
    val hudState: StateFlow<HudState?> =
        combine(
            camera.sample(HUD_SAMPLE_MILLIS),
            timeFlow,
            azimuthAdjustment,
            altitudeAdjustment,
        ) { cam, _, correctionAz, correctionAlt ->
            val frame = localFrame
            val los = cam.lineOfSight
            val raDec = RaDec.fromGeocentricVector(los)
            HudState(
                raDeg = raDec.raDeg,
                decDeg = raDec.decDeg,
                altDeg = asin((los dot frame.up).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES,
                azDeg =
                    normalizeDegrees(
                        atan2(los dot frame.trueEast, los dot frame.trueNorth) *
                            RADIANS_TO_DEGREES,
                    ),
                fovDeg = cam.fovDeg,
                fovLocked = _arMode.value && arSpecs.value?.shortSideFovDeg != null,
                correctionAzDeg = correctionAz,
                correctionAltDeg = correctionAlt,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The sky-gradient dome input while the preference is on, `null` while it's off. The sun's
     * geocentric direction rides the shared clock bus (paying off the D41 debt): under time
     * travel the dome tracks the same accelerated instants the layers see, so it can never
     * disagree with `SolarSystemLayer`'s sun image.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun skyGradients(): Flow<SkyGradient?> =
        settings.showSkyGradient.flatMapLatest { on ->
            if (!on) return@flatMapLatest flowOf(null)
            timeFlow.map { time ->
                SkyGradient(
                    ephemeris.geocentricPosition(SolarSystemBody.SUN, time).toGeocentricVector(),
                )
            }
        }

    @Volatile
    private var currentLocation = LocationController.DEFAULT_LOCATION

    @Volatile
    private var viewDirection = ViewDirectionMode.STANDARD

    @Volatile
    private var useMagneticCorrection = true

    @Volatile
    private var autoLevelHorizon = true

    private var flingJob: Job? = null

    private var levelJob: Job? = null
    private var slewJob: Job? = null

    @Volatile
    private var localFrame = SkyModel.localFrame(now(), LocationController.DEFAULT_LOCATION)

    init {
        // Watch the camera rather than the pinch handler: `onStretch` is only one of several
        // things that change the FOV (search fly-to picks a per-object zoom, the AR lock
        // drives it from the lens), and a hint about zoom should not depend on *how* the user
        // got there. One collector covers every path, present and future.
        viewModelScope.launch {
            if (settings.labelSizeHintShown.first()) return@launch
            _camera.first { it.fovDeg <= LABEL_HINT_FOV_DEG }
            settings.setLabelSizeHintShown()
            labelSizeHints.tryEmit(Unit)
        }
        viewModelScope.launch {
            locations.collect {
                currentLocation = it
                refreshLocalFrame()
            }
        }
        // The clock bus paces the local frame: every ~minute in real time (the sky drifts
        // ~0.25°/min), every tick under time travel so sensor pointing sweeps with the clock.
        viewModelScope.launch {
            timeFlow.collect { time -> refreshLocalFrame(time) }
        }
        viewModelScope.launch {
            _referenceFrame.collectLatest { frame ->
                if (frame == ReferenceFrame.SENSOR) collectSensors()
            }
        }
        viewModelScope.launch {
            settings.viewDirectionMode.collect { viewDirection = it }
        }
        viewModelScope.launch {
            settings.autoLevelHorizon.collect { autoLevelHorizon = it }
        }
        // Declination inputs: the correction toggle and the drag-to-align azimuth offset
        // (D64, the successor of v1's manual compass adjustment) both invalidate the cached
        // declination and rebuild the local frame, as v1's preference listener did.
        viewModelScope.launch {
            combine(
                settings.useMagneticCorrection,
                settings.sensorAzimuthAdjustmentDeg,
                ::Pair,
            ).collect { (use, azimuthDeg) ->
                useMagneticCorrection = use
                azimuthAdjustment.value = azimuthDeg
                declinationCache = null
                refreshLocalFrame()
            }
        }
        viewModelScope.launch {
            settings.sensorAltitudeAdjustmentDeg.collect { altitudeDeg ->
                altitudeAdjustment.value = altitudeDeg
                reprojectSensorCamera()
            }
        }
    }

    fun setNightMode(enabled: Boolean) {
        logMenuItem(AnalyticsEvents.TOGGLED_NIGHT_MODE_LABEL)
        viewModelScope.launch { settings.setNightMode(enabled) }
    }

    private var alignmentBeforeReset: Pair<Double, Double>? = null

    /**
     * Zeroes the drag-to-align correction from the HUD's reset (D65): both axes clear
     * together, remembering the pair for [undoAlignmentReset]'s snackbar action.
     */
    fun resetAlignment() {
        alignmentBeforeReset = azimuthAdjustment.value to altitudeAdjustment.value
        applyAlignment(0.0, 0.0)
        persistAlignment()
    }

    /** Restores the correction pair the last [resetAlignment] cleared (snackbar Undo). */
    fun undoAlignmentReset() {
        val previous = alignmentBeforeReset ?: return
        alignmentBeforeReset = null
        applyAlignment(previous.first, previous.second)
        persistAlignment()
    }

    /** Fired at the end of each drag-to-align gesture; the map shows the undo receipt. */
    val arAlignmentReceipts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Fired once, ever, the first time the view zooms deep enough that the user would expect
     * the labels to have grown with the sky. Sky labels are deliberately zoom-independent —
     * they annotate the view, they are not objects in it — but that reads as a bug unless you
     * know the size is a preference. The map turns this into a snackbar linking to the setting;
     * catching the user at the moment of the zoom is when the answer is actually wanted.
     *
     * Emitted from a camera collector in `init`, so every route to a deep zoom counts — see
     * there for why this is not hung off the pinch handler.
     *
     * `replay = 1`, unlike the gesture-driven receipts above: those are emitted by a touch, so
     * the map is composed and collecting by construction. This one fires from `init`, which
     * can easily beat the map's collector into existence — and a zero-replay `SharedFlow`
     * drops an emission that nobody is subscribed to, which is exactly what made the hint
     * never appear. The replay bridges that startup gap only: the map calls
     * `resetReplayCache()` as soon as it receives the hint, because its collector restarts on
     * every re-composition (returning from Settings or the gallery) and would otherwise be
     * handed the cached value again each time. The preference latch in `init` stops the hint
     * recurring across launches; clearing the cache stops it recurring within one.
     */
    val labelSizeHints = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    private var alignmentBeforeDrag: Pair<Double, Double>? = null

    private var lastAlignmentGestureStart: Pair<Double, Double>? = null

    /**
     * One drag step of drag-to-align (camera-ar-mode.md/D64): the drawn sky tracks the
     * finger. Pixels convert through the FOV over the short side exactly as [onDrag]'s
     * manual math does, so alignment dragging feels identical to map dragging; azimuth
     * wraps (it is periodic), altitude clamps to a sane correction range. Values apply
     * live through the local frame; DataStore persists once, at gesture end.
     */
    private fun onAlignDrag(
        dxPixels: Float,
        dyPixels: Float,
        screenShortSidePx: Int,
    ) {
        if (screenShortSidePx <= 0) return
        if (alignmentBeforeDrag == null) {
            alignmentBeforeDrag = azimuthAdjustment.value to altitudeAdjustment.value
        }
        val degreesPerPixel = _camera.value.fovDeg / screenShortSidePx
        // Dragging right must move the drawn sky right: a positive azimuth correction
        // swings the computed pointing right (sky left), so the drag subtracts.
        val azimuth = azimuthAdjustment.value - dxPixels * degreesPerPixel
        applyAlignment(
            normalizeDegrees(azimuth + 180.0) - 180.0,
            (altitudeAdjustment.value + dyPixels * degreesPerPixel)
                .coerceIn(-MAX_ALTITUDE_ADJUSTMENT_DEG, MAX_ALTITUDE_ADJUSTMENT_DEG),
        )
    }

    /** The receipt snackbar's Undo: rolls back the whole last drag gesture. */
    fun undoAlignmentDrag() {
        val previous = lastAlignmentGestureStart ?: return
        lastAlignmentGestureStart = null
        applyAlignment(previous.first, previous.second)
        persistAlignment()
    }

    private fun applyAlignment(
        azimuthDeg: Double,
        altitudeDeg: Double,
    ) {
        azimuthAdjustment.value = azimuthDeg
        altitudeAdjustment.value = altitudeDeg
        refreshLocalFrame()
        reprojectSensorCamera()
    }

    private fun persistAlignment() {
        val azimuthDeg = azimuthAdjustment.value
        val altitudeDeg = altitudeAdjustment.value
        viewModelScope.launch {
            settings.setSensorAzimuthAdjustmentDeg(azimuthDeg)
            settings.setSensorAltitudeAdjustmentDeg(altitudeDeg)
        }
    }

    fun setReferenceFrame(frame: ReferenceFrame) {
        if (frame == ReferenceFrame.SENSOR && !sensorsAvailable) return
        if (frame != _referenceFrame.value) {
            analytics.trackEvent(
                AnalyticsEvents.MANUAL_MODE_TOGGLED_EVENT,
                mapOf(
                    AnalyticsEvents.MANUAL_MODE_ENABLED to
                        (frame == ReferenceFrame.MANUAL).toString(),
                ),
            )
        }
        // AR requires sensor mode (D64): taking the map by hand turns the camera layer off.
        if (frame == ReferenceFrame.MANUAL && _arMode.value) setArMode(false)
        stopFling()
        stopLeveling()
        _referenceFrame.value = frame
    }

    /**
     * Toggles the camera layer (camera-ar-mode.md/D64). Enabling requires usable sensors and
     * forces sensor mode — the caller checks the frame beforehand if it wants to announce the
     * switch — and remembers the map FOV so disabling restores the user's zoom. Returns false
     * when enabling is impossible (no sensors).
     */
    fun setArMode(on: Boolean): Boolean {
        if (on == _arMode.value) return true
        if (on) {
            if (!sensorsAvailable) return false
            if (_referenceFrame.value != ReferenceFrame.SENSOR) {
                setReferenceFrame(ReferenceFrame.SENSOR)
            }
            fovBeforeAr = _camera.value.fovDeg
            _arZoomRatio.value = 1.0
            arIsoFractionSetting.value = 0.0
            arShutterFractionSetting.value = 0.0
            _arMode.value = true
            applyArFov()
        } else {
            _arMode.value = false
            arSpecs.value = null
            _arZoomRatio.value = 1.0
            fovBeforeAr?.let { fov ->
                val cam = _camera.value
                _camera.value = SkyCamera(cam.lineOfSight, cam.up, fov)
            }
            fovBeforeAr = null
        }
        return true
    }

    /** The hardware edge reports the running camera's envelope; the FOV lock engages here. */
    fun onArCameraReady(specs: ArCameraSpecs) {
        if (!_arMode.value) return
        arSpecs.value = specs
        _arZoomRatio.value = 1.0
        _arExposureIndex.value = 0
        applyArFov()
    }

    /** Camera dimmer (the GL scrim); clamped to the slider's range. */
    fun setArScrim(value: Double) {
        arScrimSetting.value = value.coerceIn(0.0, MAX_AR_SCRIM)
    }

    /** AE-compensation slider; clamped to the camera's reported range. */
    fun setArExposureIndex(index: Int) {
        val specs = arSpecs.value ?: return
        _arExposureIndex.value = index.coerceIn(specs.exposureMin, specs.exposureMax)
    }

    /** The exposure index the hardware edge applies. */
    val arExposureIndex: StateFlow<Int> = _arExposureIndex.asStateFlow()

    /** Map FOV = camera FOV / zoom ratio while the lock is engaged (D64). */
    private fun applyArFov() {
        if (!_arMode.value) return
        val baseFov = arSpecs.value?.shortSideFovDeg ?: return
        val cam = _camera.value
        val fov = (baseFov / _arZoomRatio.value).coerceIn(MIN_FOV_DEG, MAX_FOV_DEG)
        _camera.value = SkyCamera(cam.lineOfSight, cam.up, fov)
    }

    /** v1's menu-item event; the map chrome's buttons funnel through here. */
    fun logMenuItem(label: String) {
        analytics.trackEvent(
            AnalyticsEvents.MENU_ITEM_EVENT,
            mapOf(AnalyticsEvents.MENU_ITEM_EVENT_VALUE to label),
        )
    }

    /** The no-usable-sensors dialog came up (v1 logged the same on its warning toast). */
    fun logNoSensorsWarning() {
        analytics.trackEvent(AnalyticsEvents.NO_SENSORS_WARNING_EVENT)
    }

    fun toggleReferenceFrame() =
        setReferenceFrame(
            when (_referenceFrame.value) {
                ReferenceFrame.SENSOR -> ReferenceFrame.MANUAL
                ReferenceFrame.MANUAL -> ReferenceFrame.SENSOR
            },
        )

    // ---- Gestures (v1 semantics; drag/rotate/fling no-op while sensors own the camera) ---

    /**
     * One-finger drag in screen pixels. v1 `MapMover.onDrag`: pixels convert to radians via the
     * FOV over the screen side the FOV spans — [SkyCamera.fovDeg] spans the **short** side — so
     * the sky tracks the finger ~1:1 at any zoom.
     */
    fun onDrag(
        dxPixels: Float,
        dyPixels: Float,
        screenShortSidePx: Int,
        pointerCount: Int = 1,
    ) {
        // Drag-to-align (D64): while the camera layer is on, a one-finger drag *is* the
        // alignment adjustment. Two-finger pans stay zoom/rotate noise, never alignment.
        if (_arMode.value && _referenceFrame.value == ReferenceFrame.SENSOR) {
            if (pointerCount == 1) onAlignDrag(dxPixels, dyPixels, screenShortSidePx)
            return
        }
        if (_referenceFrame.value != ReferenceFrame.MANUAL || screenShortSidePx <= 0) return
        val pixelsToRadians = _camera.value.fovDeg * DEGREES_TO_RADIANS / screenShortSidePx
        val pitchRadians = -dyPixels * pixelsToRadians
        val yawRadians = -dxPixels * pixelsToRadians

        val cam = _camera.value
        val pitchedLos = (cam.lineOfSight - cam.up * pitchRadians).normalized()
        val pitchedUp = (cam.up + cam.lineOfSight * pitchRadians).normalized()
        val horizontal = pitchedLos cross pitchedUp
        val finalLos = (pitchedLos + horizontal * yawRadians).normalized()

        _camera.value = SkyCamera(finalLos, pitchedUp, cam.fovDeg)
    }

    /**
     * Two-finger stretch by [ratio] > 1 zooms in. v1 `MapMover.onStretch` → `zoomBy(1/ratio)`.
     * Works in **both** frames: v1 never disables its `ZoomController` in auto mode, and the
     * sensor pipeline preserves the current FOV.
     */
    fun onStretch(ratio: Float) {
        if (ratio <= 0f || ratio.isNaN()) return
        // While the AR FOV lock is engaged, pinch drives the *camera's* digital zoom and the
        // map FOV follows the lens (D64); the zoom range becomes the camera's, far narrower
        // than the map's — you can't point a camera at a 90° sky anyway.
        val specs = arSpecs.value
        if (_arMode.value && specs?.shortSideFovDeg != null) {
            _arZoomRatio.value =
                (_arZoomRatio.value * ratio).coerceIn(specs.zoomMin, specs.zoomMax)
            applyArFov()
            return
        }
        val cam = _camera.value
        val fov = (cam.fovDeg / ratio).coerceIn(MIN_FOV_DEG, MAX_FOV_DEG)
        _camera.value = SkyCamera(cam.lineOfSight, cam.up, fov)
    }

    /** Two-finger rotate; screen-clockwise [degrees] rotates the sky with the fingers (v1). */
    fun onRotate(degrees: Float) {
        if (_referenceFrame.value != ReferenceFrame.MANUAL || degrees.isNaN()) return
        val cam = _camera.value
        val newUp = (rotationMatrix(degrees.toDouble(), cam.lineOfSight) * cam.up).normalized()
        _camera.value = SkyCamera(cam.lineOfSight, newUp, cam.fovDeg)
    }

    /**
     * Momentum after a one-finger drag lifts, in screen pixels/second. Port of v1's `Flinger`,
     * with its 20 Hz pump replaced by a per-frame one (D93): the velocity decays continuously
     * at [FLING_DECAY_PER_SECOND], which is v1's 1.1× per 50 ms tick expressed as a rate, so
     * the fling feels the same but moves on every drawn frame instead of every sixth one. A new
     * touch-down or a frame switch stops it.
     */
    fun onFling(
        vxPixelsPerSec: Float,
        vyPixelsPerSec: Float,
        screenShortSidePx: Int,
    ) {
        if (_referenceFrame.value != ReferenceFrame.MANUAL) return
        stopFling()
        flingJob =
            viewModelScope.launch {
                var vx = vxPixelsPerSec
                var vy = vyPixelsPerSec
                var last = frameTicker.awaitFrame()
                while (vx * vx + vy * vy >= FLING_STOP_SPEED_SQ) {
                    val frame = frameTicker.awaitFrame()
                    val dt = secondsSince(last, frame)
                    last = frame
                    onDrag(vx * dt, vy * dt, screenShortSidePx)
                    val decay = exp(-FLING_DECAY_PER_SECOND * dt)
                    vx *= decay
                    vy *= decay
                }
            }
    }

    /**
     * Elapsed seconds between two frame timestamps, clamped to [MAX_FRAME_SECONDS]. A stalled
     * frame — a GC pause, a backgrounded app — must slow an animation down, never teleport the
     * camera by a whole second of momentum in one step.
     */
    private fun secondsSince(
        previousNanos: Long,
        frameNanos: Long,
    ): Float = ((frameNanos - previousNanos) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)

    /**
     * Brings a fling — and any in-flight search slew — to a dead stop (v1 `Flinger.stop`,
     * fired on the next touch-down: the user's finger always wins over camera momentum).
     */
    fun stopFling() {
        flingJob?.cancel()
        flingJob = null
        slewJob?.cancel()
        slewJob = null
    }

    /**
     * Slews the camera to a search target over [SLEW_MILLIS]: a smoothstep-eased great-circle
     * rotation carrying the up vector along (so orthogonality is preserved by construction),
     * with the FOV easing in step. Fired only in manual mode — in sensor mode the arrow guides
     * the user's arm instead, and the camera stays the sensors' (v1 `activateSearchTarget`).
     * v1's `TeleportingController.teleport` jumped instantly; the scroll is a deliberate v2
     * improvement (PR #23 review). A touch-down or frame switch cancels the slew mid-flight.
     *
     * [fovDeg], when present, is the catalog's per-object `search_level`; v1 shipped that data
     * but never wired the zoom (`AstronomicalRenderable.getSearchLevel` stayed commented out) —
     * v2 completes the thought, in manual mode only.
     */
    fun aimAt(
        direction: Vector3,
        fovDeg: Double? = null,
    ) {
        if (_referenceFrame.value != ReferenceFrame.MANUAL) return
        stopFling()
        val targetLos = direction.normalized()
        if (targetLos == Vector3.ZERO) return
        val start = _camera.value
        val targetFov = fovDeg?.coerceIn(MIN_FOV_DEG, MAX_FOV_DEG) ?: start.fovDeg
        val dot = (start.lineOfSight dot targetLos).coerceIn(-1.0, 1.0)
        val angleDeg = acos(dot) * RADIANS_TO_DEGREES
        // Slew axis, ordered target-first because [rotationMatrix] rotates the frame — it
        // carries vectors by −angle. For a 180° about-face the cross product vanishes and any
        // perpendicular axis traces a valid great circle — the current up is one, and it keeps
        // up fixed.
        val cross = targetLos cross start.lineOfSight
        val axis = if (cross.length2 < SLEW_AXIS_DEGENERATE_TOL) start.up else cross.normalized()
        slewJob =
            viewModelScope.launch {
                val began = frameTicker.awaitFrame()
                var t = 0.0
                while (t < 1.0) {
                    val frame = frameTicker.awaitFrame()
                    t =
                        ((frame - began) / NANOS_PER_MILLI / SLEW_MILLIS.toDouble())
                            .coerceIn(0.0, 1.0)
                    val eased = t * t * (3.0 - 2.0 * t)
                    val rotation = rotationMatrix(angleDeg * eased, axis)
                    _camera.value =
                        SkyCamera(
                            (rotation * start.lineOfSight).normalized(),
                            (rotation * start.up).normalized(),
                            start.fovDeg + (targetFov - start.fovDeg) * eased,
                        )
                }
            }
    }

    /**
     * A manual gesture ended: if auto-level is on, spring the horizon back to level — the
     * port of v1's `HorizonLeveler` (20 Hz, 20% of the remaining angle per tick, stop under
     * 0.1°). The target up is the zenith projected into the view plane; v1's snap of the
     * phone's roll to the nearest 90° is dropped — manual mode has no live phone orientation
     * here, and the Compose activity rotates with the display, so screen-up is the natural up.
     */
    fun onGestureEnd() {
        // A finished drag-to-align gesture (D64): persist and offer undo — unless the whole
        // gesture was tap-slop noise, which quietly rolls back instead of snackbaring.
        val dragStart = alignmentBeforeDrag
        if (dragStart != null) {
            alignmentBeforeDrag = null
            val movedDeg =
                abs(azimuthAdjustment.value - dragStart.first) +
                    abs(altitudeAdjustment.value - dragStart.second)
            if (movedDeg >= ALIGN_RECEIPT_MIN_DEG) {
                lastAlignmentGestureStart = dragStart
                persistAlignment()
                arAlignmentReceipts.tryEmit(Unit)
            } else {
                applyAlignment(dragStart.first, dragStart.second)
            }
            return
        }
        if (_referenceFrame.value != ReferenceFrame.MANUAL || !autoLevelHorizon) return
        stopLeveling()
        levelJob =
            viewModelScope.launch {
                var last = frameTicker.awaitFrame()
                while (true) {
                    val frame = frameTicker.awaitFrame()
                    val dt = secondsSince(last, frame)
                    last = frame
                    val cam = _camera.value
                    val zenith = localFrame.up
                    val projected = zenith - cam.lineOfSight * (zenith dot cam.lineOfSight)
                    // Looking (nearly) straight up or down: no defined level, as in v1.
                    if (projected.length2 < LEVEL_DEGENERATE_TOL) break
                    val target = projected.normalized()
                    // Signed right-handed angle about the line of sight from up to target.
                    val sinAngle = (cam.up cross target) dot cam.lineOfSight
                    val cosAngle = cam.up dot target
                    val angleDeg = atan2(sinAngle, cosAngle) * RADIANS_TO_DEGREES
                    // A diagonal fling's pitch+yaw composition isn't a true combined rotation
                    // (MapViewModel#onDrag), so it reintroduces a little roll on every frame it
                    // runs. Don't declare victory on an instantaneous close-enough reading while
                    // the fling is still actively re-tilting the horizon underneath us (#960) —
                    // only stop once the fling itself is done.
                    if (abs(angleDeg) < LEVEL_STOP_DEG && flingJob?.isActive != true) break
                    // The share of the remaining angle this frame takes: v1's flat 20% per
                    // 50 ms tick, re-expressed as a continuous rate so the spring settles in
                    // the same time whatever the refresh rate (D93).
                    val step = 1.0 - exp(-LEVEL_DECAY_PER_SECOND * dt)
                    // Negated: `rotationMatrix` rotates clockwise about the axis (v1's
                    // transposed convention — HorizonLeveler flipped the sign the same way).
                    val newUp =
                        (
                            rotationMatrix(-angleDeg * step, cam.lineOfSight) * cam.up
                        ).normalized()
                    _camera.value = SkyCamera(cam.lineOfSight, newUp, cam.fovDeg)
                }
            }
    }

    /**
     * A double tap in manual mode flips the auto-level preference (the same one Settings
     * shows). Returns the new value so the UI can confirm which way it went; turning it off
     * also halts any spring already running.
     */
    fun toggleAutoLevelHorizon(): Boolean {
        val enabled = !autoLevelHorizon
        if (!enabled) stopLeveling()
        viewModelScope.launch { settings.setAutoLevelHorizon(enabled) }
        return enabled
    }

    /** Stops any horizon-leveling spring (new touch-down, teleport, or frame switch). */
    fun stopLeveling() {
        levelJob?.cancel()
        levelJob = null
    }

    // ---- Sensor mode --------------------------------------------------------------------

    private suspend fun collectSensors() {
        orientationSource.orientations().collect { orientation ->
            lastOrientation = orientation
            resolveSensorCamera(orientation)
        }
    }

    @Volatile
    private var lastOrientation: Matrix3? = null

    /**
     * Resolves [orientation] to the camera, applying the drag-to-align altitude correction
     * (D64) as a rotation about the screen-right axis after `SkyModel.pointing` — the yaw
     * half lives in the local frame via [declinationFor].
     */
    private fun resolveSensorCamera(orientation: Matrix3) {
        val pointing = SkyModel.pointing(localFrame, orientation, viewDirection)
        var los = pointing.lineOfSight
        var up = pointing.perpendicular
        val altitudeDeg = altitudeAdjustment.value
        if (altitudeDeg != 0.0) {
            val right = los cross up
            if (right.length2 > ALIGN_DEGENERATE_TOL) {
                // rotationMatrix carries vectors by −angle (see aimAt), so the negation
                // makes a positive altitude correction raise the view toward the zenith.
                val rotation = rotationMatrix(-altitudeDeg, right.normalized())
                los = (rotation * los).normalized()
                up = (rotation * up).normalized()
            }
        }
        _camera.value = SkyCamera(los, up, _camera.value.fovDeg)
    }

    /** Re-resolves the camera from the last sample after a mid-drag correction change. */
    private fun reprojectSensorCamera() {
        if (_referenceFrame.value != ReferenceFrame.SENSOR) return
        lastOrientation?.let { resolveSensorCamera(it) }
    }

    // [time] defaults to a fresh sample for non-clock triggers (a location change), but the
    // clock-bus collector passes the exact emitted Instant so the local frame stays phase-locked
    // to the layers and dome under fast time travel.
    private fun refreshLocalFrame(time: Instant = now()) {
        val location = currentLocation
        localFrame = SkyModel.localFrame(time, location, declinationFor(location, time))
    }

    private class DeclinationCache(
        val location: LatLong,
        val timeMillis: Long,
        val declination: Double,
    )

    @Volatile
    private var declinationCache: DeclinationCache? = null

    /**
     * Magnetic declination changes over years, so under time travel's 30 Hz local-frame refresh
     * the geomagnetic model would be re-solved thousands of times for a value that barely moves.
     * Recompute only when the location changes or the simulated time drifts by over a day. The
     * cache is an immutable snapshot behind a single volatile reference, so it can never be read
     * half-updated.
     *
     * The correction toggle zeroes the model's declination and the drag-to-align azimuth
     * correction (D64) adds a fixed offset on top of either base — v1's calculator switcher +
     * offset wrapper, with the drag as the offset's only setter. Only the base is cached; the
     * preference collector nulls the cache when either input changes.
     */
    private fun declinationFor(
        location: LatLong,
        time: Instant,
    ): Double {
        if (!useMagneticCorrection) return azimuthAdjustment.value
        val timeMillis = time.toEpochMilliseconds()
        val cache = declinationCache
        if (cache != null &&
            location == cache.location &&
            kotlin.math.abs(timeMillis - cache.timeMillis) <= MILLISECONDS_PER_DAY
        ) {
            return cache.declination + azimuthAdjustment.value
        }
        val declination = declinationSource.declinationDeg(location, time)
        declinationCache = DeclinationCache(location, timeMillis, declination)
        return declination + azimuthAdjustment.value
    }

    companion object {
        /**
         * v1's initial camera: due south-ish along the equator, celestial north up.
         *
         * v1's 45° was its *vertical* FOV, and its projection never rotated. Under D36's
         * short-side convention the short side is the **width** in portrait, so carrying 45°
         * across unchanged framed ~45° horizontally and ~85° vertically — roughly four times
         * v1's sky area, which read as cluttered. 21.1° reproduces v1's portrait framing
         * (45° vertical on a 20:9 display) with zoom still rotation-invariant.
         */
        val INITIAL_LOS = Vector3(1.0, 0.0, 0.0)
        val INITIAL_UP = Vector3.UNIT_Z
        const val INITIAL_FOV_DEG = 21.1

        /** v1 `ZoomController.MAX_ZOOM_OUT`. */
        const val MAX_FOV_DEG = 90.0

        /**
         * v1 had no zoom-in stop; v2 floors the FOV so `SkyCamera`'s `fovDeg > 0` holds.
         *
         * D86 dropped this from 0.5°, and on-device evaluation dropped it again from 0.1°. At
         * 0.1° only Jupiter and Saturn resolve as discs; Mars and Mercury are 21 px and the ice
         * giants sit on the absolute floor. At 0.03° Mars and Mercury reach 71 px, Uranus 31 and
         * Neptune 20, while Jupiter's 256-pixel texture is magnified only 1.4x. Going further to
         * 0.02° would magnify it 2.1x and undo the sharpness the re-derived art (D85) bought.
         */
        const val MIN_FOV_DEG = 0.03

        /**
         * Where the label-size hint fires: half of [INITIAL_FOV_DEG], i.e. the sky has doubled
         * in scale around labels that have not moved — enough to notice, and about one
         * deliberate stretch from the opening framing.
         *
         * Was a third, which needed a 3× zoom. That was chosen while search fly-to was assumed
         * to be a second route in; it is not — the DSO catalog ships no `search_level`, so
         * `aimAt` keeps the current FOV and pinch is the only way here. With one route, the
         * threshold has to be comfortably reachable rather than merely possible.
         */
        const val LABEL_HINT_FOV_DEG = INITIAL_FOV_DEG / 2.0

        /**
         * How many chrome reveals carry the rail's name labels (D83). Three is enough to
         * connect each icon to its name without the text becoming furniture; the third fades
         * them out, so the user sees them go rather than just finding them gone.
         */
        const val RAIL_LABEL_REVEALS = 3

        private const val MILLISECONDS_PER_DAY = 24L * 3600L * 1000L

        /**
         * The HUD's sample cadence (map-hud.md, D65): fast enough to read as live while
         * panning, far below the sensor rate. Values render in tabular figures, so the
         * tick doesn't jitter the layout.
         */
        const val HUD_SAMPLE_MILLIS = 150L

        /** The riding-along defaults when only one manual dev slider has moved. */
        private const val DEFAULT_MANUAL_ISO = 800
        private const val DEFAULT_MANUAL_SHUTTER_NS = 33_333_333L

        /** Camera dimmer default: "map first, camera as context" (camera-ar-mode.md). */
        const val DEFAULT_AR_SCRIM = 0.35

        /** The dimmer slider's ceiling — full black would beg "why is my camera broken". */
        const val MAX_AR_SCRIM = 0.8

        /** Night mode's minimum camera dimming (D64): protect dark adaptation. */
        const val NIGHT_AR_SCRIM_FLOOR = 0.65

        /** Pitch error is small (D64 caveat); corrections beyond this are finger slips. */
        const val MAX_ALTITUDE_ADJUSTMENT_DEG = 45.0

        /** Below this a "drag" was tap noise: silently rolled back, no receipt. */
        const val ALIGN_RECEIPT_MIN_DEG = 0.05

        /** Squared-length floor below which the screen-right axis is degenerate. */
        private const val ALIGN_DEGENERATE_TOL = 1e-12

        /** The search slew: one eased second, stepped once per drawn frame (D93). */
        const val SLEW_MILLIS = 1000L

        /** Below this the slew endpoints are (anti)parallel and the cross product is noise. */
        private const val SLEW_AXIS_DEGENERATE_TOL = 1e-12

        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val NANOS_PER_MILLI = 1_000_000.0

        /**
         * The longest step any animation will take from one frame to the next. A frame this
         * late means the app was stalled, not that the user wants a tenth of a second of
         * momentum applied at once.
         */
        private const val MAX_FRAME_SECONDS = 0.1f

        // v1 `Flinger`: velocity ÷ 1.1 per 50 ms tick, stop below √10 px/s. The divisor is
        // carried over as the equivalent continuous rate, 20·ln(1.1) per second, so the fling
        // decays on the same curve without being pinned to v1's 20 Hz sampling of it (D93).
        private const val FLING_DECAY_PER_SECOND = 1.9062036f
        private const val FLING_STOP_SPEED_SQ = 10f

        // v1 `HorizonLeveler`: a spring closing 20% of the angle per 50 ms tick, done at 0.1°.
        // Rate form, as above: −20·ln(0.8) per second.
        private const val LEVEL_DECAY_PER_SECOND = 4.4628710
        private const val LEVEL_STOP_DEG = 0.1
        private const val LEVEL_DEGENERATE_TOL = 0.001
    }
}
