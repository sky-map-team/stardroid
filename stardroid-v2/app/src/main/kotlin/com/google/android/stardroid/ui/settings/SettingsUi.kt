/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.settings.AutoDimness
import com.google.android.stardroid.settings.FontSize
import com.google.android.stardroid.settings.SensorDamping
import com.google.android.stardroid.settings.SensorSpeed
import com.google.android.stardroid.ui.common.topBarWindowInsets

/**
 * The settings screen — v1's `EditSettingsActivity` four sections (controls / appearance /
 * sensors / other) as a full-screen Compose overlay. "Other" holds the analytics opt-out
 * (D49); v1's sound-effects toggle still waits for sounds to be ported (D45).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                windowInsets = topBarWindowInsets(stringResource(R.string.settings_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SectionHeader(R.string.settings_section_controls)
                SwitchRow(
                    title = stringResource(R.string.settings_tap_to_identify),
                    summary = stringResource(R.string.settings_tap_to_identify_summary),
                    checked = state.tapToIdentify,
                    onCheckedChange = viewModel::setTapToIdentify,
                )
                // v1 dependency: the auto-mode refinement only matters while tap works at all.
                SwitchRow(
                    title = stringResource(R.string.settings_tap_in_auto_mode),
                    summary = stringResource(R.string.settings_tap_in_auto_mode_summary),
                    checked = state.tapToIdentifyInAutoMode,
                    enabled = state.tapToIdentify,
                    onCheckedChange = viewModel::setTapToIdentifyInAutoMode,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_auto_level_horizon),
                    summary = stringResource(R.string.settings_auto_level_horizon_summary),
                    checked = state.autoLevelHorizon,
                    onCheckedChange = viewModel::setAutoLevelHorizon,
                )

                SectionHeader(R.string.settings_section_appearance)
                ChoiceRow(
                    title = stringResource(R.string.settings_font_size),
                    summary = stringResource(R.string.settings_font_size_summary),
                    options = FontSize.entries,
                    selected = state.fontSize,
                    label = { fontSizeLabel(it) },
                    onSelect = viewModel::setFontSize,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_auto_dimness),
                    summary = stringResource(R.string.settings_auto_dimness_summary),
                    options = AutoDimness.entries,
                    selected = state.autoDimness,
                    label = { autoDimnessLabel(it) },
                    onSelect = viewModel::setAutoDimness,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_sky_gradient),
                    summary = stringResource(R.string.settings_sky_gradient_summary),
                    checked = state.showSkyGradient,
                    onCheckedChange = viewModel::setShowSkyGradient,
                )

                SectionHeader(R.string.settings_section_sensors)
                SwitchRow(
                    title = stringResource(R.string.settings_disable_gyro),
                    summary = stringResource(R.string.settings_disable_gyro_summary),
                    checked = state.disableGyro,
                    onCheckedChange = viewModel::setDisableGyro,
                )
                // Speed/damping/reverse-Z only act on the legacy (non-gyro) sensor path, so
                // they only show while that path is selected — less noise for everyone else.
                if (state.disableGyro) {
                    ChoiceRow(
                        title = stringResource(R.string.settings_sensor_speed),
                        summary = stringResource(R.string.settings_classic_sensors_only),
                        options = SensorSpeed.entries,
                        selected = state.sensorSpeed,
                        label = { sensorSpeedLabel(it) },
                        onSelect = viewModel::setSensorSpeed,
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_sensor_damping),
                        summary = stringResource(R.string.settings_classic_sensors_only),
                        options = SensorDamping.entries,
                        selected = state.sensorDamping,
                        label = { sensorDampingLabel(it) },
                        onSelect = viewModel::setSensorDamping,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_reverse_magnetic_z),
                        summary = stringResource(R.string.settings_classic_sensors_only),
                        checked = state.reverseMagneticZ,
                        onCheckedChange = viewModel::setReverseMagneticZ,
                    )
                }
                SwitchRow(
                    title = stringResource(R.string.settings_magnetic_correction),
                    summary = stringResource(R.string.settings_magnetic_correction_summary),
                    checked = state.useMagneticCorrection,
                    onCheckedChange = viewModel::setUseMagneticCorrection,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_view_direction),
                    summary = stringResource(R.string.settings_view_direction_summary),
                    options = ViewDirectionMode.entries,
                    selected = state.viewDirectionMode,
                    label = { viewDirectionLabel(it) },
                    onSelect = viewModel::setViewDirectionMode,
                )

                if (viewModel.notificationsAvailable) {
                    NotificationsSection(state, viewModel)
                }

                SectionHeader(R.string.settings_section_other)
                SwitchRow(
                    title = stringResource(R.string.enable_analytics),
                    summary = stringResource(R.string.enable_analytics_desc),
                    checked = state.enableAnalytics,
                    onCheckedChange = viewModel::setEnableAnalytics,
                )
                // Relocated from the ⋮ overflow sheet: a sensor/location readout is a
                // support tool, not something most people need most of the time, and it
                // was crowding out Help there (Hannah's feedback, 2026-08). "Settings →
                // Diagnostics" stays an easy instruction to give in a support reply.
                NavigationRow(
                    title = stringResource(R.string.diagnostics_button),
                    summary = stringResource(R.string.settings_diagnostics_summary),
                    onClick = {
                        viewModel.logMenuItem(AnalyticsEvents.DIAGNOSTICS_OPENED_LABEL)
                        onOpenDiagnostics()
                    },
                )
            }
        }
    }
}

/**
 * The D77 notifications section. The intro line states the contract ("at most once a day,
 * off until you turn it on") where the user decides, per the mockup. The first toggle-on
 * requests `POST_NOTIFICATIONS` (Android 13+); a denial flips the toggle back off, and a
 * standing system-level block shows an inline row deep-linking to the app's notification
 * settings instead of a dead switch.
 */
