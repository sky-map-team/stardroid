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
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [Settings] for JVM tests: every flow is a hot [MutableStateFlow]. */
class FakeSettings : Settings {
    private val layers = mutableMapOf<LayerId, MutableStateFlow<Boolean>>()

    private fun layerFlow(id: LayerId) = layers.getOrPut(id) { MutableStateFlow(true) }

    override fun layerEnabled(id: LayerId): Flow<Boolean> = layerFlow(id)

    override suspend fun setLayerEnabled(
        id: LayerId,
        enabled: Boolean,
    ) {
        layerFlow(id).value = enabled
    }

    private val parameters = mutableMapOf<String, MutableStateFlow<String>>()

    private fun parameterFlow(
        id: LayerId,
        key: String,
        default: String,
    ) = parameters.getOrPut("${id.id}/$key") { MutableStateFlow(default) }

    override fun layerParameter(
        id: LayerId,
        key: String,
        default: String,
    ): Flow<String> = parameterFlow(id, key, default)

    override suspend fun setLayerParameter(
        id: LayerId,
        key: String,
        option: String,
    ) {
        parameterFlow(id, key, option).value = option
    }

    private val night = MutableStateFlow(false)

    override val nightMode: Flow<Boolean> = night

    override suspend fun setNightMode(enabled: Boolean) {
        night.value = enabled
    }

    private val skyGradient = MutableStateFlow(true)

    override val showSkyGradient: Flow<Boolean> = skyGradient

    override suspend fun setShowSkyGradient(enabled: Boolean) {
        skyGradient.value = enabled
    }

    val tapToIdentifyState = MutableStateFlow(true)

    override val tapToIdentify: Flow<Boolean> = tapToIdentifyState

    override suspend fun setTapToIdentify(enabled: Boolean) {
        tapToIdentifyState.value = enabled
    }

    val tapToIdentifyInAutoModeState = MutableStateFlow(true)

    override val tapToIdentifyInAutoMode: Flow<Boolean> = tapToIdentifyInAutoModeState

    override suspend fun setTapToIdentifyInAutoMode(enabled: Boolean) {
        tapToIdentifyInAutoModeState.value = enabled
    }

    val noAutoLocateState = MutableStateFlow(false)

    override val noAutoLocate: Flow<Boolean> = noAutoLocateState

    override suspend fun setNoAutoLocate(enabled: Boolean) {
        noAutoLocateState.value = enabled
    }

    val savedLocationState = MutableStateFlow<LatLong?>(null)

    override val savedLocation: Flow<LatLong?> = savedLocationState

    override suspend fun setSavedLocation(location: LatLong) {
        savedLocationState.value = location
    }

    val showHudState = MutableStateFlow(true)

    override val showHud: Flow<Boolean> = showHudState

    override suspend fun setShowHud(enabled: Boolean) {
        showHudState.value = enabled
    }

    // Defaults to true so existing tests see the settled behavior; the first-run case sets it
    // false explicitly.
    val chromeEverToggledState = MutableStateFlow(true)

    override val chromeEverToggled: Flow<Boolean> = chromeEverToggledState

    override suspend fun setChromeEverToggled(toggled: Boolean) {
        chromeEverToggledState.value = toggled
    }

    val autoLevelHorizonState = MutableStateFlow(true)

    override val autoLevelHorizon: Flow<Boolean> = autoLevelHorizonState

    override suspend fun setAutoLevelHorizon(enabled: Boolean) {
        autoLevelHorizonState.value = enabled
    }

    val fontSizeState = MutableStateFlow(FontSize.MEDIUM)

    override val fontSize: Flow<FontSize> = fontSizeState

    override suspend fun setFontSize(size: FontSize) {
        fontSizeState.value = size
    }

    val autoDimnessState = MutableStateFlow(AutoDimness.SYSTEM)

    override val autoDimness: Flow<AutoDimness> = autoDimnessState

    override suspend fun setAutoDimness(dimness: AutoDimness) {
        autoDimnessState.value = dimness
    }

    val disableGyroState = MutableStateFlow(false)

    override val disableGyro: Flow<Boolean> = disableGyroState

    override suspend fun setDisableGyro(enabled: Boolean) {
        disableGyroState.value = enabled
    }

    val sensorSpeedState = MutableStateFlow(SensorSpeed.STANDARD)

    override val sensorSpeed: Flow<SensorSpeed> = sensorSpeedState

