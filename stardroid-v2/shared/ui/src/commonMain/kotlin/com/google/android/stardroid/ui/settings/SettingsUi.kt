/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.settings

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.astronomy.ViewDirectionMode
import com.google.android.stardroid.settings.AutoDimness
import com.google.android.stardroid.settings.FontSize
import com.google.android.stardroid.settings.OneEuroEaseOff
import com.google.android.stardroid.settings.OneEuroSteadiness
import com.google.android.stardroid.settings.RendererBackend
import com.google.android.stardroid.ui.common.notificationsBlocked
import com.google.android.stardroid.ui.common.rememberNotificationPermissionRequest
import com.google.android.stardroid.ui.common.rememberOpenNotificationSettings
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.diagnostics_button
import com.google.android.stardroid.ui.resources.enable_analytics
import com.google.android.stardroid.ui.resources.enable_analytics_desc
import com.google.android.stardroid.ui.resources.settings_announcements
import com.google.android.stardroid.ui.resources.settings_announcements_summary
import com.google.android.stardroid.ui.resources.settings_auto_dimness
import com.google.android.stardroid.ui.resources.settings_auto_dimness_classic
import com.google.android.stardroid.ui.resources.settings_auto_dimness_dim
import com.google.android.stardroid.ui.resources.settings_auto_dimness_summary
import com.google.android.stardroid.ui.resources.settings_auto_dimness_system
import com.google.android.stardroid.ui.resources.settings_auto_level_horizon
import com.google.android.stardroid.ui.resources.settings_auto_level_horizon_summary
import com.google.android.stardroid.ui.resources.settings_back
import com.google.android.stardroid.ui.resources.settings_cancel
import com.google.android.stardroid.ui.resources.settings_classic_sensors_only
import com.google.android.stardroid.ui.resources.settings_diagnostics_summary
import com.google.android.stardroid.ui.resources.settings_disable_gyro
import com.google.android.stardroid.ui.resources.settings_disable_gyro_summary
import com.google.android.stardroid.ui.resources.settings_ease_off
import com.google.android.stardroid.ui.resources.settings_ease_off_high
import com.google.android.stardroid.ui.resources.settings_ease_off_low
import com.google.android.stardroid.ui.resources.settings_ease_off_medium
import com.google.android.stardroid.ui.resources.settings_ease_off_none
import com.google.android.stardroid.ui.resources.settings_ease_off_summary
import com.google.android.stardroid.ui.resources.settings_eclipse_alerts
import com.google.android.stardroid.ui.resources.settings_eclipse_alerts_summary
import com.google.android.stardroid.ui.resources.settings_font_size
import com.google.android.stardroid.ui.resources.settings_font_size_extra_large
import com.google.android.stardroid.ui.resources.settings_font_size_large
import com.google.android.stardroid.ui.resources.settings_font_size_medium
import com.google.android.stardroid.ui.resources.settings_font_size_small
import com.google.android.stardroid.ui.resources.settings_font_size_summary
import com.google.android.stardroid.ui.resources.settings_magnetic_correction
import com.google.android.stardroid.ui.resources.settings_magnetic_correction_summary
import com.google.android.stardroid.ui.resources.settings_notifications_blocked
import com.google.android.stardroid.ui.resources.settings_notifications_blocked_action
import com.google.android.stardroid.ui.resources.settings_notifications_intro
import com.google.android.stardroid.ui.resources.settings_pass_alerts
import com.google.android.stardroid.ui.resources.settings_pass_alerts_summary
import com.google.android.stardroid.ui.resources.settings_renderer
import com.google.android.stardroid.ui.resources.settings_renderer_gles1
import com.google.android.stardroid.ui.resources.settings_renderer_gles3
import com.google.android.stardroid.ui.resources.settings_renderer_summary
import com.google.android.stardroid.ui.resources.settings_reverse_magnetic_z
import com.google.android.stardroid.ui.resources.settings_section_advanced
import com.google.android.stardroid.ui.resources.settings_section_appearance
import com.google.android.stardroid.ui.resources.settings_section_controls
import com.google.android.stardroid.ui.resources.settings_section_notifications
import com.google.android.stardroid.ui.resources.settings_section_other
import com.google.android.stardroid.ui.resources.settings_section_sensors
import com.google.android.stardroid.ui.resources.settings_shower_alerts
import com.google.android.stardroid.ui.resources.settings_shower_alerts_summary
import com.google.android.stardroid.ui.resources.settings_smoothing
import com.google.android.stardroid.ui.resources.settings_smoothing_summary
import com.google.android.stardroid.ui.resources.settings_steadiness
import com.google.android.stardroid.ui.resources.settings_steadiness_extreme
import com.google.android.stardroid.ui.resources.settings_steadiness_high
import com.google.android.stardroid.ui.resources.settings_steadiness_low
import com.google.android.stardroid.ui.resources.settings_steadiness_medium
import com.google.android.stardroid.ui.resources.settings_steadiness_summary
import com.google.android.stardroid.ui.resources.settings_steadiness_very_high
import com.google.android.stardroid.ui.resources.settings_tap_in_auto_mode
import com.google.android.stardroid.ui.resources.settings_tap_in_auto_mode_summary
import com.google.android.stardroid.ui.resources.settings_tap_to_identify
import com.google.android.stardroid.ui.resources.settings_tap_to_identify_summary
import com.google.android.stardroid.ui.resources.settings_title
import com.google.android.stardroid.ui.resources.settings_tonight_digest
import com.google.android.stardroid.ui.resources.settings_tonight_digest_summary
import com.google.android.stardroid.ui.resources.settings_view_direction
import com.google.android.stardroid.ui.resources.settings_view_direction_rotate90
import com.google.android.stardroid.ui.resources.settings_view_direction_standard
import com.google.android.stardroid.ui.resources.settings_view_direction_summary
import com.google.android.stardroid.ui.resources.settings_view_direction_telescope
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The settings screen — v1's `EditSettingsActivity` four sections (controls / appearance /
 * sensors / other) as a full-screen Compose overlay. "Other" holds the analytics opt-out
 * (D49); v1's sound-effects toggle still waits for sounds to be ported (D45).
 *
 * A platform shows only the rows it acts on: [SettingsViewModel]'s flags hide the rest (iOS has
 * no classic sensor path and no analytics), and a null [onOpenDiagnostics] hides that row. Back
 * belongs to the host, which pops the route on Android and closes the page on iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: (() -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val legacyPathActive by viewModel.legacyPathActive.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                windowInsets = topBarWindowInsets(stringResource(Res.string.settings_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_back),
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
                SectionHeader(Res.string.settings_section_controls)
                SwitchRow(
                    title = stringResource(Res.string.settings_tap_to_identify),
                    summary = stringResource(Res.string.settings_tap_to_identify_summary),
                    checked = state.tapToIdentify,
                    onCheckedChange = viewModel::setTapToIdentify,
                )
                // v1 dependency: the auto-mode refinement only matters while tap works at all.
                SwitchRow(
                    title = stringResource(Res.string.settings_tap_in_auto_mode),
                    summary = stringResource(Res.string.settings_tap_in_auto_mode_summary),
                    checked = state.tapToIdentifyInAutoMode,
                    enabled = state.tapToIdentify,
                    onCheckedChange = viewModel::setTapToIdentifyInAutoMode,
                )
                SwitchRow(
                    title = stringResource(Res.string.settings_auto_level_horizon),
                    summary = stringResource(Res.string.settings_auto_level_horizon_summary),
                    checked = state.autoLevelHorizon,
                    onCheckedChange = viewModel::setAutoLevelHorizon,
                )

                SectionHeader(Res.string.settings_section_appearance)
                ChoiceRow(
                    title = stringResource(Res.string.settings_font_size),
                    summary = stringResource(Res.string.settings_font_size_summary),
                    options = FontSize.entries,
                    selected = state.fontSize,
                    label = { fontSizeLabel(it) },
                    onSelect = viewModel::setFontSize,
                )
                ChoiceRow(
                    title = stringResource(Res.string.settings_auto_dimness),
                    summary = stringResource(Res.string.settings_auto_dimness_summary),
                    options = AutoDimness.entries,
                    selected = state.autoDimness,
                    label = { autoDimnessLabel(it) },
                    onSelect = viewModel::setAutoDimness,
                )

                SectionHeader(Res.string.settings_section_sensors)
                if (viewModel.classicSensorChoiceAvailable) {
                    SwitchRow(
                        title = stringResource(Res.string.settings_disable_gyro),
                        summary = stringResource(Res.string.settings_disable_gyro_summary),
                        checked = state.disableGyro,
                        onCheckedChange = viewModel::setDisableGyro,
                    )
                }
                // One filter serves both sensor paths now (issue #1007), so these show
                // whichever path is running. On by default for everyone while it's in beta, so
                // it gets exposure on both paths — see AppModule.settings and the summary string
                // below for the opt-out. Revisit the default once it's validated (issue #1001).
                SwitchRow(
                    title = stringResource(Res.string.settings_smoothing),
                    summary = stringResource(Res.string.settings_smoothing_summary),
                    checked = state.smoothingEnabled,
                    onCheckedChange = viewModel::setSmoothingEnabled,
                )
                // Kept as two controls while their useful range is still being found. They are
                // meant to be tuned in this order: steadiness first with ease-off at None,
                // then ease-off raised until the view keeps up.
                if (state.smoothingEnabled) {
                    ChoiceRow(
                        title = stringResource(Res.string.settings_steadiness),
                        summary = stringResource(Res.string.settings_steadiness_summary),
                        options = OneEuroSteadiness.entries,
                        selected = state.steadiness,
                        label = { steadinessLabel(it) },
                        onSelect = viewModel::setSteadiness,
                    )
                    ChoiceRow(
                        title = stringResource(Res.string.settings_ease_off),
                        summary = stringResource(Res.string.settings_ease_off_summary),
                        options = OneEuroEaseOff.entries,
                        selected = state.easeOff,
                        label = { easeOffLabel(it) },
                        onSelect = viewModel::setEaseOff,
                    )
                }
                // Still legacy-path-only: the fused sensor never sees raw magnetometer data.
                // Note the gate isn't `disableGyro` — a device with no rotation-vector sensor
                // runs the legacy path with `disableGyro` false.
                if (legacyPathActive) {
                    SwitchRow(
                        title = stringResource(Res.string.settings_reverse_magnetic_z),
                        summary = stringResource(Res.string.settings_classic_sensors_only),
                        checked = state.reverseMagneticZ,
                        onCheckedChange = viewModel::setReverseMagneticZ,
                    )
                }
                SwitchRow(
                    title = stringResource(Res.string.settings_magnetic_correction),
                    summary = stringResource(Res.string.settings_magnetic_correction_summary),
                    checked = state.useMagneticCorrection,
                    onCheckedChange = viewModel::setUseMagneticCorrection,
                )
                ChoiceRow(
                    title = stringResource(Res.string.settings_view_direction),
                    summary = stringResource(Res.string.settings_view_direction_summary),
                    options = ViewDirectionMode.entries,
                    selected = state.viewDirectionMode,
                    label = { viewDirectionLabel(it) },
                    onSelect = viewModel::setViewDirectionMode,
                )

                if (viewModel.notificationsAvailable) {
                    NotificationsSection(state, viewModel)
                }

                if (viewModel.analyticsChoiceAvailable ||
                    viewModel.announcementsAvailable ||
                    onOpenDiagnostics != null
                ) {
                    SectionHeader(Res.string.settings_section_other)
                }
                if (viewModel.analyticsChoiceAvailable) {
                    SwitchRow(
                        title = stringResource(Res.string.enable_analytics),
                        summary = stringResource(Res.string.enable_analytics_desc),
                        checked = state.enableAnalytics,
                        onCheckedChange = viewModel::setEnableAnalytics,
                    )
                }
                if (viewModel.announcementsAvailable) {
                    SwitchRow(
                        title = stringResource(Res.string.settings_announcements),
                        summary = stringResource(Res.string.settings_announcements_summary),
                        checked = state.announcements,
                        onCheckedChange = viewModel::setAnnouncements,
                    )
                }
                // Relocated from the ⋮ overflow sheet: a sensor/location readout is a
                // support tool, not something most people need most of the time, and it
                // was crowding out Help there (Hannah's feedback, 2026-08). "Settings →
                // Diagnostics" stays an easy instruction to give in a support reply.
                if (onOpenDiagnostics != null) {
                    NavigationRow(
                        title = stringResource(Res.string.diagnostics_button),
                        summary = stringResource(Res.string.settings_diagnostics_summary),
                        onClick = {
                            viewModel.logMenuItem(AnalyticsEvents.DIAGNOSTICS_OPENED_LABEL)
                            onOpenDiagnostics()
                        },
                    )
                }

                // Only on devices that can actually run the new backend: offering a choice
                // whose second option silently falls back to the first is worse than not
                // offering it. Shown on release builds too, not only debug ones, because the
                // point is to compare the two on real hardware.
                if (viewModel.rendererChoiceAvailable) {
                    SectionHeader(Res.string.settings_section_advanced)
                    ChoiceRow(
                        title = stringResource(Res.string.settings_renderer),
                        summary = stringResource(Res.string.settings_renderer_summary),
                        options = RendererBackend.entries,
                        selected = state.rendererBackend,
                        label = { rendererBackendLabel(it) },
                        // The map restarts itself: MainActivity watches this preference and
                        // recreates when it stops matching the backend its surface was built
                        // for. Recreating from here instead would race the DataStore write.
                        onSelect = viewModel::setRendererBackend,
                    )
                }
            }
        }
    }
}

