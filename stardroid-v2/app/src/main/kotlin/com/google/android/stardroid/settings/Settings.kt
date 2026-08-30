/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.settings

import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.render.api.LayerId
import kotlinx.coroutines.flow.Flow

/**
 * Sky-label size (v1's `font_size`); [scale] feeds `RenderState.labelScaleFactor` (D18).
 * The ladder is re-based from v1's 0.75–2.0: the old LARGE (1.5) is the new MEDIUM default —
 * user feedback was that the original sizes ran too small on modern screens.
 */
enum class FontSize(val scale: Double) {
    SMALL(1.0),
    MEDIUM(1.5),
    LARGE(2.0),
    EXTRA_LARGE(2.5),
}

/** How hard night mode dims the screen (v1's `auto_dimness`). */
enum class AutoDimness {
    /** Leave the brightness at the system setting. */
    SYSTEM,

    /** As dim as the platform allows. */
    DIM,

    /** v1's original hand-tuned dim level (20/255). */
    CLASSIC,
}

/** Sensor sampling rate for the classic accelerometer+magnetometer path (v1 `sensor_speed`). */
enum class SensorSpeed {
    SLOW,
    STANDARD,
    FAST,
}

/** Smoothing strength for the classic sensor path (v1 `sensor_damping`). */
enum class SensorDamping {
    STANDARD,
    HIGH,
    EXTRA_HIGH,
    REALLY_HIGH,
}

/**
 * The app's persisted preferences, as flows so consumers react to changes from any writer
 * (map controls now, the settings screen later). Keys are new — v1's `source_provider.N`
 * SharedPreferences are deliberately not migrated (D1).
 */
interface Settings {
    /** Whether [id]'s layer is drawn. Layers default to visible, as in v1. */
    fun layerEnabled(id: LayerId): Flow<Boolean>

    suspend fun setLayerEnabled(
        id: LayerId,
        enabled: Boolean,
    )

    /**
     * The chosen option for one of [id]'s declared parameters (D87), or [default] if the user has
     * never set it. Values are the parameter's option keys.
     */
    fun layerParameter(
        id: LayerId,
        key: String,
        default: String,
    ): Flow<String>

    suspend fun setLayerParameter(
        id: LayerId,
        key: String,
        option: String,
    )

    /** One preference drives both worlds: the Compose theme and `RenderState.nightMode`. */
    val nightMode: Flow<Boolean>

    suspend fun setNightMode(enabled: Boolean)

    /**
     * Whether the sun-position sky dome is drawn. Not a layer (layers-and-app.md: the gradient
     * is a render-state), but it sits alongside the layer toggles in the UI, as in v1.
     */
    val showSkyGradient: Flow<Boolean>

    suspend fun setShowSkyGradient(enabled: Boolean)

    /** Whether a sky tap identifies the nearest object (v1's `show_object_info_on_tap2`). */
    val tapToIdentify: Flow<Boolean>

    suspend fun setTapToIdentify(enabled: Boolean)

    /**
     * Whether tap-to-identify also works in the sensor frame, where the view is constantly
     * moving (v1's `show_object_info_auto_mode`; on by default in v2, unlike v1).
     */
    val tapToIdentifyInAutoMode: Flow<Boolean>

    suspend fun setTapToIdentifyInAutoMode(enabled: Boolean)

    /** Manual location mode: don't acquire automatically (v1's `no_auto_locate`). */
    val noAutoLocate: Flow<Boolean>

    suspend fun setNoAutoLocate(enabled: Boolean)

    /**
     * The last confirmed observer position — written on every provider fix and manual entry,
     * read back in manual mode and as the startup seed (v1's `latitude`/`longitude` pair).
     * Null until a location has ever been set.
     */
    val savedLocation: Flow<LatLong?>

    suspend fun setSavedLocation(location: LatLong)

    /**
     * Whether the map's pointing HUD is drawn at all. On by default; when on, the HUD still
     * shows and hides with the rest of the chrome rather than being pinned on screen.
     */
    val showHud: Flow<Boolean>

    suspend fun setShowHud(enabled: Boolean)

    /**
     * Whether the user has ever shown or hidden the map chrome themselves.
     *
     * The chrome auto-hides shortly after the map opens (v1's `FullscreenControlsManager`),
     * which leaves a first-time user looking at a bare starfield with no hint that tapping
     * brings the controls back. Until they have worked the toggle once, the chrome stays put
     * and the tap teaches itself; from then on the auto-hide behaves as it always has.
     */
    val chromeEverToggled: Flow<Boolean>

    suspend fun setChromeEverToggled(toggled: Boolean)

    /**
     * After a manual drag ends, spring the horizon back to level (v1's `auto_level_horizon`,
     * on by default).
     */
    val autoLevelHorizon: Flow<Boolean>

    suspend fun setAutoLevelHorizon(enabled: Boolean)

    /** Sky-label size; drives `RenderState.labelScaleFactor` (v1's `font_size`). */
    val fontSize: Flow<FontSize>

    suspend fun setFontSize(size: FontSize)

    /** Night-mode screen dimming strength (v1's `auto_dimness`). */
    val autoDimness: Flow<AutoDimness>

    suspend fun setAutoDimness(dimness: AutoDimness)

