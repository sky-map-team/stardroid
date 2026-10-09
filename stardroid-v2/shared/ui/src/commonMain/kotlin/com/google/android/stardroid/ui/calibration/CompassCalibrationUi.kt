/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.calibration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.common.formattedStringResource
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.calibration_accuracy_high
import com.google.android.stardroid.ui.resources.calibration_accuracy_low
import com.google.android.stardroid.ui.resources.calibration_accuracy_medium
import com.google.android.stardroid.ui.resources.calibration_accuracy_no_contact
import com.google.android.stardroid.ui.resources.calibration_accuracy_unknown
import com.google.android.stardroid.ui.resources.calibration_accuracy_unreliable
import com.google.android.stardroid.ui.resources.calibration_do_not_show_again
import com.google.android.stardroid.ui.resources.calibration_heading_user
import com.google.android.stardroid.ui.resources.calibration_heading_warning
import com.google.android.stardroid.ui.resources.calibration_what_to_do
import com.google.android.stardroid.ui.resources.calibration_what_to_do_user
import com.google.android.stardroid.ui.resources.diagnostics_sensor_absent
import com.google.android.stardroid.ui.resources.settings_back
import com.google.android.stardroid.ui.resources.settings_ok
import com.google.android.stardroid.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource

/**
 * The compass-calibration screen — v1's `CompassCalibrationActivity` as a full-screen Compose
 * overlay: the figure-eight animation, the live calibration readout, and (when the low-accuracy
 * monitor opened it) the "don't show again" opt-out. In that auto-opened form it dismisses
 * itself the moment the compass reads HIGH ([onCalibrated]; v1's `AUTO_DISMISSABLE`). Back
 * belongs to the host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassCalibrationScreen(
    viewModel: CompassCalibrationViewModel,
    nightMode: Boolean,
    userInitiated: Boolean,
    onCalibrated: () -> Unit,
    onBack: () -> Unit,
) {
    val accuracy by viewModel.accuracy.collectAsStateWithLifecycle()
    val dontShowAgain by viewModel.dontShowAgain.collectAsStateWithLifecycle()

    if (!userInitiated) {
        val currentOnCalibrated by rememberUpdatedState(onCalibrated)
        LaunchedEffect(accuracy) {
            if (accuracy == SensorAccuracy.HIGH) currentOnCalibrated()
        }
    }

    val barTitle =
        stringResource(
            if (userInitiated) {
                Res.string.calibration_heading_user
            } else {
                Res.string.calibration_heading_warning
            },
        )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(barTitle) },
                windowInsets = topBarWindowInsets(barTitle),
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
                FigureEightAnimation(
                    nightMode,
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                AccuracyReadout(viewModel.hasMagnetometer, accuracy, nightMode)
                StyledHtml(
                    formattedStringResource(
                        if (userInitiated) {
                            Res.string.calibration_what_to_do_user
                        } else {
                            Res.string.calibration_what_to_do
                        },
                        CALIBRATION_VIDEO_URL,
                    ),
                    nightMode = nightMode,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (!userInitiated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = viewModel::setDontShowAgain,
                        )
                        Text(stringResource(Res.string.calibration_do_not_show_again))
                    }
                }
                Button(
                    onClick = onBack,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp),
                ) {
                    Text(stringResource(Res.string.settings_ok))
                }
            }
        }
    }
}

/**
 * v1's figure-eight animation ([FIGURE_EIGHT_PATH]), fitted to [modifier]'s bounds and
 * red-multiplied in night mode like every other photograph (D46).
 */
@Composable
internal expect fun FigureEightAnimation(
    nightMode: Boolean,
    modifier: Modifier,
)

/** The animation among the app's assets (Android) or bundle resources (iOS). */
internal const val FIGURE_EIGHT_PATH = "calibration/calib.gif"

@Composable
private fun AccuracyReadout(
    hasMagnetometer: Boolean,
    accuracy: SensorAccuracy?,
    nightMode: Boolean,
) {
    val colors = statusColors(nightMode)
    val text: String
    val color =
        if (!hasMagnetometer) {
            text = stringResource(Res.string.diagnostics_sensor_absent)
            colors.absent
        } else {
            text =
                stringResource(
                    when (accuracy) {
                        SensorAccuracy.HIGH -> Res.string.calibration_accuracy_high
                        SensorAccuracy.MEDIUM -> Res.string.calibration_accuracy_medium
                        SensorAccuracy.LOW -> Res.string.calibration_accuracy_low
                        SensorAccuracy.UNRELIABLE -> Res.string.calibration_accuracy_unreliable
                        SensorAccuracy.NO_CONTACT -> Res.string.calibration_accuracy_no_contact
                        null -> Res.string.calibration_accuracy_unknown
                    },
                )
            when (accuracy) {
                SensorAccuracy.HIGH -> colors.good
                SensorAccuracy.MEDIUM -> colors.ok
                SensorAccuracy.LOW -> colors.warning
                SensorAccuracy.UNRELIABLE, SensorAccuracy.NO_CONTACT -> colors.bad
                null -> colors.absent
            }
        }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** v1's linked demonstration video. */
private const val CALIBRATION_VIDEO_URL = "https://www.youtube.com/watch?v=-Uq7AmSAjt8"
