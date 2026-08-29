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
    val reverseMagneticZ: Boolean = false,
    val useMagneticCorrection: Boolean = true,
    val viewDirectionMode: ViewDirectionMode = ViewDirectionMode.STANDARD,
    val enableAnalytics: Boolean = true,
    val showerAlerts: Boolean = false,
    val tonightDigest: Boolean = false,
    val satelliteData: Boolean = false,
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
            settings.tapToIdentify,
            settings.tapToIdentifyInAutoMode,
            settings.autoLevelHorizon,
            settings.fontSize,
            settings.autoDimness,
            settings.showSkyGradient,
            settings.disableGyro,
            settings.sensorSpeed,
            settings.sensorDamping,
            settings.reverseMagneticZ,
            settings.useMagneticCorrection,
            settings.viewDirectionMode,
            settings.enableAnalytics,
            settings.showerAlertsEnabled,
            settings.tonightDigestEnabled,
            settings.satelliteDataEnabled,
        ) { values ->
            SettingsUiState(
                tapToIdentify = values[0] as Boolean,
                tapToIdentifyInAutoMode = values[1] as Boolean,
                autoLevelHorizon = values[2] as Boolean,
                fontSize = values[3] as FontSize,
                autoDimness = values[4] as AutoDimness,
                showSkyGradient = values[5] as Boolean,
                disableGyro = values[6] as Boolean,
                sensorSpeed = values[7] as SensorSpeed,
                sensorDamping = values[8] as SensorDamping,
                reverseMagneticZ = values[9] as Boolean,
                useMagneticCorrection = values[10] as Boolean,
                viewDirectionMode = values[11] as ViewDirectionMode,
                enableAnalytics = values[12] as Boolean,
                showerAlerts = values[13] as Boolean,
                tonightDigest = values[14] as Boolean,
                // Appended, never inserted: this combine unpacks by position, so a new flow in the
                // middle would silently shift every index after it.
                satelliteData = values[15] as Boolean,
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