/**
 * The D77 notifications section. The intro line states the contract ("at most once a day,
 * off until you turn it on") where the user decides, per the mockup. A toggle-on asks for the
 * platform's permission where it needs asking; a refusal flips the toggle back off, and a
 * standing system-level block shows an inline row deep-linking to the app's notification
 * settings instead of a dead switch.
 */
@Composable
private fun NotificationsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    // The switch whose turn-on waits on the ask, to flip back if the user refuses.
    var pendingRevert by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val requestPermission =
        rememberNotificationPermissionRequest(
            onDenied = {
                pendingRevert?.invoke(false)
                pendingRevert = null
            },
        )
    val openNotificationSettings = rememberOpenNotificationSettings()

    fun toggle(
        setter: (Boolean) -> Unit,
        enabled: Boolean,
    ) {
        setter(enabled)
        if (enabled) {
            pendingRevert = setter
            requestPermission()
        }
    }

    val anyAlertOn =
        state.showerAlerts || state.eclipseAlerts || state.passAlerts || state.tonightDigest
    SectionHeader(Res.string.settings_section_notifications)
    Text(
        stringResource(Res.string.settings_notifications_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    SwitchRow(
        title = stringResource(Res.string.settings_shower_alerts),
        summary = stringResource(Res.string.settings_shower_alerts_summary),
        checked = state.showerAlerts,
        onCheckedChange = { toggle(viewModel::setShowerAlerts, it) },
    )
    SwitchRow(
        title = stringResource(Res.string.settings_eclipse_alerts),
        summary = stringResource(Res.string.settings_eclipse_alerts_summary),
        checked = state.eclipseAlerts,
        onCheckedChange = { toggle(viewModel::setEclipseAlerts, it) },
    )
    if (viewModel.satelliteAlertsAvailable) {
        SwitchRow(
            title = stringResource(Res.string.settings_pass_alerts),
            summary = stringResource(Res.string.settings_pass_alerts_summary),
            checked = state.passAlerts,
            onCheckedChange = { toggle(viewModel::setPassAlerts, it) },
        )
    }
    SwitchRow(
        title = stringResource(Res.string.settings_tonight_digest),
        summary = stringResource(Res.string.settings_tonight_digest_summary),
        checked = state.tonightDigest,
        onCheckedChange = { toggle(viewModel::setTonightDigest, it) },
    )
    if (anyAlertOn && notificationsBlocked()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = openNotificationSettings)
                    .padding(vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_notifications_blocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(Res.string.settings_notifications_blocked_action),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: StringResource) {
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
                    Text(stringResource(Res.string.settings_cancel))
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
private fun rendererBackendLabel(backend: RendererBackend): String =
    stringResource(
        when (backend) {
            RendererBackend.GLES1 -> Res.string.settings_renderer_gles1
            RendererBackend.GLES3 -> Res.string.settings_renderer_gles3
        },
    )

@Composable
private fun fontSizeLabel(size: FontSize): String =
    stringResource(
        when (size) {
            FontSize.SMALL -> Res.string.settings_font_size_small
            FontSize.MEDIUM -> Res.string.settings_font_size_medium
            FontSize.LARGE -> Res.string.settings_font_size_large
            FontSize.EXTRA_LARGE -> Res.string.settings_font_size_extra_large
        },
    )

@Composable
private fun autoDimnessLabel(dimness: AutoDimness): String =
    stringResource(
        when (dimness) {
            AutoDimness.SYSTEM -> Res.string.settings_auto_dimness_system
            AutoDimness.DIM -> Res.string.settings_auto_dimness_dim
            AutoDimness.CLASSIC -> Res.string.settings_auto_dimness_classic
        },
    )

@Composable
private fun steadinessLabel(level: OneEuroSteadiness): String =
    stringResource(
        when (level) {
            OneEuroSteadiness.LOW -> Res.string.settings_steadiness_low
            OneEuroSteadiness.MEDIUM -> Res.string.settings_steadiness_medium
            OneEuroSteadiness.HIGH -> Res.string.settings_steadiness_high
            OneEuroSteadiness.VERY_HIGH -> Res.string.settings_steadiness_very_high
            OneEuroSteadiness.EXTREME -> Res.string.settings_steadiness_extreme
        },
    )

@Composable
private fun easeOffLabel(level: OneEuroEaseOff): String =
    stringResource(
        when (level) {
            OneEuroEaseOff.NONE -> Res.string.settings_ease_off_none
            OneEuroEaseOff.LOW -> Res.string.settings_ease_off_low
            OneEuroEaseOff.MEDIUM -> Res.string.settings_ease_off_medium
            OneEuroEaseOff.HIGH -> Res.string.settings_ease_off_high
        },
    )

@Composable
private fun viewDirectionLabel(mode: ViewDirectionMode): String =
    stringResource(
        when (mode) {
            ViewDirectionMode.STANDARD -> Res.string.settings_view_direction_standard
            ViewDirectionMode.ROTATE90 -> Res.string.settings_view_direction_rotate90
            ViewDirectionMode.TELESCOPE -> Res.string.settings_view_direction_telescope
        },
    )
