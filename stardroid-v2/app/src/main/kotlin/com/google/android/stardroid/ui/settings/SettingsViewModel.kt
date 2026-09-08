/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.settings.AutoDimness
import com.google.android.stardroid.settings.FontSize
import com.google.android.stardroid.settings.RotationSmoothingLevel
import com.google.android.stardroid.settings.SensorDamping
import com.google.android.stardroid.settings.SensorSpeed
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the settings screen shows, in v1's four-section order. */
data class SettingsUiState(
    val tapToIdentify: Boolean = true,
    val tapToIdentifyInAutoMode: Boolean = true,
    val autoLevelHorizon: Boolean = true,
    val fontSize: FontSize = FontSize.MEDIUM,
    val autoDimness: AutoDimness = AutoDimness.SYSTEM,
    val showSkyGradient: Boolean = true,
    val disableGyro: Boolean = false,
    val sensorSpeed: SensorSpeed = SensorSpeed.STANDARD,
    val sensorDamping: SensorDamping = SensorDamping.EXTRA_HIGH,
    val rotationLowPass: RotationSmoothingLevel = RotationSmoothingLevel.OFF,
    val rotationDeadband: RotationSmoothingLevel = RotationSmoothingLevel.OFF,
    val reverseMagneticZ: Boolean = false,
    val useMagneticCorrection: Boolean = true,
    val viewDirectionMode: ViewDirectionMode = ViewDirectionMode.STANDARD,
    val enableAnalytics: Boolean = true,
    val showerAlerts: Boolean = false,
    val tonightDigest: Boolean = false,
    val satelliteData: Boolean = false,
)

// Grouped so `state` below combines at most 5 flows at a time, each into a small typed data
// class via a constructor reference — combine's array-unpacking overload (kicks in past 5
// flows) loses static types and made the old flat version an `values[N] as T` index puzzle
// that grew more error-prone with every added setting.
private data class ControlsPrefs(
    val tapToIdentify: Boolean,
    val tapToIdentifyInAutoMode: Boolean,
    val autoLevelHorizon: Boolean,
)

private data class AppearancePrefs(
    val fontSize: FontSize,
    val autoDimness: AutoDimness,
    val showSkyGradient: Boolean,
)

// Split further into a legacy-path sub-group since SensorPrefs itself now has 6 fields —
// past combine's 5-flow typed overload (same nesting used in AppModule's SensorConfig combine).
private data class LegacySensorPrefs(
    val disableGyro: Boolean,
    val sensorSpeed: SensorSpeed,
    val sensorDamping: SensorDamping,
    val reverseMagneticZ: Boolean,
)

private data class SensorPrefs(
    val disableGyro: Boolean,
    val sensorSpeed: SensorSpeed,
    val sensorDamping: SensorDamping,
    val reverseMagneticZ: Boolean,
    val rotationLowPass: RotationSmoothingLevel,
    val rotationDeadband: RotationSmoothingLevel,
)

private data class MagneticPrefs(
    val useMagneticCorrection: Boolean,
    val viewDirectionMode: ViewDirectionMode,
)

private data class OtherPrefs(
    val enableAnalytics: Boolean,
    val showerAlertsEnabled: Boolean,
    val tonightDigestEnabled: Boolean,
    val satelliteDataEnabled: Boolean,
)

/**
 * The settings screen over the typed [Settings] flows (screens-and-startup.md): one UI state
 * combining every preference, one suspend write per control. Consumers of each preference
 * (`MapViewModel`, the sensor source, the activity's dimmer) collect [Settings] themselves —
 * this ViewModel is only the editor.
 *
 * Every write logs v1's preference-change event (`PreferenceChangeAnalyticsTracker`), with
 * the DataStore key standing in for v1's SharedPreferences key.
 */