@Composable
private fun NotificationsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    var pendingRevert by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) pendingRevert?.invoke(false)
            pendingRevert = null
        }

    fun toggle(
        setter: (Boolean) -> Unit,
        enabled: Boolean,
    ) {
        setter(enabled)
        if (enabled &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingRevert = setter
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SectionHeader(R.string.settings_section_notifications)
    Text(
        stringResource(R.string.settings_notifications_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    SwitchRow(
        title = stringResource(R.string.settings_shower_alerts),
        summary = stringResource(R.string.settings_shower_alerts_summary),
        checked = state.showerAlerts,
        onCheckedChange = { toggle(viewModel::setShowerAlerts, it) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_tonight_digest),
        summary = stringResource(R.string.settings_tonight_digest_summary),
        checked = state.tonightDigest,
        onCheckedChange = { toggle(viewModel::setTonightDigest, it) },
    )
    if ((state.showerAlerts || state.tonightDigest) &&
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }.padding(vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_notifications_blocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(R.string.settings_notifications_blocked_action),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    @StringRes title: Int,
) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = 8.dp),
    ) {
        PreferenceLabels(title, summary, enabled, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A row that navigates elsewhere rather than holding a value — same labels and metrics as
 * the preference rows around it, but with a chevron instead of a control.
 */
@Composable
private fun NavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        PreferenceLabels(title, summary, enabled = true, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A preference whose value is one of [options]; tapping opens a radio dialog (v1's lists). */
@Composable
private fun <T> ChoiceRow(
    title: String,
    summary: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showDialog = true }
                .padding(vertical = 8.dp),
    ) {
        PreferenceLabels(title, summary, enabled, Modifier.weight(1f))
        Text(
            label(selected),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
        )
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (option in options) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = option == selected,
                                        role = Role.RadioButton,
                                    ) {
                                        onSelect(option)
                                        showDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                        ) {
                            RadioButton(
                                selected = option == selected,
                                onClick = null,
                            )
                            Text(
                                text = label(option),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun PreferenceLabels(
    title: String,
    summary: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleColor =
        if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Column(modifier.padding(end = 16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun fontSizeLabel(size: FontSize): String =
    stringResource(
        when (size) {
            FontSize.SMALL -> R.string.settings_font_size_small
            FontSize.MEDIUM -> R.string.settings_font_size_medium
            FontSize.LARGE -> R.string.settings_font_size_large
            FontSize.EXTRA_LARGE -> R.string.settings_font_size_extra_large
        },
    )

@Composable
private fun autoDimnessLabel(dimness: AutoDimness): String =
    stringResource(
        when (dimness) {
            AutoDimness.SYSTEM -> R.string.settings_auto_dimness_system
            AutoDimness.DIM -> R.string.settings_auto_dimness_dim
            AutoDimness.CLASSIC -> R.string.settings_auto_dimness_classic
        },
    )

@Composable
private fun sensorSpeedLabel(speed: SensorSpeed): String =
    stringResource(
        when (speed) {
            SensorSpeed.SLOW -> R.string.settings_sensor_speed_slow
            SensorSpeed.STANDARD -> R.string.settings_sensor_speed_standard
            SensorSpeed.FAST -> R.string.settings_sensor_speed_fast
        },
    )

@Composable
private fun sensorDampingLabel(damping: SensorDamping): String =
    stringResource(
        when (damping) {
            SensorDamping.STANDARD -> R.string.settings_sensor_damping_standard
            SensorDamping.HIGH -> R.string.settings_sensor_damping_high
            SensorDamping.EXTRA_HIGH -> R.string.settings_sensor_damping_extra_high
            SensorDamping.REALLY_HIGH -> R.string.settings_sensor_damping_really_high
        },
    )

@Composable
private fun viewDirectionLabel(mode: ViewDirectionMode): String =
    stringResource(
        when (mode) {
            ViewDirectionMode.STANDARD -> R.string.settings_view_direction_standard
            ViewDirectionMode.ROTATE90 -> R.string.settings_view_direction_rotate90
            ViewDirectionMode.TELESCOPE -> R.string.settings_view_direction_telescope
        },
    )