    /**
     * Skip the fused rotation-vector sensor and use the classic accelerometer+magnetometer
     * path (v1's `disable_gyro`) — the escape hatch for devices with bad fused sensors.
     */
    val disableGyro: Flow<Boolean>

    suspend fun setDisableGyro(enabled: Boolean)

    /** Classic-path sensor sampling rate (v1's `sensor_speed`). */
    val sensorSpeed: Flow<SensorSpeed>

    suspend fun setSensorSpeed(speed: SensorSpeed)

    /** Classic-path smoothing strength (v1's `sensor_damping`, `EXTRA HIGH` by default). */
    val sensorDamping: Flow<SensorDamping>

    suspend fun setSensorDamping(damping: SensorDamping)

    /**
     * Negate the magnetometer's Z axis before fusion (v1's `reverse_magnetic_z`) — a
     * workaround for devices with a mis-mounted magnetometer.
     */
    val reverseMagneticZ: Flow<Boolean>

    suspend fun setReverseMagneticZ(enabled: Boolean)

    /**
     * Correct compass headings for local magnetic declination (v1's
     * `use_magnetic_correction`, on by default).
     */
    val useMagneticCorrection: Flow<Boolean>

    suspend fun setUseMagneticCorrection(enabled: Boolean)

    /**
     * Azimuth half of the drag-to-align sensor correction (D64): a rotation about the
     * zenith, in degrees — the successor of v1's `manual_compass_adjustment`, whose key and
     * settings row are gone (D64: everyone starts from zero). Set only by dragging in AR
     * mode; shown in the map HUD and diagnostics, cleared by the HUD's reset (D65). Never
     * exposed in the settings screen.
     */
    val sensorAzimuthAdjustmentDeg: Flow<Double>

    suspend fun setSensorAzimuthAdjustmentDeg(degrees: Double)

    /**
     * Altitude half of the drag-to-align sensor correction (D64): a rotation about the
     * camera-right axis, in degrees. Same lifecycle as [sensorAzimuthAdjustmentDeg].
     */
    val sensorAltitudeAdjustmentDeg: Flow<Double>

    suspend fun setSensorAltitudeAdjustmentDeg(degrees: Double)

    /** Which phone axis points at the sky in sensor mode (v1's `viewing_direction`). */
    val viewDirectionMode: Flow<ViewDirectionMode>

    suspend fun setViewDirectionMode(mode: ViewDirectionMode)

    /**
     * Suppress the automatic compass-calibration screen; low accuracy becomes a toast
     * instead (v1's `no calibration dialog`).
     */
    val dontShowCalibrationDialog: Flow<Boolean>

    suspend fun setDontShowCalibrationDialog(enabled: Boolean)

    /**
     * Whether usage statistics are collected (v1's `enable_analytics`). The default is
     * per-flavor, as in v1: opted in on gms, permanently false on fdroid (whose analytics
     * edge is a no-op regardless).
     */
    val enableAnalytics: Flow<Boolean>

    suspend fun setEnableAnalytics(enabled: Boolean)

    /**
     * When the low-accuracy warning last fired, epoch millis; 0 until the first warning
     * (v1's `Last calibration warning time`). Throttles the calibration nudge.
     */
    val lastCalibrationWarningMillis: Flow<Long>

    suspend fun setLastCalibrationWarningMillis(timeMillis: Long)

    /**
     * Whether the Moon info card's add-a-widget row has been acted on — added or dismissed,
     * either way it never shows again (D75: promo rows are offered exactly once).
     */
    val moonWidgetPromoDismissed: Flow<Boolean>

    suspend fun setMoonWidgetPromoDismissed()

    /** Meteor-shower peak notifications (D77). Off until the user opts in — quiet by default. */
    val showerAlertsEnabled: Flow<Boolean>

    suspend fun setShowerAlertsEnabled(enabled: Boolean)

    /** The tonight's-sky digest notification (D77). Off until the user opts in. */
    val tonightDigestEnabled: Flow<Boolean>

    suspend fun setTonightDigestEnabled(enabled: Boolean)

    /**
     * Consent to fetch satellite element sets from CelesTrak (D92).
     *
     * Not the same act as wanting to *see* the satellite layer — a user can have the feature and
     * still decline the network request — so it is a separate gate from the layer's visibility.
     * The default differs by flavor: on for gms, whose users already run a build that talks to
     * Firebase, and off for fdroid, where network access is flagged in the anti-features metadata
     * and the audience expects opt-in.
     */
    val satelliteDataEnabled: Flow<Boolean>

    suspend fun setSatelliteDataEnabled(enabled: Boolean)

    /**
     * Whether the "labels don't scale with zoom" hint has been shown. Fires at most once ever,
     * on the first deep zoom — the moment the user is actually wondering why the names stayed
     * small — and points at [fontSize], which they would otherwise never find.
     */
    val labelSizeHintShown: Flow<Boolean>

    suspend fun setLabelSizeHintShown()

    /**
     * How many times the map chrome has been revealed with the rail's name labels attached.
     * The labels teach what the icons mean and then get out of the way: they ride the first
     * few reveals and fade out on the last one, so the final appearance is itself the lesson
     * that they are leaving. Counts reveals, not sessions — a user who opens the app three
     * times without ever showing the chrome has been taught nothing.
     */
    val railLabelReveals: Flow<Int>

    suspend fun incrementRailLabelReveals()
}