class SettingsViewModel(
    private val settings: Settings,
    private val analytics: Analytics = NoOpAnalytics,
    experimentConfig: ExperimentConfig = ExperimentConfig.Static,
) : ViewModel() {
    /** Whether the notifications section shows at all (D77 experiment gate). */
    val notificationsAvailable: Boolean =
        experimentConfig.isEnabled(Experiment.NOTIFICATIONS)

    val state: StateFlow<SettingsUiState> =
        combine(
            combine(
                settings.tapToIdentify,
                settings.tapToIdentifyInAutoMode,
                settings.autoLevelHorizon,
                ::ControlsPrefs,
            ),
            combine(
                settings.fontSize,
                settings.autoDimness,
                settings.showSkyGradient,
                ::AppearancePrefs,
            ),
            combine(
                combine(
                    settings.disableGyro,
                    settings.sensorSpeed,
                    settings.sensorDamping,
                    settings.reverseMagneticZ,
                    ::LegacySensorPrefs,
                ),
                settings.rotationLowPass,
                settings.rotationDeadband,
            ) { legacy, rotationLowPass, rotationDeadband ->
                SensorPrefs(
                    disableGyro = legacy.disableGyro,
                    sensorSpeed = legacy.sensorSpeed,
                    sensorDamping = legacy.sensorDamping,
                    reverseMagneticZ = legacy.reverseMagneticZ,
                    rotationLowPass = rotationLowPass,
                    rotationDeadband = rotationDeadband,
                )
            },
            combine(settings.useMagneticCorrection, settings.viewDirectionMode, ::MagneticPrefs),
            combine(
                settings.enableAnalytics,
                settings.showerAlertsEnabled,
                settings.tonightDigestEnabled,
                settings.satelliteDataEnabled,
                ::OtherPrefs,
            ),
        ) { controls, appearance, sensors, magnetic, other ->
            SettingsUiState(
                tapToIdentify = controls.tapToIdentify,
                tapToIdentifyInAutoMode = controls.tapToIdentifyInAutoMode,
                autoLevelHorizon = controls.autoLevelHorizon,
                fontSize = appearance.fontSize,
                autoDimness = appearance.autoDimness,
                showSkyGradient = appearance.showSkyGradient,
                disableGyro = sensors.disableGyro,
                sensorSpeed = sensors.sensorSpeed,
                sensorDamping = sensors.sensorDamping,
                reverseMagneticZ = sensors.reverseMagneticZ,
                rotationLowPass = sensors.rotationLowPass,
                rotationDeadband = sensors.rotationDeadband,
                useMagneticCorrection = magnetic.useMagneticCorrection,
                viewDirectionMode = magnetic.viewDirectionMode,
                enableAnalytics = other.enableAnalytics,
                showerAlerts = other.showerAlertsEnabled,
                tonightDigest = other.tonightDigestEnabled,
                satelliteData = other.satelliteDataEnabled,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setTapToIdentify(enabled: Boolean) {
        trackChange("tap_to_identify", enabled)
        viewModelScope.launch { settings.setTapToIdentify(enabled) }
    }

    fun setTapToIdentifyInAutoMode(enabled: Boolean) {
        trackChange("tap_to_identify_auto_mode", enabled)
        viewModelScope.launch { settings.setTapToIdentifyInAutoMode(enabled) }
    }

    fun setAutoLevelHorizon(enabled: Boolean) {
        trackChange("auto_level_horizon", enabled)
        viewModelScope.launch { settings.setAutoLevelHorizon(enabled) }
    }

    fun setFontSize(size: FontSize) {
        trackChange("font_size", size)
        viewModelScope.launch { settings.setFontSize(size) }
    }

    fun setAutoDimness(dimness: AutoDimness) {
        trackChange("auto_dimness", dimness)
        viewModelScope.launch { settings.setAutoDimness(dimness) }
    }

    fun setShowSkyGradient(enabled: Boolean) {
        trackChange("show_sky_gradient", enabled)
        viewModelScope.launch { settings.setShowSkyGradient(enabled) }
    }

    fun setDisableGyro(enabled: Boolean) {
        trackChange("disable_gyro", enabled)
        viewModelScope.launch { settings.setDisableGyro(enabled) }
    }

    fun setSensorSpeed(speed: SensorSpeed) {
        trackChange("sensor_speed", speed)
        viewModelScope.launch { settings.setSensorSpeed(speed) }
    }

    fun setSensorDamping(damping: SensorDamping) {
        trackChange("sensor_damping", damping)
        viewModelScope.launch { settings.setSensorDamping(damping) }
    }

    fun setRotationLowPass(level: RotationSmoothingLevel) {
        trackChange("rotation_low_pass", level)
        viewModelScope.launch { settings.setRotationLowPass(level) }
    }

    fun setRotationDeadband(level: RotationSmoothingLevel) {
        trackChange("rotation_deadband", level)
        viewModelScope.launch { settings.setRotationDeadband(level) }
    }

    fun setReverseMagneticZ(enabled: Boolean) {
        trackChange("reverse_magnetic_z", enabled)
        viewModelScope.launch { settings.setReverseMagneticZ(enabled) }
    }

    fun setUseMagneticCorrection(enabled: Boolean) {
        trackChange("use_magnetic_correction", enabled)
        viewModelScope.launch { settings.setUseMagneticCorrection(enabled) }
    }

    fun setViewDirectionMode(mode: ViewDirectionMode) {
        trackChange("view_direction_mode", mode)
        viewModelScope.launch { settings.setViewDirectionMode(mode) }
    }

    /**
     * The opt-out switch. Tracked like any preference — the last event before collection
     * stops, mirroring v1 (the edge itself flips via `AppModule`'s settings collector).
     */
    fun setEnableAnalytics(enabled: Boolean) {
        trackChange("enable_analytics", enabled)
        viewModelScope.launch { settings.setEnableAnalytics(enabled) }
    }

    fun setShowerAlerts(enabled: Boolean) {
        trackChange("shower_alerts_enabled", enabled)
        viewModelScope.launch { settings.setShowerAlertsEnabled(enabled) }
    }

    fun setTonightDigest(enabled: Boolean) {
        trackChange("tonight_digest_enabled", enabled)
        viewModelScope.launch { settings.setTonightDigestEnabled(enabled) }
    }

    /**
     * Consent to fetch satellite element sets (D92). Turning it off cancels the scheduled job on
     * the next collection in `SkyMapApplication`, so traffic stops promptly rather than at the
     * next app start.
     */
    fun setSatelliteData(enabled: Boolean) {
        viewModelScope.launch { settings.setSatelliteDataEnabled(enabled) }
    }

    /**
     * A menu destination opened from Settings, logged on the same event as the ⋮ overflow's
     * rows so the Diagnostics counter stays continuous across its move into this screen.
     */
    fun logMenuItem(label: String) {
        analytics.trackEvent(
            AnalyticsEvents.MENU_ITEM_EVENT,
            mapOf(AnalyticsEvents.MENU_ITEM_EVENT_VALUE to label),
        )
    }

    /** v1 `PreferenceChangeAnalyticsTracker`'s `"$key:$value"` payload. */
    private fun trackChange(
        key: String,
        value: Any,
    ) {
        analytics.trackEvent(
            AnalyticsEvents.PREFERENCE_CHANGE_EVENT,
            mapOf(AnalyticsEvents.PREFERENCE_CHANGE_EVENT_VALUE to "$key:$value"),
        )
    }
}
