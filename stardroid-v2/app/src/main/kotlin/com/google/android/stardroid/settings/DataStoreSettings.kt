/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.render.api.LayerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** [Settings] on Preferences DataStore; the app's single settings file. */
class DataStoreSettings(
    private val dataStore: DataStore<Preferences>,
    enableAnalyticsDefault: Boolean = true,
    satelliteDataDefault: Boolean = true,
) : Settings {
    override fun layerEnabled(id: LayerId): Flow<Boolean> = boolean(layerKey(id), default = true)

    override suspend fun setLayerEnabled(
        id: LayerId,
        enabled: Boolean,
    ) {
        dataStore.edit { it[layerKey(id)] = enabled }
    }

    override fun layerParameter(
        id: LayerId,
        key: String,
        default: String,
    ): Flow<String> =
        safeData()
            .map { it[layerParameterKey(id, key)] ?: default }
            .distinctUntilChanged()

    override suspend fun setLayerParameter(
        id: LayerId,
        key: String,
        option: String,
    ) {
        dataStore.edit { it[layerParameterKey(id, key)] = option }
    }

    override val nightMode: Flow<Boolean> = boolean(NIGHT_MODE, default = false)

    override suspend fun setNightMode(enabled: Boolean) {
        dataStore.edit { it[NIGHT_MODE] = enabled }
    }

    override val showSkyGradient: Flow<Boolean> = boolean(SHOW_SKY_GRADIENT, default = true)

    override suspend fun setShowSkyGradient(enabled: Boolean) {
        dataStore.edit { it[SHOW_SKY_GRADIENT] = enabled }
    }

    override val tapToIdentify: Flow<Boolean> = boolean(TAP_TO_IDENTIFY, default = true)

    override suspend fun setTapToIdentify(enabled: Boolean) {
        dataStore.edit { it[TAP_TO_IDENTIFY] = enabled }
    }

    override val tapToIdentifyInAutoMode: Flow<Boolean> =
        boolean(TAP_TO_IDENTIFY_AUTO_MODE, default = true)

    override suspend fun setTapToIdentifyInAutoMode(enabled: Boolean) {
        dataStore.edit { it[TAP_TO_IDENTIFY_AUTO_MODE] = enabled }
    }

    override val noAutoLocate: Flow<Boolean> = boolean(NO_AUTO_LOCATE, default = false)

    override suspend fun setNoAutoLocate(enabled: Boolean) {
        dataStore.edit { it[NO_AUTO_LOCATE] = enabled }
    }

    override val savedLocation: Flow<LatLong?> =
        safeData()
            .map { prefs ->
                val latitude = prefs[LATITUDE] ?: return@map null
                val longitude = prefs[LONGITUDE] ?: return@map null
                LatLong(latitude, longitude)
            }
            .distinctUntilChanged()

    override suspend fun setSavedLocation(location: LatLong) {
        dataStore.edit {
            it[LATITUDE] = location.latitudeDeg
            it[LONGITUDE] = location.longitudeDeg
        }
    }

    override val showHud: Flow<Boolean> = boolean(SHOW_HUD, default = true)

    override suspend fun setShowHud(enabled: Boolean) {
        dataStore.edit { it[SHOW_HUD] = enabled }
    }

    override val chromeEverToggled: Flow<Boolean> = boolean(CHROME_EVER_TOGGLED, default = false)

    override suspend fun setChromeEverToggled(toggled: Boolean) {
        dataStore.edit { it[CHROME_EVER_TOGGLED] = toggled }
    }

    override val autoLevelHorizon: Flow<Boolean> = boolean(AUTO_LEVEL_HORIZON, default = true)

    override suspend fun setAutoLevelHorizon(enabled: Boolean) {
        dataStore.edit { it[AUTO_LEVEL_HORIZON] = enabled }
    }

    override val fontSize: Flow<FontSize> = enum(FONT_SIZE, FontSize.MEDIUM)

    override suspend fun setFontSize(size: FontSize) {
        dataStore.edit { it[FONT_SIZE] = size.name }
    }

    override val autoDimness: Flow<AutoDimness> = enum(AUTO_DIMNESS, AutoDimness.SYSTEM)

    override suspend fun setAutoDimness(dimness: AutoDimness) {
        dataStore.edit { it[AUTO_DIMNESS] = dimness.name }
    }

    override val disableGyro: Flow<Boolean> = boolean(DISABLE_GYRO, default = false)

    override suspend fun setDisableGyro(enabled: Boolean) {
        dataStore.edit { it[DISABLE_GYRO] = enabled }
    }

    override val sensorSpeed: Flow<SensorSpeed> = enum(SENSOR_SPEED, SensorSpeed.STANDARD)

    override suspend fun setSensorSpeed(speed: SensorSpeed) {
        dataStore.edit { it[SENSOR_SPEED] = speed.name }
    }

    // v1's effective default: its preference XML said EXTRA HIGH and setDefaultValues
    // persisted it, so the code-path STANDARD fallback never applied in practice.
    override val sensorDamping: Flow<SensorDamping> =
        enum(SENSOR_DAMPING, SensorDamping.EXTRA_HIGH)

    override suspend fun setSensorDamping(damping: SensorDamping) {
        dataStore.edit { it[SENSOR_DAMPING] = damping.name }
    }

    override val reverseMagneticZ: Flow<Boolean> = boolean(REVERSE_MAGNETIC_Z, default = false)

    override suspend fun setReverseMagneticZ(enabled: Boolean) {
        dataStore.edit { it[REVERSE_MAGNETIC_Z] = enabled }
    }

    override val useMagneticCorrection: Flow<Boolean> =
        boolean(USE_MAGNETIC_CORRECTION, default = true)

    override suspend fun setUseMagneticCorrection(enabled: Boolean) {
        dataStore.edit { it[USE_MAGNETIC_CORRECTION] = enabled }
    }

    override val sensorAzimuthAdjustmentDeg: Flow<Double> =
        safeData()
            .map { it[SENSOR_AZIMUTH_ADJUSTMENT] ?: 0.0 }
            .distinctUntilChanged()

    override suspend fun setSensorAzimuthAdjustmentDeg(degrees: Double) {
        dataStore.edit { it[SENSOR_AZIMUTH_ADJUSTMENT] = degrees }
    }

    override val sensorAltitudeAdjustmentDeg: Flow<Double> =
        safeData()
            .map { it[SENSOR_ALTITUDE_ADJUSTMENT] ?: 0.0 }
            .distinctUntilChanged()

    override suspend fun setSensorAltitudeAdjustmentDeg(degrees: Double) {
        dataStore.edit { it[SENSOR_ALTITUDE_ADJUSTMENT] = degrees }
    }

    override val viewDirectionMode: Flow<ViewDirectionMode> =
        enum(VIEW_DIRECTION_MODE, ViewDirectionMode.STANDARD)

    override suspend fun setViewDirectionMode(mode: ViewDirectionMode) {
        dataStore.edit { it[VIEW_DIRECTION_MODE] = mode.name }
    }

    override val dontShowCalibrationDialog: Flow<Boolean> =
        boolean(DONT_SHOW_CALIBRATION_DIALOG, default = false)

    override suspend fun setDontShowCalibrationDialog(enabled: Boolean) {
        dataStore.edit { it[DONT_SHOW_CALIBRATION_DIALOG] = enabled }
    }

    override val enableAnalytics: Flow<Boolean> =
        boolean(ENABLE_ANALYTICS, default = enableAnalyticsDefault)

    override suspend fun setEnableAnalytics(enabled: Boolean) {
        dataStore.edit { it[ENABLE_ANALYTICS] = enabled }
    }

    override val lastCalibrationWarningMillis: Flow<Long> =
        safeData()
            .map { it[LAST_CALIBRATION_WARNING] ?: 0L }
            .distinctUntilChanged()

    override suspend fun setLastCalibrationWarningMillis(timeMillis: Long) {
        dataStore.edit { it[LAST_CALIBRATION_WARNING] = timeMillis }
    }

    override val moonWidgetPromoDismissed: Flow<Boolean> =
        boolean(MOON_WIDGET_PROMO_DISMISSED, default = false)

    override suspend fun setMoonWidgetPromoDismissed() {
        dataStore.edit { it[MOON_WIDGET_PROMO_DISMISSED] = true }
    }

    override val showerAlertsEnabled: Flow<Boolean> =
        boolean(SHOWER_ALERTS_ENABLED, default = false)

    override suspend fun setShowerAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[SHOWER_ALERTS_ENABLED] = enabled }
    }

    override val satelliteDataEnabled: Flow<Boolean> =
        boolean(SATELLITE_DATA_ENABLED, default = satelliteDataDefault)

    override suspend fun setSatelliteDataEnabled(enabled: Boolean) {
        dataStore.edit { it[SATELLITE_DATA_ENABLED] = enabled }
    }

    override val tonightDigestEnabled: Flow<Boolean> =
        boolean(TONIGHT_DIGEST_ENABLED, default = false)

    override suspend fun setTonightDigestEnabled(enabled: Boolean) {
        dataStore.edit { it[TONIGHT_DIGEST_ENABLED] = enabled }
    }

    override val labelSizeHintShown: Flow<Boolean> =
        boolean(LABEL_SIZE_HINT_SHOWN, default = false)

    override suspend fun setLabelSizeHintShown() {
        dataStore.edit { it[LABEL_SIZE_HINT_SHOWN] = true }
    }

    override val railLabelReveals: Flow<Int> =
        safeData()
            .map { it[RAIL_LABEL_REVEALS] ?: 0 }
            .distinctUntilChanged()

    override suspend fun incrementRailLabelReveals() {
        // Saturates rather than wrapping: this only ever gets compared against a small
        // threshold, and an Int that overflows would silently bring the labels back.
        dataStore.edit {
            val seen = it[RAIL_LABEL_REVEALS] ?: 0
            if (seen < Int.MAX_VALUE) it[RAIL_LABEL_REVEALS] = seen + 1
        }
    }

    private fun boolean(
        key: Preferences.Key<Boolean>,
        default: Boolean,
    ): Flow<Boolean> =
        safeData()
            .map { it[key] ?: default }
            .distinctUntilChanged()

    /** An enum stored by name; unknown stored values fall back to [default]. */
    private inline fun <reified T : Enum<T>> enum(
        key: Preferences.Key<String>,
        default: T,
    ): Flow<T> =
        safeData()
            .map { prefs ->
                prefs[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
                    ?: default
            }
            .distinctUntilChanged()

    /** The preferences stream with the standard unreadable-file fallback. */
    private fun safeData(): Flow<Preferences> =
        dataStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }

    companion object {
        /** Namespaced by the stable `LayerId`, e.g. `layer_visible/catalog/stars`. */
        private fun layerKey(id: LayerId) = booleanPreferencesKey("layer_visible/${id.id}")

        private fun layerParameterKey(
            id: LayerId,
            key: String,
        ) = stringPreferencesKey("layer_param/${id.id}/$key")

        private val NIGHT_MODE = booleanPreferencesKey("night_mode")

        private val SHOW_SKY_GRADIENT = booleanPreferencesKey("show_sky_gradient")

        private val TAP_TO_IDENTIFY = booleanPreferencesKey("tap_to_identify")

        private val TAP_TO_IDENTIFY_AUTO_MODE = booleanPreferencesKey("tap_to_identify_auto_mode")

        private val NO_AUTO_LOCATE = booleanPreferencesKey("no_auto_locate")

        private val LATITUDE = doublePreferencesKey("latitude")

        private val LONGITUDE = doublePreferencesKey("longitude")

        private val SHOW_HUD = booleanPreferencesKey("show_hud")

        private val CHROME_EVER_TOGGLED = booleanPreferencesKey("chrome_ever_toggled")

        private val AUTO_LEVEL_HORIZON = booleanPreferencesKey("auto_level_horizon")

        private val FONT_SIZE = stringPreferencesKey("font_size")

        private val AUTO_DIMNESS = stringPreferencesKey("auto_dimness")

        private val DISABLE_GYRO = booleanPreferencesKey("disable_gyro")

        private val SENSOR_SPEED = stringPreferencesKey("sensor_speed")

        private val SENSOR_DAMPING = stringPreferencesKey("sensor_damping")

        private val REVERSE_MAGNETIC_Z = booleanPreferencesKey("reverse_magnetic_z")

        private val USE_MAGNETIC_CORRECTION = booleanPreferencesKey("use_magnetic_correction")

        private val SENSOR_AZIMUTH_ADJUSTMENT = doublePreferencesKey("sensor_azimuth_adjustment")

        private val SENSOR_ALTITUDE_ADJUSTMENT =
            doublePreferencesKey("sensor_altitude_adjustment")

        private val VIEW_DIRECTION_MODE = stringPreferencesKey("view_direction_mode")

        private val DONT_SHOW_CALIBRATION_DIALOG =
            booleanPreferencesKey("dont_show_calibration_dialog")

        private val LAST_CALIBRATION_WARNING = longPreferencesKey("last_calibration_warning")

        private val ENABLE_ANALYTICS = booleanPreferencesKey("enable_analytics")

        private val MOON_WIDGET_PROMO_DISMISSED =
            booleanPreferencesKey("moon_widget_promo_dismissed")

        private val SHOWER_ALERTS_ENABLED = booleanPreferencesKey("shower_alerts_enabled")
        private val SATELLITE_DATA_ENABLED = booleanPreferencesKey("satellite_data_enabled")

        private val TONIGHT_DIGEST_ENABLED = booleanPreferencesKey("tonight_digest_enabled")

        private val LABEL_SIZE_HINT_SHOWN = booleanPreferencesKey("label_size_hint_shown")

        private val RAIL_LABEL_REVEALS = intPreferencesKey("rail_label_reveals")
    }
}