    override suspend fun setSensorSpeed(speed: SensorSpeed) {
        sensorSpeedState.value = speed
    }

    val sensorDampingState = MutableStateFlow(SensorDamping.EXTRA_HIGH)

    override val sensorDamping: Flow<SensorDamping> = sensorDampingState

    override suspend fun setSensorDamping(damping: SensorDamping) {
        sensorDampingState.value = damping
    }

    val reverseMagneticZState = MutableStateFlow(false)

    override val reverseMagneticZ: Flow<Boolean> = reverseMagneticZState

    override suspend fun setReverseMagneticZ(enabled: Boolean) {
        reverseMagneticZState.value = enabled
    }

    val useMagneticCorrectionState = MutableStateFlow(true)

    override val useMagneticCorrection: Flow<Boolean> = useMagneticCorrectionState

    override suspend fun setUseMagneticCorrection(enabled: Boolean) {
        useMagneticCorrectionState.value = enabled
    }

    val sensorAzimuthAdjustmentState = MutableStateFlow(0.0)

    override val sensorAzimuthAdjustmentDeg: Flow<Double> = sensorAzimuthAdjustmentState

    override suspend fun setSensorAzimuthAdjustmentDeg(degrees: Double) {
        sensorAzimuthAdjustmentState.value = degrees
    }

    val sensorAltitudeAdjustmentState = MutableStateFlow(0.0)

    override val sensorAltitudeAdjustmentDeg: Flow<Double> = sensorAltitudeAdjustmentState

    override suspend fun setSensorAltitudeAdjustmentDeg(degrees: Double) {
        sensorAltitudeAdjustmentState.value = degrees
    }

    val viewDirectionModeState = MutableStateFlow(ViewDirectionMode.STANDARD)

    override val viewDirectionMode: Flow<ViewDirectionMode> = viewDirectionModeState

    override suspend fun setViewDirectionMode(mode: ViewDirectionMode) {
        viewDirectionModeState.value = mode
    }

    val dontShowCalibrationDialogState = MutableStateFlow(false)

    override val dontShowCalibrationDialog: Flow<Boolean> = dontShowCalibrationDialogState

    override suspend fun setDontShowCalibrationDialog(enabled: Boolean) {
        dontShowCalibrationDialogState.value = enabled
    }

    val lastCalibrationWarningMillisState = MutableStateFlow(0L)

    override val lastCalibrationWarningMillis: Flow<Long> = lastCalibrationWarningMillisState

    override suspend fun setLastCalibrationWarningMillis(timeMillis: Long) {
        lastCalibrationWarningMillisState.value = timeMillis
    }

    val enableAnalyticsState = MutableStateFlow(true)

    override val enableAnalytics: Flow<Boolean> = enableAnalyticsState

    override suspend fun setEnableAnalytics(enabled: Boolean) {
        enableAnalyticsState.value = enabled
    }

    val moonWidgetPromoDismissedState = MutableStateFlow(false)

    override val moonWidgetPromoDismissed: Flow<Boolean> = moonWidgetPromoDismissedState

    override suspend fun setMoonWidgetPromoDismissed() {
        moonWidgetPromoDismissedState.value = true
    }

    val showerAlertsEnabledState = MutableStateFlow(false)

    override val showerAlertsEnabled: Flow<Boolean> = showerAlertsEnabledState

    override suspend fun setShowerAlertsEnabled(enabled: Boolean) {
        showerAlertsEnabledState.value = enabled
    }

    val tonightDigestEnabledState = MutableStateFlow(false)

    override val tonightDigestEnabled: Flow<Boolean> = tonightDigestEnabledState

    override suspend fun setTonightDigestEnabled(enabled: Boolean) {
        tonightDigestEnabledState.value = enabled
    }

    val satelliteDataEnabledState = MutableStateFlow(false)

    override val satelliteDataEnabled: Flow<Boolean> = satelliteDataEnabledState

    override suspend fun setSatelliteDataEnabled(enabled: Boolean) {
        satelliteDataEnabledState.value = enabled
    }

    val labelSizeHintShownState = MutableStateFlow(false)

    override val labelSizeHintShown: Flow<Boolean> = labelSizeHintShownState

    override suspend fun setLabelSizeHintShown() {
        labelSizeHintShownState.value = true
    }

    val railLabelRevealsState = MutableStateFlow(0)

    override val railLabelReveals: Flow<Int> = railLabelRevealsState

    override suspend fun incrementRailLabelReveals() {
        railLabelRevealsState.value += 1
    }
}
